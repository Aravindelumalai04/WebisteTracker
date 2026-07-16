package com.aravind.orderalert

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Bridges OkHttp's cookie handling to Android's WebView CookieManager.
 * This lets background (WorkManager) network calls reuse the exact same
 * session cookie that was set when the user logged in through the WebView.
 */
class WebViewCookieJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        for (cookie in cookies) {
            manager.setCookie(url.toString(), cookie.toString())
        }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return cookieHeader.split(";")
            .mapNotNull { raw ->
                Cookie.parse(url, raw.trim())
            }
    }
}
