package com.cusil.neoeffects

import android.content.Context
import org.json.JSONArray
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
    const val VERTEX = "attribute vec2 aPos; uniform float uFlip; varying vec2 vUv; void main() { vUv = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5); gl_Position = vec4(aPos.x, aPos.y * uFlip, 0.0, 1.0); }"
    const val PASSTHROUGH = "vec4 effect(vec2 uv) { return texture2D(image, uv); }"

    private val BAD_IDS = setOf(
        "image", "resolution", "time", "aPos", "uFlip", "vUv", "gl_FragColor",
        "input", "output", "sample", "half", "fixed", "long", "short", "double", "unsigned",
        "class", "union", "enum", "default", "switch", "goto", "static", "extern", "inline",
        "volatile", "public", "interface", "template", "this", "packed", "asm", "namespace",
        "using", "cast", "sizeof", "mix", "min", "max", "step", "pow", "abs", "sin", "cos",
        "tan", "clamp", "length", "dot", "floor", "fract", "mod", "sign", "smoothstep",
        "normalize", "distance", "reflect", "sqrt", "exp", "log", "ceil", "atan", "cross", "main", "effect"
    )

    fun clean(text: String): String {
        var t = text.trim()
        t = t.replace("```json", "").replace("```JSON", "").replace("```", "")
        t = t.replace('\u201c', '"').replace('\u201d', '"').replace('\u2018', '\'').replace('\u2019', '\'')
        val a = t.indexOf('{')
        val b = t.lastIndexOf('}')
        if (a >= 0 && b > a) t = t.substring(a, b + 1)
        return t
    }

    fun sanitize(code: String, paramIds: List<String>): String {
        var s = code
        s = Regex("precision\\s+(lowp|mediump|highp)\\s+\\w+\\s*;").replace(s, " ")
        s = Regex("varying\\s+(highp\\s+|mediump\\s+|lowp\\s+)?vec2\\s+vUv\\s*;").replace(s, " ")
        val names = listOf("image", "resolution", "time") + paramIds
        for (n in names) {
            s = Regex("uniform\\s+(highp\\s+|mediump\\s+|lowp\\s+)?(sampler2D|vec2|float)\\s+" + n + "\\s*;").replace(s, " ")
        }
        return s.trim()
    }

    fun fragment(code: String, params: List<NeoParam>): String {
        val sb = StringBuilder()
        sb.append("#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n")
        sb.append("uniform sampler2D image;\nuniform vec2 resolution;\nuniform float time;\nvarying vec2 vUv;\n")
        for (p in params) {
            sb.append("uniform float ").append(p.id).append(";\n")
        }
        sb.append(code).append("\n")
        if (!code.contains("void main")) {
            sb.append("void main() { gl_FragColor = effect(vUv); }\n")
        }
        return sb.toString()
    }

    fun parse(text: String, builtIn: Boolean): NeoEffect {
        val o = try {
            JSONObject(clean(text))
        } catch (e: Exception) {
            throw IllegalArgumentException("Not valid JSON. Send only one JSON object. (" + (e.message ?: "") + ")")
        }
        val name = o.optString("name", "")
        var id = o.optString("id", "").lowercase().replace(Regex("[^a-z0-9_]"), "_")
        if (id.isEmpty()) id = name.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        require(id.isNotEmpty()) { "Missing \"id\" or \"name\"" }

        val raw = o.opt("shader")
        require(raw != null && raw != JSONObject.NULL) { "Missing \"shader\"" }
        var shader = if (raw is JSONArray) {
            val sb = StringBuilder()
            for (i in 0 until raw.length()) sb.append(raw.getString(i)).append("\n")
            sb.toString()
        } else {
            raw.toString()
        }
        require(!shader.contains("half4") && !shader.contains("float2") && !shader.contains("float3") && !shader.contains("uniform shader")) {
            "This looks like AGSL/HLSL (half4/float2). Use GLSL: vec2, vec3, vec4"
        }

        val ps = ArrayList<NeoParam>()
        val arr = o.optJSONArray("params")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val pid = p.getString("id")
                require(Regex("[a-zA-Z_][a-zA-Z0-9_]*").matches(pid)) { "Bad param id: " + pid }
                require(pid !in BAD_IDS) { "Param id '" + pid + "' is not allowed (reserved or a GLSL name). Pick another." }
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
        shader = sanitize(shader, ps.map { it.id })
        require(shader.contains("effect(") || shader.contains("void main")) { "shader must define: vec4 effect(vec2 uv)" }
        return NeoEffect(id, if (name.isEmpty()) id else name, o.optString("category", "Custom"), ps, shader, builtIn)
    }

    fun toJson(e: NeoEffect): String {
        val o = JSONObject()
        o.put("neofx", 2)
        o.put("id", e.id)
        o.put("name", e.name)
        o.put("category", e.category)
        val a = JSONArray()
        for (p in e.params) {
            val po = JSONObject()
            po.put("id", p.id)
            po.put("name", p.name)
            po.put("min", p.min.toDouble())
            po.put("max", p.max.toDouble())
            po.put("default", p.def.toDouble())
            a.put(po)
        }
        o.put("params", a)
        o.put("shader", e.shader.replace("\n", " "))
        return o.toString()
    }

    fun fixPrompt(json: String, err: String): String {
        return "The NeoEffects app could not use the effect below.\nError: " + err +
            "\n\nFix it. Rules: GLSL ES 1.00, define only vec4 effect(vec2 uv), shader on ONE line, " +
            "no main(), no uniform declarations for image/resolution/time/params, every float needs a decimal point (1.0), " +
            "never use half4/float2. Reply with the full corrected JSON only, no markdown.\n\n" + json
    }
}

