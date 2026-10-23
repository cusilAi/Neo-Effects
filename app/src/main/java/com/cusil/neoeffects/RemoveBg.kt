@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CutState {
    var w = 0
    var h = 0
    var mask = ByteArray(0)
    var auto = ByteArray(0)
    var px = IntArray(0)
    var out: Bitmap? = null

    fun init(work: Bitmap) {
        w = work.width
        h = work.height
        px = IntArray(w * h)
        work.getPixels(px, 0, w, 0, 0, w, h)
        mask = ByteArray(w * h) { 0xFF.toByte() }
        auto = mask.copyOf()
        out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        rebuild(0, 0, w - 1, h - 1)
    }

    fun rebuild(x0: Int, y0: Int, x1: Int, y1: Int) {
        val o = out ?: return
        val rw = x1 - x0 + 1
        val rh = y1 - y0 + 1
        if (rw <= 0 || rh <= 0) return
        val arr = IntArray(rw * rh)
        for (y in 0 until rh) {
            for (x in 0 until rw) {
                val i = (y0 + y) * w + (x0 + x)
                arr[y * rw + x] = ((mask[i].toInt() and 0xFF) shl 24) or (px[i] and 0xFFFFFF)
            }
        }
        o.setPixels(arr, 0, rw, x0, y0, rw, rh)
    }

    fun paint(cx: Float, cy: Float, r: Float, mode: Int) {
        val x0 = maxOf(0, (cx - r).toInt())
        val x1 = minOf(w - 1, (cx + r).toInt())
        val y0 = maxOf(0, (cy - r).toInt())
        val y1 = minOf(h - 1, (cy + r).toInt())
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x - cx
                val dy = y - cy
                val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (d > r) continue
                val soft = ((r - d) / (r * 0.35f)).coerceIn(0f, 1f)
                val i = y * w + x
                val cur = mask[i].toInt() and 0xFF
                val nv = when (mode) {
                    0 -> maxOf(cur, (soft * 255f).toInt())
                    1 -> minOf(cur, ((1f - soft) * 255f).toInt())
                    else -> {
                        val a = auto[i].toInt() and 0xFF
                        (cur + (a - cur) * soft).toInt()
                    }
                }
                mask[i] = nv.coerceIn(0, 255).toByte()
            }
        }
        rebuild(x0, y0, x1, y1)
    }

    fun feather(radius: Int) {
        if (radius < 1) return
        val tmp = FloatArray(w * h)
        val res = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                var n = 0
                for (k in -radius..radius) {
                    val xx = x + k
                    if (xx < 0 || xx >= w) continue
                    s += (mask[y * w + xx].toInt() and 0xFF)
                    n++
                }
                tmp[y * w + x] = s / n
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                var n = 0
                for (k in -radius..radius) {
                    val yy = y + k
                    if (yy < 0 || yy >= h) continue
                    s += tmp[yy * w + x]
                    n++
                }
                res[y * w + x] = s / n
            }
        }
        for (i in res.indices) mask[i] = res[i].toInt().coerceIn(0, 255).toByte()
        rebuild(0, 0, w - 1, h - 1)
    }

    fun refine(s: Float) {
        val lo = 0.5f * s * 0.9f
        val hi = 1f - lo
        for (i in mask.indices) {
            val t = (mask[i].toInt() and 0xFF) / 255f
            val a = ((t - lo) / (hi - lo)).coerceIn(0f, 1f)
            mask[i] = (a * 255f).toInt().toByte()
        }
        rebuild(0, 0, w - 1, h - 1)
    }
}

