package com.cusil.neoeffects

import android.content.Context
import org.json.JSONObject
import java.io.File

data class NeoParam(
    val id: String,
    val name: String,
    val min: Float,
    val max: Float,
    val def: Float
)

data class NeoEffect(
    val id: String,
    val name: String,
    val category: String,
    val params: List<NeoParam>,
    val shader: String,
    val builtIn: Boolean
)

object NeoFx {
    const val VERTEX = "attribute vec2 aPos; varying vec2 vUv; void main() { vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5); gl_Position = vec4(aPos, 0.0, 1.0); }"
    const val PASSTHROUGH = "vec4 effect(vec2 uv) { return texture2D(image, uv); }"

    fun fragment(code: String, params: List<NeoParam>): String {
        val sb = StringBuilder()
        sb.append("#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n")
        sb.append("uniform sampler2D image;\nuniform vec2 resolution;\nuniform float time;\nvarying vec2 vUv;\n")
        for (p in params) {
            sb.append("uniform float ").append(p.id).append(";\n")
        }
        sb.append(code).append("\nvoid main() { gl_FragColor = effect(vUv); }\n")
        return sb.toString()
    }

    fun parse(text: String, builtIn: Boolean): NeoEffect {
        val o = JSONObject(text.trim())
        require(o.optInt("neofx", 0) == 2) { "Missing \"neofx\": 2 (this is the OpenGL format)" }
        val id = o.getString("id")
        require(Regex("[a-z0-9_]+").matches(id)) { "id must be lowercase letters, numbers or _" }
        val shader = o.getString("shader")
        require(!shader.contains("half4") && !shader.contains("uniform shader")) { "Old AGSL shader. Use GLSL: vec4 effect(vec2 uv)" }
        require(shader.contains("vec4 effect(")) { "shader must define: vec4 effect(vec2 uv)" }
        require(!shader.contains("void main")) { "Do not write main(). Only define effect()" }

        val reserved = setOf("image", "resolution", "time")
        val ps = ArrayList<NeoParam>()
        val arr = o.optJSONArray("params")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val pid = p.getString("id")
                require(Regex("[a-zA-Z_][a-zA-Z0-9_]*").matches(pid)) { "bad param id: $pid" }
                require(pid !in reserved) { "param id '$pid' is reserved" }
                ps.add(
                    NeoParam(
                        pid,
                        p.optString("name", pid),
                        p.optDouble("min", 0.0).toFloat(),
                        p.optDouble("max", 1.0).toFloat(),
                        p.optDouble("default", 0.0).toFloat()
                    )
                )
            }
        }
        return NeoEffect(id, o.optString("name", id), o.optString("category", "Custom"), ps, shader, builtIn)
    }
}

object EffectStore {
    fun load(ctx: Context): List<NeoEffect> {
        val out = ArrayList<NeoEffect>()
        val names = ctx.assets.list("effects")
        if (names != null) {
            for (n in names.sorted()) {
                try {
                    val t = ctx.assets.open("effects/$n").bufferedReader().use { it.readText() }
                    out.add(NeoFx.parse(t, true))
                } catch (e: Exception) {
                }
            }
        }
        val files = File(ctx.filesDir, "effects").listFiles()
        if (files != null) {
            for (f in files.sortedBy { it.name }) {
                try {
                    out.add(NeoFx.parse(f.readText(), false))
                } catch (e: Exception) {
                }
            }
        }
        return out
    }

    fun saveImported(ctx: Context, text: String): NeoEffect {
        val fx = NeoFx.parse(text, false)
        val dir = File(ctx.filesDir, "effects")
        dir.mkdirs()
        File(dir, fx.id + ".neofx").writeText(text.trim())
        return fx
    }
}

const val AI_PROMPT = """Write ONE NeoEffects effect file (.neofx). Output ONLY valid JSON. No markdown, no explanation.

Format:
{
  "neofx": 2,
  "id": "lowercase_id",
  "name": "Display Name",
  "category": "Glitch",
  "params": [
    { "id": "amount", "name": "Amount", "min": 0, "max": 50, "default": 10 }
  ],
  "shader": "<GLSL ES 1.00 code on ONE line>"
}

Shader rules (OpenGL ES 2.0 GLSL):
- Define ONLY this function: vec4 effect(vec2 uv). uv is 0 to 1, (0,0) is the top-left.
- Do NOT write main(), precision or uniform declarations. The app adds them.
- Available: sampler2D image, vec2 resolution (pixels), float time (seconds), and one float uniform per param named by its id.
- Read pixels with texture2D(image, coord) using 0-1 coordinates.
- Helper functions are allowed above effect().
- No double quotes inside the shader, keep it on one line.

Example:
{"neofx":2,"id":"wave","name":"Wave","category":"Distort","params":[{"id":"strength","name":"Strength","min":0,"max":60,"default":20}],"shader":"vec4 effect(vec2 uv) { vec2 q = uv; q.x += sin(uv.y * resolution.y / 40.0 + time * 3.0) * strength / resolution.x; return texture2D(image, q); }"}

The effect I want:
"""