object EffectStore {
    fun load(ctx: Context): List<NeoEffect> {
        val out = ArrayList<NeoEffect>()
        val names = ctx.assets.list("effects")
        if (names != null) {
            for (n in names.sorted()) {
                try {
                    val t = ctx.assets.open("effects/" + n).bufferedReader().use { it.readText() }
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
        File(dir, fx.id + ".neofx").writeText(NeoFx.toJson(fx))
        return fx
    }

    fun delete(ctx: Context, id: String) {
        File(File(ctx.filesDir, "effects"), id + ".neofx").delete()
    }
}

const val AI_PROMPT = """You are writing a visual effect for the NeoEffects video editor. Reply with ONE JSON object only. No markdown, no code fences, no explanation.

JSON format:
{
  "neofx": 2,
  "id": "lowercase_id_with_underscores",
  "name": "Display Name",
  "category": "Glitch",
  "params": [
    { "id": "amount", "name": "Amount", "min": 0, "max": 50, "default": 10 }
  ],
  "shader": "vec4 effect(vec2 uv) { ... }"
}

The "shader" value is GLSL ES 1.00 (OpenGL ES 2.0, the same language as WebGL 1). It runs once for every pixel. Write it on ONE line (no line breaks, no // comments).

You define exactly one entry function:
  vec4 effect(vec2 uv)
uv goes from 0.0 to 1.0 and (0.0, 0.0) is the TOP-LEFT of the frame. Return the final pixel colour as vec4(r, g, b, a), values 0.0 to 1.0.

The app already provides these. NEVER declare them and NEVER write main() or precision:
  uniform sampler2D image;   // the current frame
  uniform vec2 resolution;   // frame size in pixels
  uniform float time;        // seconds
  plus one "uniform float" for every entry in params, named exactly like its id.

Rules that avoid compile errors:
1. Read pixels with texture2D(image, coord). coord is 0.0-1.0, not pixels. One pixel = 1.0 / resolution.
2. Every number with a decimal needs the point: write 1.0, not 1. No implicit int to float conversion.
3. Only use float, int, bool, vec2, vec3, vec4. NEVER use half4, float2, float3, fixed or any HLSL/Metal/AGSL types.
4. Loops need a constant limit, like for (int i = 0; i < 8; i++). No while loops. No dynamic array indexing.
5. Use mod(x, y), never %. Use atan(y, x) for angles.
6. Helper functions are allowed above effect() and must be declared before they are used.
7. Param ids must be new names. Do NOT use: image, resolution, time, input, output, sample, half, mix, min, max, step, pow, or any other GLSL function name.
8. Do not use double quotes inside the shader. Keep the shader under about 1500 characters.
9. Every param must really be used in the shader.
10. For random numbers use: float rnd(vec2 s) { return fract(sin(dot(s, vec2(12.9898, 78.233))) * 43758.5453); }

Working examples:

{"neofx":2,"id":"wave","name":"Wave","category":"Distort","params":[{"id":"strength","name":"Strength","min":0,"max":60,"default":20}],"shader":"vec4 effect(vec2 uv) { vec2 q = uv; q.x += sin(uv.y * resolution.y / 40.0 + time * 3.0) * strength / resolution.x; return texture2D(image, q); }"}

{"neofx":2,"id":"soft_blur","name":"Soft Blur","category":"Blur","params":[{"id":"radius","name":"Radius","min":0,"max":40,"default":8}],"shader":"vec4 effect(vec2 uv) { vec2 px = radius / resolution; vec4 c = texture2D(image, uv); float n = 1.0; for (int i = 0; i < 8; i++) { float a = float(i) * 0.785398; vec2 d = vec2(cos(a), sin(a)); c += texture2D(image, uv + d * px); n += 1.0; } return c / n; }"}

{"neofx":2,"id":"flicker_split","name":"Flicker Split","category":"Glitch","params":[{"id":"amount","name":"Amount","min":0,"max":40,"default":12},{"id":"speed","name":"Speed","min":0.5,"max":10,"default":4}],"shader":"float rnd(float x) { return fract(sin(x * 91.345) * 47453.5453); } vec4 effect(vec2 uv) { float t = floor(time * speed * 6.0); float s = (rnd(t) - 0.5) * 2.0 * amount / resolution.x; float r = texture2D(image, uv + vec2(s, 0.0)).r; vec4 c = texture2D(image, uv); float b = texture2D(image, uv - vec2(s, 0.0)).b; return vec4(r, c.g, b, c.a); }"}

Now write the effect I describe. Output only the JSON.

The effect I want:
"""
