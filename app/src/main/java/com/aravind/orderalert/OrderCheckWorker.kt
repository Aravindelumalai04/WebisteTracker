package com.aravind.orderalert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
            var body = fetchOrdersPage()
            var loginFailed = body == null

            if (loginFailed) {
                val reloginSucceeded = attemptAutoRelogin()
                if (reloginSucceeded) {
                    body = fetchOrdersPage()
                    loginFailed = body == null
                }
            }

            val prefs = applicationContext.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE
            )

            if (loginFailed || body == null) {
                val alreadyNotified = prefs.getBoolean(Constants.PREF_SESSION_EXPIRED_NOTIFIED, false)
                if (!alreadyNotified || isManualCheck) {
                    NotificationHelper.notifySessionExpired(applicationContext)
                    prefs.edit().putBoolean(Constants.PREF_SESSION_EXPIRED_NOTIFIED, true).apply()
                }
                if (isManualCheck) {
                    val hasSaved = CredentialStore.hasSavedLogin(applicationContext)
                    NotificationHelper.notifyDebug(
                        applicationContext,
                        "Check failed: still on login page after " +
                            (if (hasSaved) "attempting auto re-login. Your saved credentials may be " +
                                "out of date, or the site changed its login form. Please log in manually once."
                            else "no saved login found yet. Log in manually once so I can capture it for next time.")
                    )
                }
                return@withContext Result.success()
            }

            // Session is valid, reset the "already notified" expiry flag.
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

    /** Returns the page body, or null if the request failed or landed on the login page. */
    private fun fetchOrdersPage(): String? {
        val request = Request.Builder()
            .url(Constants.ORDERS_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) OrderAlertApp/1.0")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        response.close()

        if (!response.isSuccessful || OrderParser.isLoginPage(body)) return null
        return body
    }

    /**
     * Uses the saved (captured) login form to silently log back in.
     * Fetches a fresh copy of the login page first, so any hidden/dynamic
     * fields (e.g. CSRF tokens) are current rather than stale.
     */
    private fun attemptAutoRelogin(): Boolean {
        val saved = CredentialStore.load(applicationContext) ?: return false

        val freshValues = try {
            val loginPageRequest = Request.Builder()
                .url(Constants.LOGIN_URL)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) OrderAlertApp/1.0")
                .build()
            val loginPageResponse = client.newCall(loginPageRequest).execute()
            val html = loginPageResponse.body?.string().orEmpty()
            loginPageResponse.close()
            val doc = Jsoup.parse(html)
            val values = mutableMapOf<String, String>()
            doc.select("input[name], select[name], textarea[name]").forEach { el ->
                values[el.attr("name")] = el.attr("value")
            }
            values
        } catch (e: Exception) {
            emptyMap()
        }

        val formBuilder = FormBody.Builder()
            .add(saved.emailField, saved.emailValue)
            .add(saved.passwordField, saved.passwordValue)

        saved.otherFields.forEach { (name, fallbackValue) ->
            val currentValue = freshValues[name] ?: fallbackValue
            formBuilder.add(name, currentValue)
        }

        return try {
            val loginRequest = Request.Builder()
                .url(saved.actionUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) OrderAlertApp/1.0")
                .post(formBuilder.build())
                .build()
            val loginResponse = client.newCall(loginRequest).execute()
            val ok = loginResponse.isSuccessful
            loginResponse.close()
            ok
        } catch (e: Exception) {
            false
        }
    }
}
