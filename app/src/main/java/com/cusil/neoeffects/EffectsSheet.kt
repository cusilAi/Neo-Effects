@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class EffCat(val key: String, val label: String, val glyph: String)

val EFF_CATS = listOf(
    EffCat("trending", "Trending", "🔥"),
    EffCat("basic", "Basic", "⚙️"),
    EffCat("blur", "Blur", "💧"),
    EffCat("distortion", "Distortion", "🌀"),
    EffCat("glitch", "Glitch", "⚡"),
    EffCat("color", "Color", "🎨"),
    EffCat("light", "Light", "💡"),
    EffCat("shake", "Shake", "📳"),
    EffCat("3d", "3D", "🧊")
)

val TRENDING = listOf(
    "camera_shake", "glow", "chromatic", "zoom_blur", "rgb_split", "slice_glitch",
    "vhs", "speed_lines", "light_leak", "shockwave", "strobe", "kaleidoscope"
)

fun effCatOf(e: NeoEffect): String = when (e.category) {
    "Blur" -> "blur"
    "Motion" -> "shake"
    "Glitch", "Retro" -> "glitch"
    "Distort" -> "distortion"
    "Color" -> "color"
    "Light" -> "light"
    else -> "basic"
}

fun catColors(key: String): Pair<Color, Color> = when (key) {
    "blur" -> Pair(Color(0xFF3B5FA8), Color(0xFF0B1226))
    "shake" -> Pair(Color(0xFFB0102A), Color(0xFF1E0308))
    "glitch" -> Pair(Color(0xFF8A2BE2), Color(0xFF0E0420))
    "distortion" -> Pair(Color(0xFF1F8FA8), Color(0xFF04141A))
    "color" -> Pair(Color(0xFFE0762B), Color(0xFF1C0C04))
    "light" -> Pair(Color(0xFFE0B02B), Color(0xFF1C1404))
    else -> Pair(Color(0xFF5A5D70), Color(0xFF10111A))
}

@Composable
fun EffectCard(e: NeoEffect, applied: Boolean, active: Boolean, onClick: () -> Unit) {
    val key = effCatOf(e)
    val cc = catColors(key)
    val glyph = EFF_CATS.firstOrNull { it.key == key }?.glyph ?: "✨"
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.clip(shape).background(NC.card2)
            .border(if (active) 2.dp else 1.dp, if (active) NC.red else NC.border, shape)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(62.dp).background(Brush.linearGradient(listOf(cc.first, cc.second)))) {
            Text(glyph, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
            if (applied) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = NC.red, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(e.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(e.category, color = NC.sub, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
fun EffectsSheet(
    clip: Clip?,
    library: List<NeoEffect>,
    project: Project,
    undo: UndoStack,
    activeId: String,
    onActive: (String) -> Unit,
    onClose: () -> Unit
) {
    var cat by remember { mutableStateOf("trending") }
    var query by remember { mutableStateOf("") }
    val all = library.filter { it.id != "color_grade" }
    val list = all.filter { e ->
        val inCat = if (query.isNotBlank()) true else if (cat == "trending") TRENDING.contains(e.id) else effCatOf(e) == cat
        val inQ = query.isBlank() || e.name.contains(query, ignoreCase = true)
        inCat && inQ
    }
    val activeInst = clip?.fx?.firstOrNull { it.effect.id == activeId }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Column(Modifier.fillMaxSize().clip(shape).background(NC.card).border(1.dp, NC.border, shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.Close, "Close", size = 38.dp) { onClose() }
            Text("Effects", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(NC.red),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(NC.card2)
                            .border(1.dp, NC.border, RoundedCornerShape(19.dp)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, "Search", tint = NC.sub, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search effects...", color = NC.dim, fontSize = 13.sp)
                            inner()
                        }
                    }
                }
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (c in EFF_CATS) {
                val sel = cat == c.key && query.isBlank()
                Column(
                    Modifier.width(66.dp).clickable { cat = c.key; query = "" }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(c.glyph, fontSize = 18.sp)
                    Text(c.label, color = if (sel) NC.red else NC.sub, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 3.dp).width(34.dp).height(2.dp).background(if (sel) NC.red else Color.Transparent))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (clip == null) {
                Text("Select a clip in the timeline first.", color = NC.sub, modifier = Modifier.align(Alignment.Center))
            } else if (list.isEmpty()) {
                Text(
                    if (cat == "3d" && query.isBlank()) "3D effects are coming soon." else "No effects found.",
                    color = NC.sub, modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list) { e ->
                        val applied = clip.fx.any { it.effect.id == e.id }
                        EffectCard(e, applied, e.id == activeId && applied) {
                            if (!applied) {
                                undo.push()
                                clip.fx.add(FxInst(e))
                            }
                            onActive(e.id)
                        }
                    }
                }
            }
        }
        if (clip != null && activeInst != null) {
            Row(
                Modifier.fillMaxWidth().background(NC.card2).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(84.dp)) {
                    Text(activeInst.effect.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(activeInst.effect.category, color = NC.sub, fontSize = 10.sp)
                    Text("Remove", color = NC.red, fontSize = 11.sp, modifier = Modifier.clickable {
                        undo.push()
                        clip.fx.remove(activeInst)
                        onActive("")
                    }.padding(top = 4.dp))
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    if (activeInst.effect.params.isEmpty()) {
                        Text("No settings", color = NC.sub, fontSize = 12.sp)
                    }
                    for (p in activeInst.effect.params) {
                        ParamControl(activeInst, p, clip, project, undo, Modifier.width(150.dp).padding(end = 12.dp))
                    }
                }
            }
        }
    }
}
