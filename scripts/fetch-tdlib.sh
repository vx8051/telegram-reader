#!/usr/bin/env bash
# Downloads the prebuilt TDLib Android AAR (Java API + libtdjni.so for all ABIs)
# from https://github.com/FaiBah/TDLibAndroidPrebuilt into app/libs/tdlib.aar.
set -euo pipefail

TDLIB_TAG="${TDLIB_TAG:-v1.8.67-d1085f9-Java}"
URL="https://github.com/FaiBah/TDLibAndroidPrebuilt/releases/download/${TDLIB_TAG}/tdlib.aar"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/libs/tdlib.aar"

mkdir -p "$(dirname "$DEST")"
echo "Downloading TDLib ${TDLIB_TAG} -> ${DEST}"
curl -fL --progress-bar -o "$DEST" "$URL"
echo "Done: $(du -h "$DEST" | cut -f1)"
