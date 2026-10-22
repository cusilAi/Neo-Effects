package com.cusil.neoeffects

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Probe(val isVideo: Boolean, val durMs: Long, val name: String)

fun probeMedia(ctx: Context, uri: Uri): Probe? {
    val mime = ctx.contentResolver.getType(uri) ?: ""
    val video = mime.startsWith("video")
    var name = "clip"
    try {
        val c = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (c != null) {
            c.use {
                if (it.moveToFirst()) name = it.getString(0) ?: "clip"
            }
        }
    } catch (e: Exception) {
    }
    if (!video) return Probe(false, 10000L, name)
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (d <= 0L) null else Probe(true, d, name)
    } catch (e: Exception) {
        null
    } finally {
        try { r.release() } catch (e: Exception) { }
    }
}

fun loadThumb(ctx: Context, c: Clip): Bitmap? {
    return try {
        if (c.isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, Uri.parse(c.uri))
                val f = r.getFrameAtTime(c.inMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (f == null) {
                    null
                } else {
                    val h = 120
                    val w = maxOf(1, f.width * h / f.height)
                    Bitmap.createScaledBitmap(f, w, h, true)
                }
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
        } else {
            loadBitmap(ctx, Uri.parse(c.uri), 256)
        }
    } catch (e: Exception) {
        null
    }
}

class PlaybackEngine(private val ctx: Context, val project: Project, private val gl: GlEngine) {
    private var mp: MediaPlayer? = null
    private var surface: Surface? = null
    private var loadedId = -1L
    private var ready = false
    private var pendingSeek = 0L
    private var lastSrc = 0L
    private var wantPlay = false
    private val photos = HashMap<String, Bitmap>()

    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf("")

    fun onSurface(s: Surface) {
        surface = s
        val p = mp
        if (p != null) {
            try { p.setSurface(s) } catch (e: Exception) { }
            if (ready) seek(lastSrc, false)
        }
    }

    fun show(tMs: Long, fast: Boolean = false) {
        val total = project.totalMs
        project.playheadMs = tMs.coerceIn(0L, total)
        val loc = project.clipAt(project.playheadMs)
        if (loc == null) {
            releasePlayer()
            gl.clear()
            return
        }
        val c = loc.first
        if (!c.isVideo) {
            releasePlayer()
            gl.setPhoto(photo(c))
            return
        }
        gl.setVideoMode()
        val src = c.inMs + (loc.second * c.speed).toLong()
        lastSrc = src
        ensureLoaded(c, src, fast)
    }

    private fun photo(c: Clip): Bitmap {
        val cached = photos[c.uri]
        if (cached != null) return cached
        val b = loadBitmap(ctx, Uri.parse(c.uri), 1600) ?: Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        photos[c.uri] = b
        return b
    }

    private fun ensureLoaded(c: Clip, srcMs: Long, fast: Boolean) {
        if (loadedId == c.id && mp != null) {
            if (ready) {
                seek(srcMs, fast)
                if (wantPlay) startMp(c) else pauseMp()
            } else {
                pendingSeek = srcMs
            }
            return
        }
        releasePlayer()
        val p = MediaPlayer()
        mp = p
        loadedId = c.id
        ready = false
        pendingSeek = srcMs
        try {
            p.setDataSource(ctx, Uri.parse(c.uri))
            val s = gl.currentSurface ?: surface
            if (s != null) p.setSurface(s)
            p.setOnVideoSizeChangedListener { _, w, h -> gl.setVideoSize(w, h) }
            p.setOnPreparedListener { pl ->
                ready = true
                gl.setVideoSize(pl.videoWidth, pl.videoHeight)
                seek(pendingSeek, false)
                if (wantPlay) startMp(c) else pauseMp()
            }
            p.setOnErrorListener { _, what, extra ->
                error = "Video error " + what + "/" + extra
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            error = e.message ?: "Cannot open video"
        }
    }

    private fun seek(ms: Long, fast: Boolean) {
        val p = mp ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                p.seekTo(ms, if (fast) MediaPlayer.SEEK_CLOSEST_SYNC else MediaPlayer.SEEK_CLOSEST)
            } else {
                p.seekTo(ms.toInt())
            }
        } catch (e: Exception) {
        }
    }

    private fun startMp(c: Clip) {
        val p = mp ?: return
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                p.playbackParams = PlaybackParams().setSpeed(c.speed)
            }
            if (!p.isPlaying) p.start()
        } catch (e: Exception) {
        }
    }

    private fun pauseMp() {
        val p = mp ?: return
        try {
            if (p.isPlaying) p.pause()
        } catch (e: Exception) {
        }
    }

    private fun releasePlayer() {
        val p = mp
        mp = null
        loadedId = -1L
        ready = false
        if (p != null) {
            try {
                p.setOnPreparedListener(null)
                p.release()
            } catch (e: Exception) {
            }
        }
    }

    fun play() {
        if (project.clips.isEmpty()) return
        if (project.playheadMs >= project.totalMs - 50L) project.playheadMs = 0L
        wantPlay = true
        playing = true
        show(project.playheadMs)
    }

    fun pause() {
        wantPlay = false
        playing = false
        pauseMp()
    }

    fun tick(dtMs: Long) {
        if (!playing) return
        val total = project.totalMs
        val next = project.playheadMs + dtMs
        if (next >= total) {
            project.playheadMs = total
            pause()
            return
        }
        val before = project.clipAt(project.playheadMs)?.first
        val after = project.clipAt(next)?.first
        project.playheadMs = next
        if (before !== after) {
            val same = before != null && after != null && before.isVideo && after.isVideo &&
                before.uri == after.uri && before.outMs == after.inMs && before.speed == after.speed &&
                mp != null && ready
            if (same && after != null) {
                loadedId = after.id
            } else {
                show(next)
            }
        }
    }

    fun releaseAll() {
        releasePlayer()
        photos.clear()
    }
}
