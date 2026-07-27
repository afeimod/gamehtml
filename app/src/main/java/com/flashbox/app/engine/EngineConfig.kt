package com.flashbox.app.engine

import com.google.gson.annotations.SerializedName

/**
 * Per-engine visual configuration. Persisted as JSON in SharedPreferences.
 *
 * Maps directly onto Ruffle's BaseLoadOptions (quality/scale/letterbox/renderer)
 * and onto Waflash's comparable options.
 */
data class EngineConfig(
    @SerializedName("quality") val quality: String = "high",
    @SerializedName("scale") val scale: String = "showAll",
    @SerializedName("letterbox") val letterbox: Boolean = true,
    @SerializedName("renderer") val renderer: String = "auto",
    @SerializedName("smooth") val smooth: Boolean = true,
    @SerializedName("frameRate") val frameRate: Int = 0
) {
    companion object {
        val QUALITY_OPTIONS = listOf("low", "medium", "high", "best", "auto")
        val SCALE_OPTIONS = listOf("showAll", "noBorder", "exactFit", "noScale")
        val RENDERER_OPTIONS = listOf("auto", "wgpu-webgl", "webgl", "canvas")
    }
}
