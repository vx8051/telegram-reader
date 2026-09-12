# Telegram Reader

Android app that reads new posts from selected Telegram channels aloud — in the foreground
or in the background — using the device's text-to-speech engine.

It logs in with **your own Telegram account** through [TDLib](https://github.com/tdlib/td),
so it works for any channel you are subscribed to (public or private) and receives posts
in real time via MTProto push, not polling.

## How it works

```
Telegram ──MTProto──▶ TDLib (libtdjni.so) ──updates──▶ ReaderService (foreground)
                                                          │  filters UpdateNewMessage
                                                          │  by selected chat ids
                                                          ▼
                                                    Speaker (TextToSpeech)
                                                    queue · audio focus · wake lock
```

| Piece | Where |
|---|---|
| TDLib wrapper (coroutines / Flow) | `telegram/TdlibClient.kt` |
| Channel list (channels the account has joined) | `telegram/ChannelRepository.kt` |
| Message → speech text (captions, polls, URL/emoji stripping, chunking) | `telegram/MessageSpeech.kt` |
| Foreground service (notification with Pause / Skip / Stop) | `service/ReaderService.kt` |
| TTS queue with audio-focus ducking | `service/Speaker.kt` |
| Restart after reboot | `service/BootReceiver.kt` |
| Per-post language detection (TextClassifier + script heuristic) | `service/LanguageDetector.kt` |
| Compose UI (credentials → sign-in → home / settings) | `ui/` |

## Prerequisites

1. **Telegram API credentials** (`api_id` / `api_hash`) — see [Getting api_id and api_hash](#getting-api_id-and-api_hash)
   below. You'll enter them in the app on first launch.
2. JDK 17, Android SDK (platform 35), and the prebuilt TDLib AAR:

   ```sh
   ./scripts/fetch-tdlib.sh          # downloads app/libs/tdlib.aar (~40 MB, git-ignored)
   ```

   The AAR comes from [FaiBah/TDLibAndroidPrebuilt](https://github.com/FaiBah/TDLibAndroidPrebuilt)
   (TDLib 1.8.67, all four ABIs, standard `org.drinkless.tdlib` Java API). Pin a different
   release with `TDLIB_TAG=<tag> ./scripts/fetch-tdlib.sh`.

## Getting api_id and api_hash

Telegram requires every third-party client to identify itself with an application id and hash
tied to a Telegram account. They are free and take a couple of minutes to obtain.

1. Open <https://my.telegram.org> in a browser.
2. Enter the phone number of your Telegram account in international format (e.g. `+380501234567`)
   and click **Next**.
3. Telegram sends a confirmation code **to the Telegram app** (as a message from "Telegram"),
   not by SMS. Enter it on the website.
4. Click **API development tools**.
5. Fill in the form:
   - **App title** — anything, e.g. `Telegram Reader`
   - **Short name** — 5–32 Latin letters/digits, e.g. `tgreader`
   - **URL** — optional, may be left empty
   - **Platform** — `Android`
   - **Description** — optional
6. Click **Create application**.
7. The next page shows **App api_id** (a number) and **App api_hash** (32 hexadecimal
   characters). Copy both into the app's first screen.

Notes:

- One account can have only **one** application; if you've already created one, the same page
  simply shows the existing credentials.
- The form sometimes fails with a bare **ERROR** message. This is a known quirk of the site: wait a
  few minutes and retry, try a different short name, use a different browser or network, or
  disable VPN/ad-blockers. Some users report it works only from the mobile browser (or only from
  desktop) — try both.
- Keep `api_hash` private. It identifies your app to Telegram; anyone with it can present their
  client as yours. It is **not** a login credential for your account, though — signing in still
  requires the phone-number code (and 2FA password if enabled).
- The credentials are stored only on the device, in the app's private storage, and never leave it
  except in TDLib's connection to Telegram's servers.

## Build & install

```sh
./scripts/fetch-tdlib.sh          # once: downloads app/libs/tdlib.aar
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Release builds are split per ABI (`assembleRelease` → `app/build/outputs/apk/release/`):
`arm64-v8a` (~25 MB, most phones since 2016), `armeabi-v7a`, `x86_64`, and a `universal` APK
(~96 MB) containing all of them. R8 is enabled; keep rules for TDLib are in `proguard-rules.pro`.

### Signing

Without a keystore, `assembleRelease` signs with the debug key. For a real release key, create
`keystore.properties` in the project root (git-ignored):

```properties
storeFile=release.jks
storePassword=…
keyAlias=telegram-reader
keyPassword=…
```

and generate the keystore with
`keytool -genkeypair -keystore release.jks -alias telegram-reader -keyalg RSA -keysize 2048 -validity 10000`.
Keep the keystore safe: an APK signed with a different key can't be installed over an existing one.

### Releases on GitHub

[`release.yml`](.github/workflows/release.yml) builds signed APKs and publishes them as a GitHub
Release whenever a `v*` tag is pushed:

```sh
git tag v0.1.0
git push origin v0.1.0
```

It needs four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i release.jks` (single line) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `telegram-reader` |
| `KEY_PASSWORD` | key password |

The version name comes from the tag (`v1.2.3` → `1.2.3`); the version code is the workflow run
number. [`ci.yml`](.github/workflows/ci.yml) builds a debug APK on every push to `main`.

## Using the app

1. Enter `api_id` / `api_hash`.
2. Sign in with your phone number → code (→ 2FA password if enabled).
3. Pick the channels to read from the **Channels** dropdown (only channels you've joined are listed;
   tap refresh after joining new ones in Telegram).
4. Tap **▶**. A persistent notification appears with Pause / Skip / Stop and shows what is being
   read; reading continues with the screen off and the app in the background. **Test** reads the
   newest post from the selected channels so you can check the voice.
5. Tap **Allow background activity** when prompted so Android doesn't kill the connection
   (battery-optimisation exemption). On some OEM ROMs (Xiaomi, Huawei, Samsung…) you may also
   need to lock the app in "recent apps" or disable their extra battery managers.

Settings (gear icon): speech rate, pitch, voice languages, lead-in pause, announce channel name,
read posts that arrived while offline, mark as read in Telegram, auto-start after reboot.

**Voice languages** takes a comma-separated list of BCP-47 tags, e.g. `en-US, uk-UA`. With one tag
every post uses that voice; with several, each post's language is detected on-device (Android's
`TextClassifier`, with a script-based fallback that also tells Ukrainian from Russian) and the matching
voice is used. The first tag is the fallback. Empty = device default. If a voice is missing, install it
under Settings → Text-to-speech → your engine → Install voice data.

**Lead-in pause** queues a short silence (default 0.7 s) before each post. Bluetooth and car head
units drop the first fraction of a second after an audio stream starts; the pause absorbs that so
the first word isn't clipped. By default it applies only when audio is routed to Bluetooth / USB /
car output; turn off *Only on external audio* to apply it always.

**Mark as read in Telegram** (off by default) calls `viewMessages` once a post has been spoken to
the end, so it shows as read on your other devices. Skipped or interrupted posts stay unread.

## Behaviour notes

- Posts arriving while the reader was stopped are skipped by default (anything older than 5 min
  when it arrives) — enable *Read posts that arrived while offline* to hear the backlog.
- Media albums are announced once (the first item carries the caption).
- Stickers are ignored; photos/videos/files are announced with their caption; polls are read with
  their options.
- URLs are replaced with the word "link"; emoji and markdown symbols are stripped.
- Audio focus is requested with *transient, may duck*, so music lowers while a post is read; an
  incoming call or another exclusive audio app pauses reading until focus returns.
- Muting a channel in Telegram has no effect here — the app reads every channel you've selected.
- "Recently read" keeps the last 10 spoken posts in memory; it is cleared when the process ends.
- The TTS engine and voice come from the phone's text-to-speech settings; the app sets language,
  rate and pitch. For better Ukrainian, install Google Speech Services or RHVoice and select it as
  the system engine.

## Not supported (yet)

- Accounts that require e-mail login (Telegram's login-email feature) — sign in from the official
  app once, then the phone/code flow works.
- QR-code login.
