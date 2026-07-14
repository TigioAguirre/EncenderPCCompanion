package com.example.bandcolorreact

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {
    private const val PREFS_NAME = "encenderpc_companion_prefs"
    const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_TRIGGER_URL = "trigger_url"
    private const val KEY_TUTORIAL_SHOWN = "tutorial_shown"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isServiceEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SERVICE_ENABLED, true)
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun getTriggerUrl(context: Context): String {
        return prefs(context).getString(KEY_TRIGGER_URL, "") ?: ""
    }

    fun setTriggerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_TRIGGER_URL, url.trim()).apply()
    }

    fun isTutorialShown(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TUTORIAL_SHOWN, false)
    }

    fun setTutorialShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_SHOWN, shown).apply()
    }
}