fun autoSegment(bmp: Bitmap, done: (ByteArray?) -> Unit) {
    try {
        val opts = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        val seg = Segmentation.getClient(opts)
        seg.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { m ->
                try {
                    val mw = m.width
                    val mh = m.height
                    val buf = m.buffer
                    val conf = FloatArray(mw * mh)
                    for (i in conf.indices) conf[i] = buf.getFloat()
                    val res = ByteArray(bmp.width * bmp.height)
                    for (y in 0 until bmp.height) {
                        val sy = minOf(mh - 1, y * mh / bmp.height)
                        for (x in 0 until bmp.width) {
                            val sx = minOf(mw - 1, x * mw / bmp.width)
                            val c = conf[sy * mw + sx]
                            val a = ((c - 0.3f) / 0.4f).coerceIn(0f, 1f)
                            res[y * bmp.width + x] = (a * 255f).toInt().toByte()
                        }
                    }
                    done(res)
                } catch (e: Exception) {
                    done(null)
                }
                seg.close()
            }
            .addOnFailureListener {
                done(null)
                seg.close()
            }
    } catch (e: Exception) {
        done(null)
    }
}

fun DrawScope.drawCutout(
    x: Float, y: Float, w: Float, h: Float, bgMode: Int, solid: Color,
    blurImg: ImageBitmap?, customImg: ImageBitmap?, fg: ImageBitmap?, cell: Float
) {
    val off = IntOffset(x.toInt(), y.toInt())
    val sz = IntSize(w.toInt(), h.toInt())
    when (bgMode) {
        0 -> {
            var cy = 0
            var yy = y
            while (yy < y + h) {
                var cx = 0
                var xx = x
                while (xx < x + w) {
                    val c = if ((cx + cy) % 2 == 0) Color(0xFF3A3C48) else Color(0xFF22232C)
                    drawRect(c, Offset(xx, yy), Size(minOf(cell, x + w - xx), minOf(cell, y + h - yy)))
                    xx += cell
                    cx++
                }
                yy += cell
                cy++
            }
        }
        1 -> drawRect(solid, Offset(x, y), Size(w, h))
        2 -> {
            if (blurImg != null) drawImage(blurImg, dstOffset = off, dstSize = sz) else drawRect(Color(0xFF22232C), Offset(x, y), Size(w, h))
        }
        else -> {
            if (customImg != null) {
                val ia = customImg.width.toFloat() / customImg.height.toFloat()
                val da = w / h
                var sw = customImg.width
                var sh = customImg.height
                if (ia > da) sw = (customImg.height * da).toInt() else sh = (customImg.width / da).toInt()
                val so = IntOffset((customImg.width - sw) / 2, (customImg.height - sh) / 2)
                drawImage(customImg, srcOffset = so, srcSize = IntSize(sw, sh), dstOffset = off, dstSize = sz)
            } else {
                drawRect(Color(0xFF22232C), Offset(x, y), Size(w, h))
            }
        }
    }
    if (fg != null) drawImage(fg, dstOffset = off, dstSize = sz)
}

private fun sampleMask(mask: ByteArray, mw: Int, mh: Int, fx: Float, fy: Float): Int {
    val x = fx.coerceIn(0f, (mw - 1).toFloat())
    val y = fy.coerceIn(0f, (mh - 1).toFloat())
    val x0 = x.toInt()
    val y0 = y.toInt()
    val x1 = minOf(mw - 1, x0 + 1)
    val y1 = minOf(mh - 1, y0 + 1)
    val tx = x - x0
    val ty = y - y0
    val a = (mask[y0 * mw + x0].toInt() and 0xFF) * (1 - tx) + (mask[y0 * mw + x1].toInt() and 0xFF) * tx
    val b = (mask[y1 * mw + x0].toInt() and 0xFF) * (1 - tx) + (mask[y1 * mw + x1].toInt() and 0xFF) * tx
    return (a * (1 - ty) + b * ty).toInt().coerceIn(0, 255)
}

