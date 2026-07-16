package com.aravind.orderalert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OrderCheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val isManualCheck: Boolean
        get() = inputData.getBoolean("manual", false)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Constants.ORDERS_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) OrderAlertApp/1.0"
                )
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            response.close()

            if (!response.isSuccessful || OrderParser.isLoginPage(body)) {
                val prefs = applicationContext.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE
                )
                val alreadyNotified = prefs.getBoolean(Constants.PREF_SESSION_EXPIRED_NOTIFIED, false)
                if (!alreadyNotified || isManualCheck) {
                    NotificationHelper.notifySessionExpired(applicationContext)
                    prefs.edit().putBoolean(Constants.PREF_SESSION_EXPIRED_NOTIFIED, true).apply()
                }
                if (isManualCheck) {
                    NotificationHelper.notifyDebug(
                        applicationContext,
                        "Check failed: HTTP ${response.code}, page looked like login page = " +
                            "${OrderParser.isLoginPage(body)}. Your session cookie probably isn't " +
                            "being sent. Try logging in again."
                    )
                }
                return@withContext Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE
            )
            // Session is valid again, reset the "already notified" expiry flag.
            prefs.edit().putBoolean(Constants.PREF_SESSION_EXPIRED_NOTIFIED, false).apply()

            val allOrders = OrderParser.parseOrders(body)
            val matches = OrderParser.filterPaidPending(allOrders)

            val notifiedIds = prefs.getStringSet(Constants.PREF_NOTIFIED_IDS, emptySet())
                ?.toMutableSet() ?: mutableSetOf()

            var newlyNotified = false
            for (order in matches) {
                if (!notifiedIds.contains(order.orderId)) {
                    NotificationHelper.notifyNewOrder(applicationContext, order)
                    notifiedIds.add(order.orderId)
                    newlyNotified = true
                }
            }

            if (newlyNotified) {
                // Keep the notified-id set from growing forever.
                val trimmed = if (notifiedIds.size > 300) {
                    notifiedIds.toList().takeLast(300).toMutableSet()
                } else notifiedIds
                prefs.edit().putStringSet(Constants.PREF_NOTIFIED_IDS, trimmed).apply()
            }

            if (isManualCheck) {
                val idsPreview = matches.take(5).joinToString(", ") { it.orderId }
                NotificationHelper.notifyDebug(
                    applicationContext,
                    "Check complete. Parsed ${allOrders.size} orders total, " +
                        "${matches.size} are paid+processing" +
                        (if (matches.isNotEmpty()) " (e.g. $idsPreview)" else "") +
                        ". Already-notified count: ${notifiedIds.size}."
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
