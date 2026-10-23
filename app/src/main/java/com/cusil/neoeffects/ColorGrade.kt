@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GradePreset(val name: String, val c1: Color, val c2: Color, val v: Map<String, Float>)

val GRADE_PRESETS = listOf(
    GradePreset("None", Color(0xFF3A3C4A), Color(0xFF14151C), emptyMap()),
    GradePreset(
        "Cinematic", Color(0xFF1F8FA8), Color(0xFFE0762B),
        mapOf("liftx" to 0.4f, "gainx" to -0.45f, "gainy" to -0.15f, "contrast" to 0.18f, "saturation" to -0.05f, "vignette" to 0.35f)
    ),
    GradePreset(
        "Anime", Color(0xFFB02BE0), Color(0xFF2B8FE0),
        mapOf("saturation" to 0.3f, "vibrance" to 0.3f, "contrast" to 0.12f, "exposure" to 0.1f, "sharpen" to 0.6f)
    ),
    GradePreset(
        "Moody", Color(0xFF1F3A5A), Color(0xFF0A0F18),
        mapOf("exposure" to -0.25f, "contrast" to 0.2f, "saturation" to -0.2f, "temperature" to -0.25f, "shadows" to -0.1f, "vignette" to 0.5f)
    ),
    GradePreset(
        "Vibrant", Color(0xFFE0762B), Color(0xFF2BE0B0),
        mapOf("saturation" to 0.35f, "vibrance" to 0.4f, "contrast" to 0.1f, "exposure" to 0.08f, "temperature" to 0.1f)
    ),
    GradePreset(
        "Dark Film", Color(0xFF5A1626), Color(0xFF0A0A0E),
        mapOf("exposure" to -0.3f, "contrast" to 0.25f, "blacks" to 0.15f, "grain" to 0.35f, "vignette" to 0.4f, "saturation" to -0.15f, "gainx" to -0.3f, "gainy" to -0.1f)
    )
)

@Composable
fun gradeSliderColors() = SliderDefaults.colors(
    thumbColor = NC.red, activeTrackColor = NC.red, inactiveTrackColor = Color(0xFF2A2C3A)
)

@Composable
fun GradeSlider(inst: FxInst, id: String, label: String, undo: UndoStack, modifier: Modifier = Modifier) {
    val p = inst.effect.params.firstOrNull { it.id == id } ?: return
    val v = inst.values[id] ?: p.def
    var drag by remember { mutableStateOf(false) }
    Column(modifier) {
        Row {
            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(String.format("%+.2f", v), color = NC.sub, fontSize = 11.sp)
        }
        Slider(
            value = v.coerceIn(p.min, p.max),
            onValueChange = {
                if (!drag) {
                    undo.push()
                    drag = true
                }
                inst.values[id] = it
            },
            onValueChangeFinished = { drag = false },
            valueRange = p.min..p.max,
            colors = gradeSliderColors()
        )
    }
}

@Composable
fun ColorWheel(label: String, inst: FxInst, xId: String, yId: String, mId: String, undo: UndoStack, modifier: Modifier = Modifier) {
    val x = inst.values[xId] ?: 0f
    val y = inst.values[yId] ?: 0f
    val ringColors = remember {
        (0..12).map { i -> Color.hsv(((0.5f - i / 12f + 1f) % 1f) * 360f, 1f, 1f) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Canvas(
            Modifier.size(100.dp).pointerInput(Unit) {
                fun upd(pos: Offset) {
                    val r = minOf(size.width, size.height) / 2f * 0.85f
                    var dx = (pos.x - size.width / 2f) / r
                    var dy = -(pos.y - size.height / 2f) / r
                    val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (len > 1f) {
                        dx /= len
                        dy /= len
                    }
                    inst.values[xId] = dx
                    inst.values[yId] = dy
                }
                detectDragGestures(onDragStart = { pos ->
                    undo.push()
                    upd(pos)
                }) { change, _ ->
                    change.consume()
                    upd(change.position)
                }
            }
        ) {
            val r = size.minDimension / 2f * 0.85f
            val c = center
            drawCircle(brush = Brush.sweepGradient(ringColors, c), radius = r, center = c, style = Stroke(width = r * 0.30f))
            drawCircle(Color(0xFF12141D), radius = r * 0.85f, center = c)
            drawCircle(Color(0x33FFFFFF), radius = 2.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(c.x + x * r, c.y - y * r), style = Stroke(width = 2.dp.toPx()))
        }
        GradeSlider(inst, mId, "Level", undo, Modifier.width(110.dp))
    }
}

@Composable
fun CurveEditor(ch: Int, inst: FxInst, undo: UndoStack, modifier: Modifier = Modifier) {
    val pre = listOf("cm", "cr", "cg", "cb")[ch]
    val ids = listOf(pre + "1", pre + "2", pre + "3")
    val ys = ids.map { inst.values[it] ?: 0.5f }
    val lineColor = when (ch) {
        1 -> Color(0xFFFF4D4D)
        2 -> Color(0xFF4DFF88)
        3 -> Color(0xFF4D8DFF)
        else -> NC.redGlow
    }
    Canvas(
        modifier.pointerInput(ch) {
            var idx = 0
            fun upd(pos: Offset) {
                inst.values[ids[idx]] = (1f - pos.y / size.height).coerceIn(0f, 1f)
            }
            detectDragGestures(onDragStart = { pos ->
                undo.push()
                val fx = pos.x / size.width
                idx = if (fx < 0.375f) 0 else if (fx < 0.625f) 1 else 2
                upd(pos)
            }) { change, _ ->
                change.consume()
                upd(change.position)
            }
        }
    ) {
        val w = size.width
        val h = size.height
        for (i in 1..3) {
            drawLine(Color(0xFF2A2C3A), Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), strokeWidth = 1f)
            drawLine(Color(0xFF2A2C3A), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeWidth = 1f)
        }
        val pts = listOf(
            Offset(0f, h), Offset(w * 0.25f, h * (1f - ys[0])), Offset(w * 0.5f, h * (1f - ys[1])),
            Offset(w * 0.75f, h * (1f - ys[2])), Offset(w, 0f)
        )
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        drawPath(path, lineColor, style = Stroke(width = 3f))
        for (i in 1..3) {
            drawCircle(Color.White, radius = 6.dp.toPx(), center = pts[i])
            drawCircle(lineColor, radius = 4.dp.toPx(), center = pts[i])
        }
    }
}

