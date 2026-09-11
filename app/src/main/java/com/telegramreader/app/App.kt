package com.telegramreader.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.telegramreader.app.data.Settings
import com.telegramreader.app.telegram.ChannelRepository
import com.telegramreader.app.telegram.TdlibClient

class App : Application() {
    val settings by lazy { Settings(this) }
    val tdlib by lazy { TdlibClient(this) }
    val channels by lazy { ChannelRepository(tdlib) }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.notification_channel_description) },
        )
        // Bring TDLib up as early as possible so the service and UI share one authorised client.
        startTdlibIfConfigured()
    }

    /** Starts TDLib once credentials exist. Safe to call repeatedly. */
    fun startTdlibIfConfigured() {
        val p = settings.prefs.value
        if (p.hasCredentials && !tdlib.isStarted) {
            channels // touch so it subscribes before the first updates arrive
            tdlib.start(p.apiId, p.apiHash)
        }
    }

    companion object {
        const val CHANNEL_ID = "reader"
    }
}
