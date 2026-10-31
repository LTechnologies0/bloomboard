package dev.ltechnologies.bloomboard.data

import android.content.Context
import dev.ltechnologies.bloomboard.hid.LayoutId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val layout: LayoutId = LayoutId.AZERTY,
    val haptic: Boolean = true,
    val sensitivity: Float = 1f,
    val linuxUnicode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val tvHost: String = "",
    val hidEnabled: Boolean = false,
    val tvActive: Boolean = false,
)

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value)
        prefs.edit()
            .putString(KEY_LAYOUT, next.layout.storageKey)
            .putBoolean(KEY_HAPTIC, next.haptic)
            .putFloat(KEY_SENSITIVITY, next.sensitivity.coerceIn(0.4f, 2.8f))
            .putBoolean(KEY_UNICODE, next.linuxUnicode)
            .putBoolean(KEY_SCREEN, next.keepScreenOn)
            .putString(KEY_HOST, next.tvHost)
            .putBoolean(KEY_HID, next.hidEnabled)
            .putBoolean(KEY_TV, next.tvActive)
            .apply()
        _settings.value = next.copy(sensitivity = next.sensitivity.coerceIn(0.4f, 2.8f))
    }

    fun pinFor(host: String): String? = prefs.getString(pinKey(host), null)

    fun setPin(host: String, pinHex: String) {
        prefs.edit().putString(pinKey(host), pinHex).commit()
    }

    fun clearPin(host: String) {
        prefs.edit().remove(pinKey(host)).commit()
    }

    private fun read(): Settings = Settings(
        layout = LayoutId.fromKey(prefs.getString(KEY_LAYOUT, LayoutId.AZERTY.storageKey)),
        haptic = prefs.getBoolean(KEY_HAPTIC, true),
        sensitivity = prefs.getFloat(KEY_SENSITIVITY, 1f),
        linuxUnicode = prefs.getBoolean(KEY_UNICODE, false),
        keepScreenOn = prefs.getBoolean(KEY_SCREEN, true),
        tvHost = prefs.getString(KEY_HOST, "").orEmpty(),
        hidEnabled = prefs.getBoolean(KEY_HID, false),
        tvActive = prefs.getBoolean(KEY_TV, false),
    )

    private fun pinKey(host: String) = "pin:" + host.trim().lowercase()

    private companion object {
        const val FILE = "bloomboard"
        const val KEY_LAYOUT = "layout"
        const val KEY_HAPTIC = "haptic"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_UNICODE = "unicode"
        const val KEY_SCREEN = "screen"
        const val KEY_HOST = "tv_host"
        const val KEY_HID = "hid"
        const val KEY_TV = "tv_active"
    }
}
