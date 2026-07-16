package com.aravind.orderalert

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Exposed to the WebView as "AndroidLoginCapture". A small JS snippet
 * injected into the login page calls this with the exact form action URL
 * and field name/value pairs at the moment of submit, so we don't have to
 * guess the site's form structure.
 */
class LoginCaptureInterface(
    private val onCaptured: (actionUrl: String, fields: List<Pair<String, String>>, types: Map<String, String>) -> Unit
) {

    @JavascriptInterface
    fun onFormSubmit(json: String) {
        try {
            val obj = JSONObject(json)
            val actionUrl = obj.getString("action")
            val fieldsArray = obj.getJSONArray("fields")
            val fields = mutableListOf<Pair<String, String>>()
            val types = mutableMapOf<String, String>()
            for (i in 0 until fieldsArray.length()) {
                val f = fieldsArray.getJSONObject(i)
                val name = f.getString("name")
                val value = f.optString("value", "")
                val type = f.optString("type", "text")
                fields.add(name to value)
                types[name] = type
            }
            onCaptured(actionUrl, fields, types)
        } catch (e: Exception) {
            // Ignore malformed capture; falls back to manual login next time.
        }
    }
}