@Composable
fun GradeCard(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(shape).background(NC.card2).border(1.dp, NC.border, shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun ColorGradePanel(
    clip: Clip?,
    library: List<NeoEffect>,
    undo: UndoStack,
    bypass: Boolean,
    onBypass: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val e = library.firstOrNull { it.id == "color_grade" }
    val inst = clip?.fx?.firstOrNull { it.effect.id == "color_grade" }
    LaunchedEffect(clip?.id, e) {
        if (clip != null && e != null && clip.fx.none { it.effect.id == "color_grade" }) {
            clip.fx.add(FxInst(e))
        }
    }
    var curveCh by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(0) }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Column(Modifier.fillMaxSize().clip(shape).background(NC.card).border(1.dp, NC.border, shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.Close, "Close", size = 38.dp) { onClose() }
            Text("Color Grading", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (clip == null || inst == null) {
            Text("Select a clip in the timeline first.", color = NC.sub, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GradeCard("Presets") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in GRADE_PRESETS.indices) {
                        val pr = GRADE_PRESETS[i]
                        val shp = RoundedCornerShape(10.dp)
                        Column(
                            Modifier.width(72.dp).clip(shp).border(1.dp, if (preset == i) NC.red else NC.border, shp)
                                .clickable {
                                    undo.push()
                                    for (p in inst.effect.params) inst.values[p.id] = pr.v[p.id] ?: p.def
                                    inst.keys.clear()
                                    preset = i
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.fillMaxWidth().height(44.dp).background(Brush.linearGradient(listOf(pr.c1, pr.c2))))
                            Text(pr.name, color = if (preset == i) NC.red else Color.White, fontSize = 11.sp, modifier = Modifier.padding(4.dp), maxLines = 1)
                        }
                    }
                }
            }
            GradeCard("Color Wheels", trailing = {
                Text("Reset", color = NC.red, fontSize = 12.sp, modifier = Modifier.clickable {
                    undo.push()
                    for (id in listOf("liftx", "lifty", "liftm", "gammax", "gammay", "gammam", "gainx", "gainy", "gainm")) inst.values[id] = 0f
                }.padding(4.dp))
            }) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorWheel("Lift", inst, "liftx", "lifty", "liftm", undo)
                    ColorWheel("Gamma", inst, "gammax", "gammay", "gammam", undo)
                    ColorWheel("Gain", inst, "gainx", "gainy", "gainm", undo)
                }
            }
            GradeCard("Curves", trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val names = listOf("RGB", "R", "G", "B")
                    for (i in names.indices) NeoChip(names[i], curveCh == i) { curveCh = i }
                }
            }) {
                CurveEditor(curveCh, inst, undo, Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF0B0C12)))
                Text("Drag the 3 points up or down.", color = NC.sub, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            GradeCard("Adjust") {
                val rows = listOf(
                    "exposure" to "Exposure", "contrast" to "Contrast", "highlights" to "Highlights", "shadows" to "Shadows",
                    "whites" to "Whites", "blacks" to "Blacks", "saturation" to "Saturation", "vibrance" to "Vibrance",
                    "temperature" to "Temperature", "tint" to "Tint", "hue" to "Hue", "sharpen" to "Sharpen",
                    "grain" to "Grain", "vignette" to "Vignette"
                )
                for (r in rows) GradeSlider(inst, r.first, r.second, undo)
            }
            GradeCard("Auto Enhance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Boosts contrast, vibrance and shadows in one tap", color = NC.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = auto,
                        onCheckedChange = { on ->
                            undo.push()
                            val d = if (on) 1f else -1f
                            fun bump(id: String, amt: Float) {
                                val p = inst.effect.params.firstOrNull { it.id == id } ?: return
                                inst.values[id] = ((inst.values[id] ?: p.def) + amt * d).coerceIn(p.min, p.max)
                            }
                            bump("contrast", 0.08f)
                            bump("vibrance", 0.2f)
                            bump("shadows", 0.08f)
                            bump("saturation", 0.06f)
                            auto = on
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NC.red)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (bypass) Color(0xFF3A0914) else NC.card2)
                    .border(1.dp, NC.border, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            onBypass(true)
                            tryAwaitRelease()
                            onBypass(false)
                        })
                    }.padding(horizontal = 14.dp, vertical = 12.dp)
            ) { Text("Compare (hold)", color = Color.White, fontSize = 13.sp) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(NC.card2)
                    .border(1.dp, NC.border, RoundedCornerShape(12.dp))
                    .clickable {
                        undo.push()
                        for (p in inst.effect.params) inst.values[p.id] = p.def
                        inst.keys.clear()
                        preset = 0
                        auto = false
                    }.padding(horizontal = 14.dp, vertical = 12.dp)
            ) { Text("Reset All", color = Color.White, fontSize = 13.sp) }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(NC.red, Color(0xFFB00828))))
                    .clickable { onClose() }.padding(horizontal = 22.dp, vertical = 12.dp)
            ) { Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}
