package com.hack.smartcam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.hack.smartcam.data.Accounts
import com.hack.smartcam.data.Repo
import com.hack.smartcam.phone.GuardService
import com.hack.smartcam.phone.Reminders
import com.hack.smartcam.ui.Haptics

class SmartCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Accounts.init(this) // reopens the last signed-in account
        Reminders.channel(this)
        Haptics.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(GuardService.CHANNEL, "Screen lock", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while SmartCam is watching bedtime and app limits"
            },
        )
        if (Repo.state.value.settings.guardOn) GuardService.start(this)
    }
}
