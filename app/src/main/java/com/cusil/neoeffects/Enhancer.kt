@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun avg3(a: Int, b: Int, c: Int): Int {
    val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF) + ((c shr 16) and 0xFF)) / 3
    val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF) + ((c shr 8) and 0xFF)) / 3
    val bl = ((a and 0xFF) + (b and 0xFF) + (c and 0xFF)) / 3
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

private fun blur3(px: IntArray, w: Int, h: Int): IntArray {
    val tmp = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            tmp[row + x] = avg3(px[row + maxOf(0, x - 1)], px[row + x], px[row + minOf(w - 1, x + 1)])
        }
    }
    val out = IntArray(w * h)
    for (y in 0 until h) {
        val y0 = maxOf(0, y - 1) * w
        val y1 = y * w
        val y2 = minOf(h - 1, y + 1) * w
        for (x in 0 until w) {
            out[y1 + x] = avg3(tmp[y0 + x], tmp[y1 + x], tmp[y2 + x])
        }
    }
    return out
}

// Classic (non-AI) image enhancement: upscale + denoise + sharpen + color + HDR-style tone fix
fun enhanceBitmap(
    src: Bitmap, targetLong: Int, mode: Int, s: Float,
    sharp: Boolean, denoise: Boolean, color: Boolean, hdr: Boolean
): Bitmap {
    val longSide = maxOf(src.width, src.height)
    val scale = if (targetLong > longSide) minOf(targetLong.toFloat() / longSide.toFloat(), 4f) else 1f
    val w = maxOf(2, (src.width * scale).toInt())
    val h = maxOf(2, (src.height * scale).toInt())
    val base = if (scale > 1.01f) Bitmap.createScaledBitmap(src, w, h, true) else src.copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(w * h)
    base.getPixels(px, 0, w, 0, 0, w, h)
    val bl = blur3(px, w, h)
    val sharpAmt = if (mode == 1) 0.5f + 0.8f * s else 0.7f + 1.3f * s
    val satBase = when (mode) {
        1 -> 1.18f
        2 -> 1.06f
        else -> 1.12f
    }
    val sat = 1f + (satBase - 1f) * (0.4f + s)
    val con = 1f + 0.06f * s
    for (i in px.indices) {
        val c = px[i]
        val b = bl[i]
        var r = ((c shr 16) and 0xFF).toFloat()
        var g = ((c shr 8) and 0xFF).toFloat()
        var bb = (c and 0xFF).toFloat()
        val br = ((b shr 16) and 0xFF).toFloat()
        val bg = ((b shr 8) and 0xFF).toFloat()
        val bbl = (b and 0xFF).toFloat()
        if (denoise) {
            val edge = (Math.abs(r - br) + Math.abs(g - bg) + Math.abs(bb - bbl)) / 3f
            val k = (1f - (edge / 24f).coerceIn(0f, 1f)) * 0.6f * s
            r += (br - r) * k
            g += (bg - g) * k
            bb += (bbl - bb) * k
        }
        if (sharp) {
            r += sharpAmt * (r - br)
            g += sharpAmt * (g - bg)
            bb += sharpAmt * (bb - bbl)
        }
        if (hdr) {
            val l = (0.299f * r + 0.587f * g + 0.114f * bb) / 255f
            val d = 255f * (0.18f * s * (1f - l) * (1f - l) - 0.08f * s * l * l)
            r += d
            g += d
            bb += d
        }
        if (color) {
            val l = 0.299f * r + 0.587f * g + 0.114f * bb
            r = l + (r - l) * sat
            g = l + (g - l) * sat
            bb = l + (bb - l) * sat
            r = (r - 128f) * con + 128f
            g = (g - 128f) * con + 128f
            bb = (bb - 128f) * con + 128f
        }
        val ri = r.toInt().coerceIn(0, 255)
        val gi = g.toInt().coerceIn(0, 255)
        val bi = bb.toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
    val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    res.setPixels(px, 0, w, 0, 0, w, h)
    return res
}

fun videoFirstFrame(ctx: android.content.Context, uri: Uri): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        val f = r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (f == null) {
            null
        } else {
            val s = minOf(1f, 1280f / maxOf(f.width, f.height).toFloat())
            Bitmap.createScaledBitmap(f, maxOf(2, (f.width * s).toInt()), maxOf(2, (f.height * s).toInt()), true)
        }
    } catch (e: Exception) {
        null
    } finally {
        try { r.release() } catch (e: Exception) { }
    }
}

