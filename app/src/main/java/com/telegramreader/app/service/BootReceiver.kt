package com.telegramreader.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telegramreader.app.App

/** Restarts the reader after a reboot / app update when the user opted in and is logged in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as App
        val prefs = app.settings.prefs.value
        if (prefs.autoStartOnBoot && prefs.hasCredentials && prefs.channelIds.isNotEmpty()) {
            ReaderService.start(context)
        }
    }
}
