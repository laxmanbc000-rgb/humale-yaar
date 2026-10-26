package com.humale.yaar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Store {
    private fun prefs(c: Context): SharedPreferences {
        val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            c, "humale_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiKey(c: Context): String = prefs(c).getString("api_key", "") ?: ""
    fun setApiKey(c: Context, v: String) = prefs(c).edit().putString("api_key", v.trim()).apply()

    fun model(c: Context): String = prefs(c).getString("model", "claude-sonnet-4-5") ?: "claude-sonnet-4-5"
    fun setModel(c: Context, v: String) = prefs(c).edit().putString("model", v.trim()).apply()

    fun wake(c: Context): Boolean = prefs(c).getBoolean("wake", false)
    fun setWake(c: Context, v: Boolean) = prefs(c).edit().putBoolean("wake", v).apply()
}
