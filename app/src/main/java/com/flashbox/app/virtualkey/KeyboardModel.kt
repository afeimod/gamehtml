package com.flashbox.app.virtualkey

import android.view.KeyEvent

/**
 * A visual keyboard model used by the key picker dialog.
 * Organized into rows resembling a physical keyboard so the user can pick any key.
 */
object KeyboardModel {

    data class KeyRow(val keys: List<KeyDef>)

    /** Build the full set of selectable keys, row by row. */
    fun rows(): List<KeyRow> {
        val rows = mutableListOf<KeyRow>()
        // Number row
        rows.add(KeyRow(listOf(
            k("1","1",49), k("2","2",50), k("3","3",51), k("4","4",52),
            k("5","5",53), k("6","6",54), k("7","7",55), k("8","8",56),
            k("9","9",57), k("0","0",48)
        )))
        // QWERTY row
        rows.add(KeyRow(listOf(
            k("Q","q",81), k("W","w",87), k("E","e",69), k("R","r",82),
            k("T","t",84), k("Y","y",89), k("U","u",85), k("I","i",73),
            k("O","o",79), k("P","p",80)
        )))
        // ASDF row
        rows.add(KeyRow(listOf(
            k("A","a",65), k("S","s",83), k("D","d",68), k("F","f",70),
            k("G","g",71), k("H","h",72), k("J","j",74), k("K","k",75),
            k("L","l",76)
        )))
        // ZXCV row
        rows.add(KeyRow(listOf(
            k("Z","z",90), k("X","x",88), k("C","c",67), k("V","v",86),
            k("B","b",66), k("N","n",78), k("M","m",77)
        )))
        // Modifier / function row
        rows.add(KeyRow(listOf(
            KeyDef(KeyEvent.KEYCODE_SPACE, "Space", " ", 32),
            KeyDef(KeyEvent.KEYCODE_ENTER, "Enter", "Enter", 13),
            KeyDef(KeyEvent.KEYCODE_DEL, "Backspace", "Backspace", 8),
            KeyDef(KeyEvent.KEYCODE_TAB, "Tab", "Tab", 9),
            KeyDef(KeyEvent.KEYCODE_ESCAPE, "Esc", "Escape", 27),
            KeyDef(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift", "Shift", 16),
            KeyDef(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl", "Control", 17),
            KeyDef(KeyEvent.KEYCODE_ALT_LEFT, "Alt", "Alt", 18),
            KeyDef(KeyEvent.KEYCODE_SPACE, "␣", " ", 32)
        )))
        // Arrow / function keys
        rows.add(KeyRow(listOf(
            KeyDef(KeyEvent.KEYCODE_DPAD_UP, "↑", "ArrowUp", 38),
            KeyDef(KeyEvent.KEYCODE_DPAD_DOWN, "↓", "ArrowDown", 40),
            KeyDef(KeyEvent.KEYCODE_DPAD_LEFT, "←", "ArrowLeft", 37),
            KeyDef(KeyEvent.KEYCODE_DPAD_RIGHT, "→", "ArrowRight", 39),
            KeyDef(KeyEvent.KEYCODE_F1, "F1", "F1", 112),
            KeyDef(KeyEvent.KEYCODE_F2, "F2", "F2", 113),
            KeyDef(KeyEvent.KEYCODE_F3, "F3", "F3", 114),
            KeyDef(KeyEvent.KEYCODE_F4, "F4", "F4", 115),
            KeyDef(KeyEvent.KEYCODE_F5, "F5", "F5", 116),
            KeyDef(KeyEvent.KEYCODE_F6, "F6", "F6", 117),
            KeyDef(KeyEvent.KEYCODE_F7, "F7", "F7", 118),
            KeyDef(KeyEvent.KEYCODE_F8, "F8", "F8", 119)
        )))
        return rows
    }

    /** All selectable keys flattened. */
    fun all(): List<KeyDef> = rows().flatMap { it.keys }

    private fun k(label: String, jsKey: String, jsCode: Int): KeyDef {
        val kc = mapJsCodeToKeyCode(jsCode)
        return KeyDef(kc, label, jsKey, jsCode)
    }

    private fun mapJsCodeToKeyCode(jsCode: Int): Int = when (jsCode) {
        48 -> KeyEvent.KEYCODE_0
        49 -> KeyEvent.KEYCODE_1
        50 -> KeyEvent.KEYCODE_2
        51 -> KeyEvent.KEYCODE_3
        52 -> KeyEvent.KEYCODE_4
        53 -> KeyEvent.KEYCODE_5
        54 -> KeyEvent.KEYCODE_6
        55 -> KeyEvent.KEYCODE_7
        56 -> KeyEvent.KEYCODE_8
        57 -> KeyEvent.KEYCODE_9
        in 65..90 -> (jsCode - 65) + KeyEvent.KEYCODE_A
        else -> KeyEvent.KEYCODE_UNKNOWN
    }
}
