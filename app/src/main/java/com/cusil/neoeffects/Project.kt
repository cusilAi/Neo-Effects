package com.cusil.neoeffects

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

class Key(val t: Long, val v: Float)

class FxInst(val effect: NeoEffect) {
    val values = mutableStateMapOf<String, Float>()
    val keys = mutableStateMapOf<String, List<Key>>()

    init {
        for (p in effect.params) values[p.id] = p.def
    }

    fun valueAt(p: NeoParam, tMs: Long): Float {
        val ks = keys[p.id]
        if (ks == null || ks.isEmpty()) return values[p.id] ?: p.def
        if (tMs <= ks.first().t) return ks.first().v
        if (tMs >= ks.last().t) return ks.last().v
        for (i in 0 until ks.size - 1) {
            val a = ks[i]
            val b = ks[i + 1]
            if (tMs <= b.t) {
                val f = (tMs - a.t).toFloat() / maxOf(1L, b.t - a.t).toFloat()
                return a.v + (b.v - a.v) * f
            }
        }
        return ks.last().v
    }

    fun setKey(pid: String, tMs: Long, v: Float) {
        val old = keys[pid] ?: emptyList()
        val list = ArrayList<Key>()
        for (k in old) if (abs(k.t - tMs) >= 50L) list.add(k)
        list.add(Key(tMs, v))
        keys[pid] = list.sortedBy { it.t }
    }

    fun removeKey(pid: String, tMs: Long) {
        val old = keys[pid] ?: return
        val rest = old.filter { abs(it.t - tMs) >= 50L }
        if (rest.isEmpty()) keys.remove(pid) else keys[pid] = rest
    }
}

class Clip(val id: Long, val uri: String, val isVideo: Boolean, val srcDurMs: Long, val name: String) {
    var inMs by mutableLongStateOf(0L)
    var outMs by mutableLongStateOf(srcDurMs)
    var speed by mutableFloatStateOf(1f)
    val fx = mutableStateListOf<FxInst>()
    var thumb by mutableStateOf<Bitmap?>(null)

    val durMs: Long
        get() = maxOf(100L, ((outMs - inMs) / speed).toLong())

    fun duplicate(newId: Long, shiftKeysMs: Long): Clip {
        val n = Clip(newId, uri, isVideo, srcDurMs, name)
        n.inMs = inMs
        n.outMs = outMs
        n.speed = speed
        n.thumb = thumb
        for (f in fx) {
            val g = FxInst(f.effect)
            for ((k, v) in f.values) g.values[k] = v
            for ((k, list) in f.keys) g.keys[k] = list.map { Key(it.t - shiftKeysMs, it.v) }
            n.fx.add(g)
        }
        return n
    }
}

fun newClipId(): Long = System.nanoTime()

class Project(val id: String, var name: String) {
    val clips = mutableStateListOf<Clip>()
    var playheadMs by mutableLongStateOf(0L)

    val totalMs: Long
        get() {
            var t = 0L
            for (c in clips) t += c.durMs
            return t
        }

    fun startOf(c: Clip): Long {
        var t = 0L
        for (x in clips) {
            if (x === c) return t
            t += x.durMs
        }
        return t
    }

    fun clipAt(t: Long): Pair<Clip, Long>? {
        if (clips.isEmpty()) return null
        var s = 0L
        for (c in clips) {
            val d = c.durMs
            if (t < s + d) return Pair(c, maxOf(0L, t - s))
            s += d
        }
        val last = clips.last()
        return Pair(last, last.durMs)
    }

    fun splitAtPlayhead(): Boolean {
        val loc = clipAt(playheadMs) ?: return false
        val c = loc.first
        val off = loc.second
        if (off < 200L || c.durMs - off < 200L) return false
        val cut = c.inMs + (off * c.speed).toLong()
        val b = c.duplicate(newClipId(), off)
        b.inMs = cut
        c.outMs = cut
        clips.add(clips.indexOf(c) + 1, b)
        return true
    }
}

