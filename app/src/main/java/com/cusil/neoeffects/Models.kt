package com.cusil.neoeffects

import android.content.Context
import android.graphics.RuntimeShader
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
    fun parse(text: String, builtIn: Boolean): NeoEffect {
        val o = JSONObject(text.trim())
        require(o.optInt("neofx", 0) == 1) { "Missing \"neofx\": 1" }
        val id = o.getString("id")
        require(Regex("[a-z0-9_]+").matches(id)) { "id must be lowercase letters, numbers or _" }
        val shader = o.getString("shader")
        require(shader.contains("uniform shader image")) { "shader must declare: uniform shader image;" }
        require(shader.contains("half4 main")) { "shader must define: half4 main(float2 p)" }

        val ps = ArrayList<NeoParam>()
        val arr = o.optJSONArray("params")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val pid = p.getString("id")
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
        val fx = NeoEffect(id, o.optString("name", id), o.optString("category", "Custom"), ps, shader, builtIn)
        // compile check: throws if the shader code is invalid
        RuntimeShader(shader)
        return fx
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
  "neofx": 1,
  "id": "lowercase_id",
  "name": "Display Name",
  "category": "Glitch",
  "params": [
    { "id": "amount", "name": "Amount", "min": 0, "max": 50, "default": 10 }
  ],
  "shader": "<AGSL shader code on ONE line>"
}

Shader rules (Android AGSL, similar to GLSL):
- Start with: uniform shader image; uniform float2 resolution; uniform float time;
- Add one "uniform float <id>;" for every param
- Entry point: half4 main(float2 p), p is in pixel coordinates (not 0-1)
- Read pixels with image.eval(coord)
- resolution = size in pixels, time = seconds
- Return half4(r, g, b, a)
- No double quotes inside the shader, keep it on one line

Example:
{"neofx":1,"id":"wave","name":"Wave","category":"Distort","params":[{"id":"strength","name":"Strength","min":0,"max":60,"default":20}],"shader":"uniform shader image; uniform float2 resolution; uniform float time; uniform float strength; half4 main(float2 p) { float2 q = p; q.x += sin(p.y / 40.0 + time * 3.0) * strength; return image.eval(q); }"}

The effect I want:
"""
