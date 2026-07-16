package com.aravind.orderalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val wasLoggedIn = prefs.getBoolean(Constants.PREF_LOGGED_IN, false)
            if (wasLoggedIn) {
                WorkScheduler.schedule(context)
            }
        }
    }
}