object ProjectStore {
    private fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "projects")
        d.mkdirs()
        return d
    }

    fun toJson(p: Project): JSONObject {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("name", p.name)
        o.put("playhead", p.playheadMs)
        val cs = JSONArray()
        for (c in p.clips) {
            val co = JSONObject()
            co.put("id", c.id)
            co.put("uri", c.uri)
            co.put("video", c.isVideo)
            co.put("name", c.name)
            co.put("srcDur", c.srcDurMs)
            co.put("in", c.inMs)
            co.put("out", c.outMs)
            co.put("speed", c.speed.toDouble())
            val fa = JSONArray()
            for (f in c.fx) {
                val fo = JSONObject()
                fo.put("id", f.effect.id)
                val vo = JSONObject()
                for ((k, v) in f.values) vo.put(k, v.toDouble())
                fo.put("values", vo)
                val ko = JSONObject()
                for ((k, list) in f.keys) {
                    val ka = JSONArray()
                    for (key in list) {
                        val pair = JSONArray()
                        pair.put(key.t)
                        pair.put(key.v.toDouble())
                        ka.put(pair)
                    }
                    ko.put(k, ka)
                }
                fo.put("keys", ko)
                fa.put(fo)
            }
            co.put("fx", fa)
            cs.put(co)
        }
        o.put("clips", cs)
        return o
    }

    fun fromJson(o: JSONObject, lib: List<NeoEffect>): Project {
        val p = Project(o.getString("id"), o.optString("name", "Project"))
        val cs = o.optJSONArray("clips")
        if (cs != null) {
            for (i in 0 until cs.length()) {
                val co = cs.getJSONObject(i)
                val c = Clip(
                    co.getLong("id"), co.getString("uri"), co.getBoolean("video"),
                    co.getLong("srcDur"), co.optString("name", "clip")
                )
                c.inMs = co.getLong("in")
                c.outMs = co.getLong("out")
                c.speed = co.optDouble("speed", 1.0).toFloat()
                val fa = co.optJSONArray("fx")
                if (fa != null) {
                    for (j in 0 until fa.length()) {
                        val fo = fa.getJSONObject(j)
                        val fid = fo.getString("id")
                        val e = lib.firstOrNull { it.id == fid } ?: continue
                        val inst = FxInst(e)
                        val vo = fo.optJSONObject("values")
                        if (vo != null) {
                            val it2 = vo.keys()
                            while (it2.hasNext()) {
                                val k = it2.next()
                                inst.values[k] = vo.getDouble(k).toFloat()
                            }
                        }
                        val ko = fo.optJSONObject("keys")
                        if (ko != null) {
                            val it3 = ko.keys()
                            while (it3.hasNext()) {
                                val k = it3.next()
                                val ka = ko.getJSONArray(k)
                                val list = ArrayList<Key>()
                                for (m in 0 until ka.length()) {
                                    val pr = ka.getJSONArray(m)
                                    list.add(Key(pr.getLong(0), pr.getDouble(1).toFloat()))
                                }
                                inst.keys[k] = list
                            }
                        }
                        c.fx.add(inst)
                    }
                }
                p.clips.add(c)
            }
        }
        p.playheadMs = o.optLong("playhead", 0L)
        return p
    }

    fun save(ctx: Context, p: Project) {
        File(dir(ctx), p.id + ".json").writeText(toJson(p).toString())
    }

    fun list(ctx: Context): List<Triple<String, String, Long>> {
        val fs = dir(ctx).listFiles() ?: return emptyList()
        val out = ArrayList<Triple<String, String, Long>>()
        for (f in fs.sortedByDescending { it.lastModified() }) {
            if (!f.name.endsWith(".json")) continue
            try {
                val o = JSONObject(f.readText())
                out.add(Triple(o.getString("id"), o.optString("name", "Project"), f.lastModified()))
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun load(ctx: Context, id: String, lib: List<NeoEffect>): Project? {
        return try {
            fromJson(JSONObject(File(dir(ctx), id + ".json").readText()), lib)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), id + ".json").delete()
    }
}
