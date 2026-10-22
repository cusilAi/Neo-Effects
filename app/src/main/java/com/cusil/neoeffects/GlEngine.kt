package com.cusil.neoeffects

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Pass(val effect: NeoEffect, val values: Map<String, Float>)

class GlEngine : GLSurfaceView.Renderer {
    @Volatile var chain: List<Pass> = emptyList()
    @Volatile var timeSec: Float = -1f
    @Volatile var onError: (String) -> Unit = {}
    @Volatile var onSurface: (Surface) -> Unit = {}
    @Volatile var currentSurface: Surface? = null

    @Volatile private var photo: Bitmap? = null
    @Volatile private var photoDirty = false
    @Volatile private var mode = 0
    @Volatile private var vidW = 0
    @Volatile private var vidH = 0

    private val main = Handler(Looper.getMainLooper())
    private val frameFlag = AtomicBoolean(false)
    private val startNs = System.nanoTime()

    private var oesTex = 0
    private var st: SurfaceTexture? = null
    private var photoTex = 0
    private var photoW = 1
    private var photoH = 1
    private val fbo = IntArray(2)
    private val fboTex = IntArray(2)
    private var fw = 0
    private var fh = 0
    private var vw = 1
    private var vh = 1
    private var copyProg = 0
    private val programs = HashMap<String, Int>()
    private val failed = ConcurrentHashMap<String, String>()
    private val texMat = FloatArray(16)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        quad.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val b: Buffer = quad
        b.position(0)
        android.opengl.Matrix.setIdentityM(texMat, 0)
    }

    fun setPhoto(b: Bitmap) {
        photo = b
        photoDirty = true
        mode = 1
    }

    fun setVideoMode() {
        mode = 2
    }

    fun clear() {
        mode = 0
    }

    fun setVideoSize(w: Int, h: Int) {
        vidW = w
        vidH = h
    }

    fun retry() {
        failed.clear()
    }

    private fun report(m: String) {
        main.post { onError(m) }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        programs.clear()
        copyProg = 0
        photoTex = 0
        fw = 0
        fh = 0
        fboTex[0] = 0
        fboTex[1] = 0
        fbo[0] = 0
        fbo[1] = 0
        photoDirty = photo != null
        GLES20.glClearColor(0.04f, 0.04f, 0.07f, 1f)

        try { st?.release() } catch (e: Exception) { }
        try { currentSurface?.release() } catch (e: Exception) { }

        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        oesTex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val s = SurfaceTexture(oesTex)
        s.setOnFrameAvailableListener { frameFlag.set(true) }
        st = s
        val sf = Surface(s)
        currentSurface = sf
        main.post { onSurface(sf) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        vw = width
        vh = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, vw, vh)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val m = mode
        if (m == 0 || vw < 2 || vh < 2) return

        var sw = 1
        var sh = 1
        var cur = 0
        var curIdx = -1
        if (m == 1) {
            if (photoDirty) {
                photoDirty = false
                val b = photo
                if (b != null && !b.isRecycled) uploadPhoto(b)
            }
            if (photoTex == 0) return
            sw = photoW
            sh = photoH
            cur = photoTex
        } else {
            if (vidW <= 0 || vidH <= 0) return
            sw = vidW
            sh = vidH
        }
        val scale = minOf(1f, 1280f / maxOf(sw, sh).toFloat())
        val w = maxOf(2, (sw * scale).toInt())
        val h = maxOf(2, (sh * scale).toInt())
        ensureFbo(w, h)

        if (m == 2) {
            val s = st ?: return
            if (frameFlag.getAndSet(false)) {
                try { s.updateTexImage() } catch (e: Exception) { }
            }
            try { s.getTransformMatrix(texMat) } catch (e: Exception) { }
            drawExternal(w, h)
            cur = fboTex[0]
            curIdx = 0
        }

        val tsec = if (timeSec >= 0f) timeSec else (System.nanoTime() - startNs) / 1_000_000_000f
        val progs = ArrayList<Int>()
        val used = ArrayList<Pass>()
        for (p in chain) {
            val pr = programFor(p.effect)
            if (pr != 0) {
                progs.add(pr)
                used.add(p)
            }
        }

        if (used.isEmpty()) {
            val pp = programFor(null)
            if (pp != 0) {
                bindScreen(w, h)
                drawProgram(pp, cur, w, h, tsec, null, 1f)
            }
            return
        }
        for (i in used.indices) {
            val last = i == used.size - 1
            val outIdx = if (curIdx == 0) 1 else 0
            if (last) bindScreen(w, h) else bindFbo(outIdx, w, h)
            drawProgram(progs[i], cur, w, h, tsec, used[i], if (last) 1f else -1f)
            if (!last) {
                cur = fboTex[outIdx]
                curIdx = outIdx
            }
        }
    }

    private fun bindScreen(w: Int, h: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val at = w.toFloat() / h.toFloat()
        val av = vw.toFloat() / vh.toFloat()
        var rw = vw
        var rh = vh
        if (at > av) {
            rh = (vw / at).toInt()
        } else {
            rw = (vh * at).toInt()
        }
        GLES20.glViewport((vw - rw) / 2, (vh - rh) / 2, rw, rh)
    }

    private fun bindFbo(idx: Int, w: Int, h: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[idx])
        GLES20.glViewport(0, 0, w, h)
    }

    private fun loc(prog: Int, name: String): Int = GLES20.glGetUniformLocation(prog, name)

    private fun drawQuad() {
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawProgram(prog: Int, tex: Int, w: Int, h: Int, tsec: Float, pass: Pass?, flip: Float) {
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(loc(prog, "image"), 0)
        GLES20.glUniform2f(loc(prog, "resolution"), w.toFloat(), h.toFloat())
        GLES20.glUniform1f(loc(prog, "time"), tsec)
        GLES20.glUniform1f(loc(prog, "uFlip"), flip)
        if (pass != null) {
            for (p in pass.effect.params) {
                val l = loc(prog, p.id)
                if (l >= 0) GLES20.glUniform1f(l, pass.values[p.id] ?: p.def)
            }
        }
        drawQuad()
    }

    private fun drawExternal(w: Int, h: Int) {
        if (copyProg == 0) {
            val fs = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vUv;\n" +
                "uniform samplerExternalOES tex;\n" +
                "uniform mat4 uTex;\n" +
                "void main() { vec2 st = vec2(vUv.x, 1.0 - vUv.y); gl_FragColor = texture2D(tex, (uTex * vec4(st, 0.0, 1.0)).xy); }\n"
            try {
                copyProg = link(NeoFx.VERTEX, fs)
            } catch (e: Exception) {
                report("Video shader: " + (e.message ?: "error"))
                return
            }
        }
        bindFbo(0, w, h)
        GLES20.glUseProgram(copyProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(loc(copyProg, "tex"), 0)
        GLES20.glUniformMatrix4fv(loc(copyProg, "uTex"), 1, false, texMat, 0)
        GLES20.glUniform1f(loc(copyProg, "uFlip"), -1f)
        drawQuad()
    }

    private fun ensureFbo(w: Int, h: Int) {
        if (fw == w && fh == h && fboTex[0] != 0) return
        if (fboTex[0] != 0) {
            GLES20.glDeleteTextures(2, fboTex, 0)
            GLES20.glDeleteFramebuffers(2, fbo, 0)
        }
        GLES20.glGenTextures(2, fboTex, 0)
        GLES20.glGenFramebuffers(2, fbo, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex[i])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex[i], 0)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        fw = w
        fh = h
    }

    private fun uploadPhoto(b: Bitmap) {
        if (photoTex == 0) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            photoTex = t[0]
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, photoTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        photoW = b.width
        photoH = b.height
    }

    private fun programFor(e: NeoEffect?): Int {
        val key = if (e == null) "__pass" else e.id + ":" + e.shader.hashCode() + ":" + e.params.size
        val have = programs[key]
        if (have != null) return have
        if (failed.containsKey(key)) return 0
        return try {
            val code = e?.shader ?: NeoFx.PASSTHROUGH
            val ps = e?.params ?: emptyList()
            val p = link(NeoFx.VERTEX, NeoFx.fragment(code, ps))
            programs[key] = p
            p
        } catch (ex: Exception) {
            val msg = (e?.name ?: "pass") + ": " + (ex.message ?: "shader error")
            failed[key] = msg
            report(msg)
            0
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
        val ls = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ls, 0)
        if (ls[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException(log)
        }
        return p
    }
}

@Composable
fun GlPreview(engine: GlEngine, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            GLSurfaceView(c).apply {
                setEGLContextClientVersion(2)
                setRenderer(engine)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
    )
}