fun renderCutout(full: Bitmap, mask: ByteArray, mw: Int, mh: Int, outMax: Int, bgMode: Int, solid: Int, custom: Bitmap?): Bitmap {
    val sc = minOf(1f, outMax.toFloat() / maxOf(full.width, full.height).toFloat())
    val ow = maxOf(2, (full.width * sc).toInt())
    val oh = maxOf(2, (full.height * sc).toInt())
    val scaled = Bitmap.createScaledBitmap(full, ow, oh, true)
    val px = IntArray(ow * oh)
    scaled.getPixels(px, 0, ow, 0, 0, ow, oh)
    for (y in 0 until oh) {
        val fy = (y + 0.5f) * mh / oh - 0.5f
        for (x in 0 until ow) {
            val fx = (x + 0.5f) * mw / ow - 0.5f
            val a = sampleMask(mask, mw, mh, fx, fy)
            px[y * ow + x] = (a shl 24) or (px[y * ow + x] and 0xFFFFFF)
        }
    }
    val fg = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    fg.setPixels(px, 0, ow, 0, 0, ow, oh)
    if (bgMode == 0) return fg
    val res = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(res)
    val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
    if (bgMode == 1) {
        cv.drawColor(solid)
    } else if (bgMode == 2) {
        val tiny = Bitmap.createScaledBitmap(scaled, maxOf(1, ow / 24), maxOf(1, oh / 24), true)
        cv.drawBitmap(tiny, null, Rect(0, 0, ow, oh), paint)
    } else if (custom != null) {
        val ia = custom.width.toFloat() / custom.height.toFloat()
        val da = ow.toFloat() / oh.toFloat()
        var sw = custom.width
        var sh = custom.height
        if (ia > da) sw = (custom.height * da).toInt() else sh = (custom.width / da).toInt()
        val l = (custom.width - sw) / 2
        val t = (custom.height - sh) / 2
        cv.drawBitmap(custom, Rect(l, t, l + sw, t + sh), Rect(0, 0, ow, oh), paint)
    }
    cv.drawBitmap(fg, 0f, 0f, null)
    return res
}

@Composable
fun ToolBox(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(if (selected) Color(0xFF3A0914) else NC.card2)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.red else NC.border, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(label, color = if (selected) NC.red else Color.White, fontSize = 11.sp)
    }
}

