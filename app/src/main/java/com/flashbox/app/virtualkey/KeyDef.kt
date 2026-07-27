package com.flashbox.app.virtualkey

import com.google.gson.annotations.SerializedName
import android.view.KeyEvent

/**
 * Definition of a single independent on-screen key button.
 * Persisted as JSON in SettingsRepository.
 *
 * @param keyCode Android KeyEvent keycode (also mapped to JS key)
 * @param label   Display label on the button
 * @param xPct    Position X as fraction of screen width (0..1), -1 = default
 * @param yPct    Position Y as fraction of screen height (0..1), -1 = default
 * @param sizeDp  Button size in dp
 */
data class KeyDef(
    @SerializedName("keyCode") val keyCode: Int,
    @SerializedName("label") val label: String,
    @SerializedName("jsKey") val jsKey: String,
    @SerializedName("jsCode") val jsCode: Int,
    @SerializedName("xPct") val xPct: Float = -1f,
    @SerializedName("yPct") val yPct: Float = -1f,
    @SerializedName("sizeDp") val sizeDp: Int = 64
) {
    companion object {
        /** Default independent keys: J K L U I O + Enter + Space. */
        fun defaults(): List<KeyDef> {
            val base = listOf(
                triple("J", "j", 74),
                triple("K", "k", 75),
                triple("L", "l", 76),
                triple("U", "u", 85),
                triple("I", "i", 73),
                triple("O", "o", 79)
            )
            val extras = listOf(
                KeyDef(KeyEvent.KEYCODE_ENTER, "Enter", "Enter", 13),
                KeyDef(KeyEvent.KEYCODE_SPACE, "Space", " ", 32)
            )
            return base + extras
        }

        private fun triple(label: String, jsKey: String, jsCode: Int): KeyDef {
            val kc = when (jsKey.lowercase()) {
                "j" -> KeyEvent.KEYCODE_J
                "k" -> KeyEvent.KEYCODE_K
                "l" -> KeyEvent.KEYCODE_L
                "u" -> KeyEvent.KEYCODE_U
                "i" -> KeyEvent.KEYCODE_I
                "o" -> KeyEvent.KEYCODE_O
                else -> KeyEvent.KEYCODE_UNKNOWN
            }
            return KeyDef(kc, label, jsKey, jsCode)
        }
    }
}
