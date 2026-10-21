package com.cusil.neoeffects

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FxRenderer : GLSurfaceView.Renderer {
    var onError: (String) -> Unit = {}

    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var bitmapDirty = false
    @Volatile private var effect: NeoEffect? = null
    @Volatile private var effectDirty = false
    @Volatile var values: Map<String, Float> = emptyMap()

    private val main = Handler(Looper.getMainLooper())
    private var prog = 0
    private var tex = 0
    private var texW = 1
    private var texH = 1
    private var vw = 1
    private var vh = 1
    private var params: List<NeoParam> = emptyList()
    private val locs = HashMap<String, Int>()
    private val startNs = System.nanoTime()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        quad.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val b: Buffer = quad
        b.position(0)
    }

    fun setBitmap(b: Bitmap) {
        bitmap = b
        bitmapDirty = true
    }

    fun setEffect(e: NeoEffect?) {
        effect = e
        effectDirty = true
    }

    private fun report(m: String) {
        main.post { onError(m) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = 0
        tex = 0
        bitmapDirty = true
        effectDirty = true
        GLES20.glClearColor(0.04f, 0.04f, 0.07f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        vw = width
        vh = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glViewport(0, 0, vw, vh)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (bitmapDirty) {
            bitmapDirty = false
            val b = bitmap
            if (b != null && !b.isRecycled) upload(b)
        }
        if (effectDirty) {
            effectDirty = false
            build(effect)
        }
        if (prog == 0 || tex == 0) return

        val at = texW.toFloat() / texH.toFloat()
        val av = vw.toFloat() / vh.toFloat()
        var w = vw
        var h = vh
        if (at > av) {
            h = (vw / at).toInt()
        } else {
            w = (vh * at).toInt()
        }
        GLES20.glViewport((vw - w) / 2, (vh - h) / 2, w, h)

        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(locs["image"] ?: -1, 0)
        GLES20.glUniform2f(locs["resolution"] ?: -1, w.toFloat(), h.toFloat())
        GLES20.glUniform1f(locs["time"] ?: -1, (System.nanoTime() - startNs) / 1_000_000_000f)
        val vals = values
        for (p in params) {
            val l = locs[p.id] ?: -1
            if (l >= 0) GLES20.glUniform1f(l, vals[p.id] ?: p.def)
        }

        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun upload(b: Bitmap) {
        if (tex == 0) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            tex = t[0]
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        texW = b.width
        texH = b.height
    }

    private fun build(e: NeoEffect?) {
        if (prog != 0) {
            GLES20.glDeleteProgram(prog)
            prog = 0
        }
        var p: List<NeoParam> = emptyList()
        var code = NeoFx.PASSTHROUGH
        if (e != null) {
            p = e.params
            code = e.shader
        }
        try {
            prog = link(NeoFx.VERTEX, NeoFx.fragment(code, p))
            params = p
            report("")
        } catch (ex: Exception) {
            report(ex.message ?: "shader error")
            try {
                prog = link(NeoFx.VERTEX, NeoFx.fragment(NeoFx.PASSTHROUGH, emptyList()))
                params = emptyList()
            } catch (ex2: Exception) {
                prog = 0
            }
        }
        locs.clear()
        if (prog != 0) {
            for (q in params) locs[q.id] = GLES20.glGetUniformLocation(prog, q.id)
            locs["image"] = GLES20.glGetUniformLocation(prog, "image")
            locs["resolution"] = GLES20.glGetUniformLocation(prog, "resolution")
            locs["time"] = GLES20.glGetUniformLocation(prog, "time")
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val st = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException(log)
        }
        return s
    }

    private fun link(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f: Int
        try {
            f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        } catch (e: Exception) {
            GLES20.glDeleteShader(v)
            throw e
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glBindAttribLocation(p, 0, "aPos")
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        val st = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException(log)
        }
        return p
    }
}

@Composable
fun GlPreview(
    bitmap: Bitmap,
    fx: NeoEffect?,
    values: Map<String, Float>,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val r = remember { FxRenderer() }
    val snapshot = values.toMap()
    SideEffect {
        r.onError = onError
        r.values = snapshot
    }
    LaunchedEffect(bitmap) { r.setBitmap(bitmap) }
    LaunchedEffect(fx) { r.setEffect(fx) }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            GLSurfaceView(c).apply {
                setEGLContextClientVersion(2)
                setRenderer(r)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
    )
}