@Composable
fun RemoveBgScreen(onBack: () -> Unit, onPro: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cut = remember { CutState() }
    var full by remember { mutableStateOf<Bitmap?>(null) }
    var work by remember { mutableStateOf<Bitmap?>(null) }
    var version by remember { mutableStateOf(0) }
    var hasImage by remember { mutableStateOf(false) }
    var imgGen by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var tool by remember { mutableStateOf("auto") }
    var brushDp by remember { mutableStateOf(26f) }
    var featherPx by remember { mutableStateOf(2f) }
    var refineAmt by remember { mutableStateOf(0.3f) }
    var bgMode by remember { mutableStateOf(0) }
    var solid by remember { mutableStateOf(Color(0xFFE60A35)) }
    var customBmp by remember { mutableStateOf<Bitmap?>(null) }
    var blurBmp by remember { mutableStateOf<Bitmap?>(null) }
    var quality by remember { mutableStateOf(1) }
    var showOriginal by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    val undos = remember { ArrayList<ByteArray>() }
    val redos = remember { ArrayList<ByteArray>() }
    var histTick by remember { mutableStateOf(0) }

    fun pushUndo() {
        undos.add(cut.mask.copyOf())
        if (undos.size > 12) undos.removeAt(0)
        redos.clear()
        histTick++
    }

    fun runAuto() {
        val wb = work ?: return
        busy = true
        msg = "Detecting subject..."
        autoSegment(wb) { res ->
            busy = false
            if (res == null) {
                msg = "AI could not run on this photo. Use Manual / Erase brushes."
            } else {
                pushUndo()
                var fg = 0
                for (b in res) if ((b.toInt() and 0xFF) > 128) fg++
                if (fg < res.size / 50) {
                    msg = "No person found. Use the Erase brush to cut the background away."
                } else {
                    System.arraycopy(res, 0, cut.mask, 0, res.size)
                    System.arraycopy(res, 0, cut.auto, 0, res.size)
                    cut.rebuild(0, 0, cut.w - 1, cut.h - 1)
                    msg = "Done. Fix edges with Manual, Erase and Restore."
                }
                version++
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                msg = "Loading photo..."
                val pair = withContext(Dispatchers.Default) {
                    try {
                        val f = loadBitmap(ctx, uri, 2048)
                        if (f == null) {
                            null
                        } else {
                            val s = minOf(1f, 1024f / maxOf(f.width, f.height).toFloat())
                            val wk = Bitmap.createScaledBitmap(f, maxOf(2, (f.width * s).toInt()), maxOf(2, (f.height * s).toInt()), true)
                            Pair(f, wk)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                if (pair == null) {
                    msg = "Could not open that photo"
                } else {
                    full = pair.first
                    work = pair.second
                    cut.init(pair.second)
                    imgGen++
                    blurBmp = Bitmap.createScaledBitmap(pair.second, maxOf(1, pair.second.width / 24), maxOf(1, pair.second.height / 24), true)
                    undos.clear()
                    redos.clear()
                    histTick++
                    hasImage = true
                    version++
                    runAuto()
                }
            }
        }
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.Default) {
                    try { loadBitmap(ctx, uri, 1600) } catch (e: Exception) { null }
                }
                if (b != null) {
                    customBmp = b
                    bgMode = 3
                    version++
                }
            }
        }
    }

    fun export(save: Boolean) {
        val f = full
        if (f == null || !hasImage) {
            msg = "Select a photo first"
            return
        }
        scope.launch {
            busy = true
            val res = withContext(Dispatchers.Default) {
                try {
                    renderCutout(f, cut.mask, cut.w, cut.h, if (quality == 0) 1024 else 2048, bgMode, solid.toArgb(), customBmp)
                } catch (e: Exception) {
                    null
                }
            }
            busy = false
            if (res == null) {
                msg = "Export failed (not enough memory?)"
            } else if (save) {
                msg = saveBitmapToGallery(ctx, res, "NEO_cutout_" + System.currentTimeMillis(), bgMode == 0)
            } else {
                shareBitmap(ctx, res, "NEO_cutout", bgMode == 0)
            }
        }
    }

    val workImg = remember(work) { work?.asImageBitmap() }
    val blurImg = remember(blurBmp) { blurBmp?.asImageBitmap() }
    val customImg = remember(customBmp) { customBmp?.asImageBitmap() }
    val outImg = remember(imgGen) { cut.out?.asImageBitmap() }
    val sliderColors = SliderDefaults.colors(thumbColor = NC.red, activeTrackColor = NC.red, inactiveTrackColor = Color(0xFF2A2C3A))
    val brushTool = tool == "manual" || tool == "erase" || tool == "restore"

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowBack, "Back") { onBack() }
            NeoLogo(small = true)
            Spacer(Modifier.weight(1f))
            ProChip { showPro = true }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A0A12))
                        .border(1.dp, NC.red, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.ContentCut, "Cut", tint = Color.White) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row {
                        Text("Remove ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Background", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Text("Cut out your subject with AI precision", color = NC.sub, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(66.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0..1) {
                        val sel = (i == 0) == showOriginal
                        val shp = RoundedCornerShape(10.dp)
                        Column(
                            Modifier.clip(shp).background(NC.card2).border(if (sel) 1.5.dp else 1.dp, if (sel) NC.red else NC.border, shp)
                                .clickable { showOriginal = (i == 0) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(NC.redDeep, NC.bg))))
                            Text(if (i == 0) "Original" else "Removed", color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(330.dp).clip(RoundedCornerShape(14.dp)).background(NC.card)
                        .border(1.dp, NC.border, RoundedCornerShape(14.dp))
                ) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(tool, brushDp, hasImage) {
                            fun paintAt(pos: Offset) {
                                if (!hasImage || cut.w == 0) return
                                val s = minOf(size.width.toFloat() / cut.w, size.height.toFloat() / cut.h)
                                val ox = (size.width - cut.w * s) / 2f
                                val oy = (size.height - cut.h * s) / 2f
                                val bx = (pos.x - ox) / s
                                val by = (pos.y - oy) / s
                                val r = brushDp.dp.toPx() / s / 2f
                                val mode = if (tool == "manual") 0 else if (tool == "erase") 1 else 2
                                cut.paint(bx, by, r, mode)
                                version++
                            }
                            detectDragGestures(onDragStart = { pos ->
                                if (brushTool) {
                                    pushUndo()
                                    paintAt(pos)
                                }
                            }) { change, _ ->
                                change.consume()
                                if (brushTool) paintAt(change.position)
                            }
                        }
                    ) {
                        @Suppress("UNUSED_VARIABLE")
                        val v = version
                        if (hasImage && cut.w > 0) {
                            val s = minOf(size.width / cut.w, size.height / cut.h)
                            val dw = cut.w * s
                            val dh = cut.h * s
                            val ox = (size.width - dw) / 2f
                            val oy = (size.height - dh) / 2f
                            if (showOriginal && workImg != null) {
                                drawImage(workImg, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
                            } else {
                                drawCutout(ox, oy, dw, dh, bgMode, solid, blurImg, customImg, outImg, 14.dp.toPx())
                            }
                        }
                    }
                    if (!hasImage) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Select a photo to start", color = NC.sub, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { picker.launch(arrayOf("image/*")) }, colors = ButtonDefaults.buttonColors(containerColor = NC.red)) {
                                Text("Select Photo", color = Color.White)
                            }
                        }
                    }
                    if (busy) {
                        Text(msg, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(10.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasImage) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("New photo", fontSize = 12.sp, color = Color.White) }
                }
                Spacer(Modifier.weight(1f))
                val cu = undos.isNotEmpty() && histTick >= 0
                val cr = redos.isNotEmpty() && histTick >= 0
                IconBtn(Icons.Outlined.Undo, "Undo", if (cu) Color.White else NC.dim, 36.dp) {
                    if (undos.isNotEmpty()) {
                        redos.add(cut.mask.copyOf())
                        val m = undos.removeAt(undos.size - 1)
                        System.arraycopy(m, 0, cut.mask, 0, m.size)
                        cut.rebuild(0, 0, cut.w - 1, cut.h - 1)
                        histTick++
                        version++
                    }
                }
                IconBtn(Icons.Outlined.Redo, "Redo", if (cr) Color.White else NC.dim, 36.dp) {
                    if (redos.isNotEmpty()) {
                        undos.add(cut.mask.copyOf())
                        val m = redos.removeAt(redos.size - 1)
                        System.arraycopy(m, 0, cut.mask, 0, m.size)
                        cut.rebuild(0, 0, cut.w - 1, cut.h - 1)
                        histTick++
                        version++
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolBox("Auto", tool == "auto", Modifier.width(66.dp), {
                    tool = "auto"
                    if (hasImage && !busy) runAuto()
                }) { Icon(Icons.Outlined.AutoFixHigh, "Auto", tint = if (tool == "auto") NC.red else Color.White) }
                ToolBox("Manual", tool == "manual", Modifier.width(66.dp), { tool = "manual" }) {
                    Icon(Icons.Outlined.Brush, "Manual", tint = if (tool == "manual") NC.red else Color.White)
                }
                ToolBox("Erase", tool == "erase", Modifier.width(66.dp), { tool = "erase" }) {
                    Text("⌫", color = if (tool == "erase") NC.red else Color.White, fontSize = 20.sp)
                }
                ToolBox("Restore", tool == "restore", Modifier.width(66.dp), { tool = "restore" }) {
                    Text("↺", color = if (tool == "restore") NC.red else Color.White, fontSize = 20.sp)
                }
                ToolBox("Feather", tool == "feather", Modifier.width(66.dp), { tool = "feather" }) {
                    Text("◌", color = if (tool == "feather") NC.red else Color.White, fontSize = 20.sp)
                }
                ToolBox("Refine", tool == "refine", Modifier.width(66.dp), { tool = "refine" }) {
                    Text("◎", color = if (tool == "refine") NC.red else Color.White, fontSize = 20.sp)
                }
            }
            if (brushTool) {
                Text("Brush size  " + brushDp.toInt(), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = brushDp, onValueChange = { brushDp = it }, valueRange = 8f..90f, colors = sliderColors)
                Text(
                    if (tool == "manual") "Paint over the subject to keep it." else if (tool == "erase") "Paint over the background to remove it." else "Paint to bring back the AI result.",
                    color = NC.sub, fontSize = 11.sp
                )
            }
            if (tool == "feather") {
                Text("Edge softness  " + featherPx.toInt() + " px", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = featherPx, onValueChange = { featherPx = it }, valueRange = 1f..8f, colors = sliderColors)
                OutlinedButton(onClick = {
                    if (hasImage) {
                        pushUndo()
                        cut.feather(featherPx.toInt())
                        version++
                    }
                }) { Text("Apply feather", color = Color.White) }
            }
            if (tool == "refine") {
                Text("Edge sharpness  " + (refineAmt * 100).toInt() + "%", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Slider(value = refineAmt, onValueChange = { refineAmt = it }, valueRange = 0f..1f, colors = sliderColors)
                OutlinedButton(onClick = {
                    if (hasImage) {
                        pushUndo()
                        cut.refine(refineAmt)
                        version++
                    }
                }) { Text("Apply refine", color = Color.White) }
            }

            Spacer(Modifier.height(10.dp))
            GradeCard("Background") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeoChip("Transparent", bgMode == 0) { bgMode = 0 }
                    NeoChip("Solid Color", bgMode == 1) { bgMode = 1 }
                    NeoChip("Blur Background", bgMode == 2) { bgMode = 2 }
                    NeoChip("Custom Image", bgMode == 3) { bgPicker.launch(arrayOf("image/*")) }
                }
                if (bgMode == 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in listOf(Color(0xFFE60A35), Color.White, Color.Black, Color(0xFF1F6BFF), Color(0xFF2BE07A), Color(0xFFFFC21A))) {
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(c)
                                    .border(if (solid == c) 3.dp else 1.dp, if (solid == c) NC.red else NC.border, RoundedCornerShape(15.dp))
                                    .clickable { solid = c }
                            )
                        }
                    }
                }
            }
            GradeCard("Output Quality") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeoChip("Normal", quality == 0) { quality = 0 }
                    NeoChip("High", quality == 1) { quality = 1 }
                    NeoChip("Ultra HD  👑", false) { showPro = true }
                }
            }
            GradeCard("AI Remove Background", trailing = {
                Text(if (busy) "Processing..." else if (hasImage) "Ready" else "Idle", color = NC.red, fontSize = 12.sp)
            }) {
                Text("Automatically detects people and removes the background. Works best on photos with a person; use the brushes for anything else.", color = NC.sub, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = listOf("Transparent", "Solid", "Blur", "Custom")
                    for (i in 0..3) {
                        val shp = RoundedCornerShape(10.dp)
                        Column(
                            Modifier.weight(1f).clip(shp).border(if (bgMode == i) 1.5.dp else 1.dp, if (bgMode == i) NC.red else NC.border, shp)
                                .clickable { if (i == 3) bgPicker.launch(arrayOf("image/*")) else bgMode = i },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                                @Suppress("UNUSED_VARIABLE")
                                val v = version
                                if (hasImage && cut.w > 0) {
                                    val s = minOf(size.width / cut.w, size.height / cut.h)
                                    val dw = cut.w * s
                                    val dh = cut.h * s
                                    drawCutout((size.width - dw) / 2f, (size.height - dh) / 2f, dw, dh, i, solid, blurImg, customImg, outImg, 6.dp.toPx())
                                } else {
                                    drawRect(Color(0xFF14151C))
                                }
                            }
                            Text(names[i], color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            if (msg.isNotEmpty() && !busy) {
                Text(msg, color = NC.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { export(true) }, modifier = Modifier.weight(1f).height(50.dp), enabled = hasImage && !busy) {
                Text("Save to Gallery", color = Color.White)
            }
            Button(
                onClick = { export(false) }, modifier = Modifier.weight(1f).height(50.dp), enabled = hasImage && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = NC.red)
            ) { Text("Export  →", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
    if (showPro) ProDialog { showPro = false }
}