@Composable
fun EnhancerScreen(onBack: () -> Unit, onPro: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(1) }
    var mode by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(0) }
    var strength by remember { mutableStateOf(0.75f) }
    var tSharp by remember { mutableStateOf(true) }
    var tDenoise by remember { mutableStateOf(true) }
    var tColor by remember { mutableStateOf(true) }
    var tHdr by remember { mutableStateOf(true) }
    var tInterp by remember { mutableStateOf(false) }
    var before by remember { mutableStateOf<Bitmap?>(null) }
    var after by remember { mutableStateOf<Bitmap?>(null) }
    var split by remember { mutableStateOf(0.5f) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var showPro by remember { mutableStateOf(false) }

    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.Default) {
                    try { loadBitmap(ctx, uri, 2048) } catch (e: Throwable) { null }
                }
                if (b != null) {
                    before = b
                    after = null
                    msg = ""
                } else {
                    msg = "Could not open that photo"
                }
            }
        }
    }
    val vidPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.Default) { videoFirstFrame(ctx, uri) }
                before = b
                after = null
                msg = if (b == null) "Could not read that video" else "Video loaded."
            }
        }
    }

    val target = if (quality == 0) 2048 else 3840
    val bf = before
    val outW: Int
    val outH: Int
    if (bf != null) {
        val ls = maxOf(bf.width, bf.height)
        val sc = if (target > ls) minOf(target.toFloat() / ls, 4f) else 1f
        outW = (bf.width * sc).toInt()
        outH = (bf.height * sc).toInt()
    } else {
        outW = 0
        outH = 0
    }
    val sliderColors = SliderDefaults.colors(thumbColor = NC.red, activeTrackColor = NC.red, inactiveTrackColor = Color(0xFF2A2C3A))
    val beforeImg = remember(before) { before?.asImageBitmap() }
    val afterImg = remember(after) { after?.asImageBitmap() }

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
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A0A12)).border(1.dp, NC.red, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("✦", color = Color.White, fontSize = 22.sp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row {
                        Text("Quality ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Enhancer", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    Text("Upscale and clean up your photos", color = NC.sub, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(
                Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)).background(NC.card)
                    .border(1.5.dp, NC.red, RoundedCornerShape(16.dp))
            ) {
                if (beforeImg == null) {
                    Text("Select a photo or video to preview", color = NC.sub, modifier = Modifier.align(Alignment.Center))
                } else {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                split = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                            }
                        }
                    ) {
                        val ia = beforeImg.width.toFloat() / beforeImg.height.toFloat()
                        val da = size.width / size.height
                        var dw = size.width
                        var dh = size.height
                        if (ia > da) dh = size.width / ia else dw = size.height * ia
                        val ox = (size.width - dw) / 2f
                        val oy = (size.height - dh) / 2f
                        val off = IntOffset(ox.toInt(), oy.toInt())
                        val sz = IntSize(dw.toInt(), dh.toInt())
                        val sx = ox + dw * split
                        clipRect(left = ox, top = oy, right = sx, bottom = oy + dh) {
                            drawImage(beforeImg, dstOffset = off, dstSize = sz)
                        }
                        clipRect(left = sx, top = oy, right = ox + dw, bottom = oy + dh) {
                            drawImage(afterImg ?: beforeImg, dstOffset = off, dstSize = sz)
                        }
                        drawLine(NC.red, Offset(sx, oy), Offset(sx, oy + dh), strokeWidth = 3.dp.toPx())
                        drawCircle(Color(0xFF12141D), radius = 16.dp.toPx(), center = Offset(sx, oy + dh / 2f))
                        drawCircle(NC.red, radius = 16.dp.toPx(), center = Offset(sx, oy + dh / 2f), style = Stroke(width = 2.dp.toPx()))
                    }
                    Text(
                        "Before", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        if (after != null) "After" else "After (press Enhance)", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (busy) {
                    Text("Enhancing...", color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(12.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NC.card2)) {
                for (i in 0..1) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (tab == i) Color(0xFF3A0914) else Color.Transparent)
                            .border(if (tab == i) 1.dp else 0.dp, if (tab == i) NC.red else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable {
                                tab = i
                                before = null
                                after = null
                                msg = ""
                            }.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (i == 0) "Video Enhancement" else "Image Enhancement", color = if (tab == i) NC.red else NC.sub, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(8.dp))
            GradeCard("Enhancement Mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = listOf("Auto", "Anime", "Realistic", "Custom")
                    for (i in names.indices) {
                        OptionBox(mode == i, Modifier.weight(1f).height(48.dp), { mode = i }) {
                            Text(names[i], color = if (mode == i) NC.red else Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
            GradeCard("Quality Level") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionBox(quality == 0, Modifier.weight(1f).height(48.dp), { quality = 0 }) { Text("2K (HD)", color = Color.White, fontSize = 12.sp) }
                    OptionBox(quality == 1, Modifier.weight(1f).height(48.dp), { quality = 1 }) { Text("4K (Ultra HD)", color = Color.White, fontSize = 12.sp) }
                    OptionBox(false, Modifier.weight(1f).height(48.dp), { showPro = true }) { Text("8K  👑", color = NC.sub, fontSize = 12.sp) }
                }
            }
            GradeCard("Processing Strength", trailing = { Text((strength * 100).toInt().toString() + "%", color = NC.red, fontWeight = FontWeight.Bold) }) {
                Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.1f..1f, colors = sliderColors)
            }
            GradeCard("Enhancements") {
                @Composable
                fun ToggleRow(label: String, on: Boolean, enabled: Boolean, set: (Boolean) -> Unit) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = if (enabled) Color.White else NC.dim, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = on, onCheckedChange = set, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = NC.red))
                    }
                }
                ToggleRow("Sharpen", tSharp, true) { tSharp = it }
                ToggleRow("Denoise", tDenoise, true) { tDenoise = it }
                ToggleRow("Color Enhance", tColor, true) { tColor = it }
                ToggleRow("HDR Fix", tHdr, true) { tHdr = it }
                ToggleRow("Frame Interpolation (video, coming soon)", tInterp, false) { tInterp = it }
            }
            GradeCard("Expected Output") {
                Text(
                    if (tab == 1) {
                        if (bf == null) "Select a photo to see the output size." else "" + outW + " × " + outH + " px · usually a few seconds"
                    } else {
                        "Video enhancement needs on-device AI models. Coming soon."
                    },
                    color = NC.sub, fontSize = 12.sp
                )
            }
            if (msg.isNotEmpty()) Text(msg, color = NC.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (tab == 1) imgPicker.launch(arrayOf("image/*")) else vidPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text(if (tab == 1) "Select Photo" else "Select Video", color = Color.White) }
            if (after != null) {
                Button(
                    onClick = {
                        val a = after
                        if (a != null) msg = saveBitmapToGallery(ctx, a, "NEO_enhanced_" + System.currentTimeMillis(), false)
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NC.red)
                ) { Text("Save to Gallery", color = Color.White, fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = {
                        val src = before
                        if (tab == 0) {
                            msg = "Video enhancement is coming soon. Image Enhancement works now."
                        } else if (src == null) {
                            msg = "Select a photo first"
                        } else {
                            scope.launch {
                                busy = true
                                msg = ""
                                val res = withContext(Dispatchers.Default) {
                                    try {
                                        enhanceBitmap(src, target, mode, strength, tSharp, tDenoise, tColor, tHdr)
                                    } catch (e: Throwable) {
                                        null
                                    }
                                }
                                busy = false
                                if (res == null) msg = "Not enough memory. Try 2K." else {
                                    after = res
                                    split = 0.5f
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp), enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NC.red)
                ) { Text("Enhance Now  →", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (showPro) ProDialog { showPro = false }
}
