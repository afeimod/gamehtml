package com.flashbox.app.engine

/**
 * Supported Flash playback engines.
 */
enum class EngineType(val id: String, val displayName: String) {
    RUFFLE("ruffle", "Ruffle"),
    WAFLASH("waflash", "Waflash");

    companion object {
        fun fromId(id: String?): EngineType =
            values().firstOrNull { it.id == id } ?: RUFFLE
    }
}
