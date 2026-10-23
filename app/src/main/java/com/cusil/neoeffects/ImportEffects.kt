@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun chipOf(e: NeoEffect): String = when (e.category) {
    "Motion" -> "Shake"
    "Blur" -> "Blur"
    "Glitch" -> "Glitch"
    "Distort" -> "Distortion"
    "Color", "Grade" -> "Color"
    "Retro" -> "TV"
    "Stylize", "Detail", "Cinematic" -> "Stylize"
    else -> "Other"
}

@Composable
fun SourceCard(title: String, sub: String, glyph: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(NC.card).border(1.dp, if (enabled) NC.red else NC.border, shape)
            .clickable(onClick = onClick).padding(10.dp)
    ) {
        Text(glyph, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = if (enabled) Color.White else NC.sub, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
        Text(sub, color = NC.sub, fontSize = 9.sp, maxLines = 2)
    }
}

@Composable
fun ImportEffectsScreen(
    effects: MutableList<NeoEffect>,
    onBack: () -> Unit,
    onLab: (NeoEffect?) -> Unit,
    onPro: () -> Unit
) {
    val ctx = LocalContext.current
    var chip by remember { mutableStateOf("All") }
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            msg = try {
                val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val fx = EffectStore.saveImported(ctx, t)
                effects.removeAll { it.id == fx.id && !it.builtIn }
                effects.add(fx)
                "✅ Imported: " + fx.name
            } catch (e: Exception) {
                "❌ " + (e.message ?: "could not import")
            }
        }
    }
    val chips = listOf("All", "Shake", "Blur", "Glitch", "Distortion", "Color", "TV", "Stylize", "Other")
    val shown = effects.filter { it.id != "color_grade" && (chip == "All" || chipOf(it) == chip) }
    val rows = shown.chunked(3)
    val mine = effects.filter { !it.builtIn }
    val packCats = listOf("Glitch", "Light", "Distort", "Color", "Blur", "Motion")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.ArrowBack, "Back") { onBack() }
                NeoLogo(small = true)
                Spacer(Modifier.weight(1f))
                ProChip(onPro)
            }
        }
        item {
            Row {
                Text("Import ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("Effects", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
            Text("Add custom effects and presets to your library.", color = NC.sub, fontSize = 12.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceCard("Import from File", ".neofx / .json / .txt", "📂", true, Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
                SourceCard("Import from Cloud", "Coming soon", "☁️", false, Modifier.weight(1f)) { msg = "Cloud import is coming soon" }
                SourceCard("Scan QR Code", "Coming soon", "🔳", false, Modifier.weight(1f)) { msg = "QR import is coming soon" }
            }
        }
        item {
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF47081A), Color(0xFF12050A))))
                    .border(1.5.dp, NC.red, shape).clickable { onLab(null) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🤖", fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI Effect Lab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Describe an effect to any AI, paste the answer, test it live and save it.", color = NC.sub, fontSize = 11.sp)
                }
                Text("›", color = Color.White, fontSize = 24.sp)
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = Color.White, fontSize = 12.sp) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in chips) NeoChip(c, chip == c) { chip = c }
            }
        }
        item { Text("🔥 Popular Effects", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (rows.isEmpty()) {
            item { Text("No effects in this category yet.", color = NC.sub, fontSize = 12.sp) }
        }
        items(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in r) {
                    Box(Modifier.weight(1f)) { EffectCard(e, false, false) { onLab(e) } }
                }
                for (i in r.size until 3) Spacer(Modifier.weight(1f))
            }
        }
        item { Text("📦 Effect Packs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pc in packCats) {
                    val n = effects.count { it.category == pc && it.id != "color_grade" }
                    val cc = catColors(effCatOf(NeoEffect("x", "x", pc, emptyList(), "", true)))
                    val shape = RoundedCornerShape(12.dp)
                    Column(Modifier.width(120.dp).clip(shape).background(NC.card).border(1.dp, NC.border, shape)) {
                        Box(Modifier.fillMaxWidth().height(54.dp).background(Brush.linearGradient(listOf(cc.first, cc.second))))
                        Column(Modifier.padding(8.dp)) {
                            Text(pc + " Pack", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("$n effects", color = NC.sub, fontSize = 10.sp)
                            Text("Installed", color = NC.red, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item { Text("🕘 Recently Imported", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (mine.isEmpty()) {
            item { Text("Effects you import or create with the AI Lab appear here.", color = NC.sub, fontSize = 12.sp) }
        }
        items(mine.toList()) { e ->
            var menu by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(NC.card).border(1.dp, NC.border, shape).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(NC.redDeep, NC.bg))), contentAlignment = Alignment.Center) { Text("✨") }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name + ".neofx", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(e.category + " · custom effect", color = NC.sub, fontSize = 10.sp)
                }
                Text("Use", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NC.red).clickable { onLab(e) }.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                Box {
                    IconBtn(Icons.Outlined.MoreVert, "Menu", NC.sub, 36.dp) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menu = false
                            EffectStore.delete(ctx, e.id)
                            effects.remove(e)
                        })
                    }
                }
            }
        }
        item {
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(NC.card).border(1.dp, NC.red, shape).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👑", fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Unlock More Effects", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Get access to premium packs", color = NC.sub, fontSize = 11.sp)
                }
                Text("Go Pro", color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NC.red).clickable { onPro() }.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}
