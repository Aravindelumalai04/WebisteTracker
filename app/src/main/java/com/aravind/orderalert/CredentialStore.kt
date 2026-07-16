package com.aravind.orderalert

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

data class SavedLogin(
    val actionUrl: String,
    val emailField: String,
    val emailValue: String,
    val passwordField: String,
    val passwordValue: String,
    // name -> fallback value, for hidden/other fields (e.g. csrf tokens).
    // Refreshed live from a fresh page load when possible.
    val otherFields: Map<String, String>
)

/**
 * Stores the captured login form (field names + your credentials) encrypted
 * on-device using Android Keystore. Never written to any file that gets
 * committed to git.
 */
object CredentialStore {

    private const val FILE_NAME = "secure_login_store"
    private const val KEY_DATA = "saved_login_json"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        FILE_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, login: SavedLogin) {
        val json = JSONObject().apply {
            put("actionUrl", login.actionUrl)
            put("emailField", login.emailField)
            put("emailValue", login.emailValue)
            put("passwordField", login.passwordField)
            put("passwordValue", login.passwordValue)
            val others = JSONObject()
            login.otherFields.forEach { (k, v) -> others.put(k, v) }
            put("otherFields", others)
        }
        prefs(context).edit().putString(KEY_DATA, json.toString()).apply()
    }

    fun load(context: Context): SavedLogin? {
        val raw = prefs(context).getString(KEY_DATA, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val others = mutableMapOf<String, String>()
            val othersJson = json.optJSONObject("otherFields")
            othersJson?.keys()?.forEach { key -> others[key] = othersJson.getString(key) }
            SavedLogin(
                actionUrl = json.getString("actionUrl"),
                emailField = json.getString("emailField"),
                emailValue = json.getString("emailValue"),
                passwordField = json.getString("passwordField"),
                passwordValue = json.getString("passwordValue"),
                otherFields = others
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_DATA).apply()
    }

    fun hasSavedLogin(context: Context): Boolean = load(context) != null
}
