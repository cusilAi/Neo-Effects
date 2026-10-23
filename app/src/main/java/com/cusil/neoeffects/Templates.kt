package com.cusil.neoeffects

import androidx.compose.ui.graphics.Color

class TemplateDef(
    val id: String,
    val title: String,
    val sub: String,
    val tag: String,
    val c1: Color,
    val c2: Color,
    val fx: List<Pair<String, Map<String, Float>>>
)

val TEMPLATES = listOf(
    TemplateDef(
        "shake", "SHAKE", "Shake Effect", "Hot", Color(0xFFB0102A), Color(0xFF200308),
        listOf(
            Pair("camera_shake", mapOf("amount" to 26f, "speed" to 5f, "zoom" to 1.08f)),
            Pair("chromatic", mapOf("amount" to 12f))
        )
    ),
    TemplateDef(
        "glitch", "GLITCH", "Glitch Vibes", "Hot", Color(0xFF1F6BFF), Color(0xFF050B1E),
        listOf(
            Pair("slice_glitch", mapOf("shift" to 0.1f)),
            Pair("rgb_split", mapOf("amount" to 20f)),
            Pair("vhs", mapOf("wobble" to 0.7f, "grain" to 0.25f))
        )
    ),
    TemplateDef(
        "velocity", "VELOCITY", "Velocity Edit", "New", Color(0xFF8A8D99), Color(0xFF0A0A0E),
        listOf(
            Pair("zoom_pulse", mapOf("amount" to 0.22f, "speed" to 3f)),
            Pair("zoom_blur", mapOf("strength" to 0.35f)),
            Pair("color_adjust", mapOf("contrast" to 1.15f, "saturation" to 1.2f))
        )
    ),
    TemplateDef(
        "neon", "NEON", "Neon Glow", "New", Color(0xFF9B2BFF), Color(0xFF12041F),
        listOf(
            Pair("glow", mapOf("intensity" to 1.8f, "radius" to 18f, "threshold" to 0.5f)),
            Pair("teal_orange", mapOf("amount" to 1.8f)),
            Pair("chromatic", mapOf("amount" to 8f))
        )
    ),
    TemplateDef(
        "zoom", "ZOOM", "Zoom Transition", "Hot", Color(0xFF6D6F7C), Color(0xFF0B0B10),
        listOf(
            Pair("zoom_blur", mapOf("strength" to 0.6f)),
            Pair("zoom_pulse", mapOf("amount" to 0.3f, "speed" to 2f)),
            Pair("light_leak", mapOf("intensity" to 0.5f))
        )
    ),
    TemplateDef(
        "anime", "ANIME", "Anime Burst", "New", Color(0xFFFF7A1A), Color(0xFF1C0A02),
        listOf(
            Pair("speed_lines", mapOf("power" to 0.7f)),
            Pair("glow", mapOf("intensity" to 1.2f)),
            Pair("camera_shake", mapOf("amount" to 14f, "speed" to 3f))
        )
    )
)

fun applyTemplate(id: String, clip: Clip, lib: List<NeoEffect>) {
    val t = TEMPLATES.firstOrNull { it.id == id } ?: return
    for ((eid, over) in t.fx) {
        val e = lib.firstOrNull { it.id == eid } ?: continue
        val inst = FxInst(e)
        for ((k, v) in over) {
            if (e.params.any { it.id == k }) inst.values[k] = v
        }
        clip.fx.add(inst)
    }
}
