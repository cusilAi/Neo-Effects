@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AspectOpt(val label: String, val w: Int, val h: Int, val sub: String)
class ResOpt(val label: String, val sub: String, val short: Int)

val ASPECTS = listOf(
    AspectOpt("9:16", 9, 16, "1080 × 1920"),
    AspectOpt("16:9", 16, 9, "1920 × 1080"),
    AspectOpt("1:1", 1, 1, "1080 × 1080"),
    AspectOpt("4:5", 4, 5, "1080 × 1350"),
    AspectOpt("21:9", 21, 9, "2560 × 1080"),
    AspectOpt("Custom", 0, 0, "Tap to set")
)

val RESOLUTIONS = listOf(
    ResOpt("480p", "SD", 480),
    ResOpt("720p", "HD", 720),
    ResOpt("1080p", "Full HD", 1080),
    ResOpt("2K", "QHD", 1440),
    ResOpt("4K", "UHD", 2160)
)

val FPS_OPTIONS = listOf(24, 30, 60, 120)
val BG_NAMES = listOf("Black", "White", "Gradient", "Blur", "Custom")

@Composable
fun SettingCard(title: String, sub: String, icon: ImageVector, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape).background(NC.card).border(1.dp, NC.border, shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, NC.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, title, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(sub, color = NC.sub, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun NewProjectScreen(defaultName: String, onBack: () -> Unit, onPro: () -> Unit, onCreate: (Project) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(defaultName) }
    var aspect by remember { mutableStateOf(0) }
    var customW by remember { mutableStateOf(1080) }
    var customH by remember { mutableStateOf(1080) }
    var fps by remember { mutableStateOf(30) }
    var res by remember { mutableStateOf(2) }
    var bg by remember { mutableStateOf(0) }
    var bgUri by remember { mutableStateOf("") }
    var adv by remember { mutableStateOf(false) }
    var autosave by remember { mutableStateOf(true) }
    var previewQ by remember { mutableStateOf(1) }
    var showCustom by remember { mutableStateOf(false) }
    var cwText by remember { mutableStateOf("1080") }
    var chText by remember { mutableStateOf("1080") }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            bgUri = uri.toString()
            bg = 4
        }
    }

    fun create() {
        val a = ASPECTS[aspect]
        val short = RESOLUTIONS[res].short
        var w: Int
        var h: Int
        if (a.w == 0) {
            w = customW
            h = customH
        } else if (a.w >= a.h) {
            h = short
            w = Math.round(short * a.w.toFloat() / a.h.toFloat())
        } else {
            w = short
            h = Math.round(short * a.h.toFloat() / a.w.toFloat())
        }
        w -= w % 2
        h -= h % 2
        val p = Project(System.currentTimeMillis().toString(), if (name.isBlank()) defaultName else name.trim())
        p.width = maxOf(2, w)
        p.height = maxOf(2, h)
        p.fps = fps
        p.resLabel = RESOLUTIONS[res].label
        p.aspectLabel = if (a.w == 0) customW.toString() + ":" + customH else a.label
        p.bgMode = bg
        p.bgUri = bgUri
        p.autosave = autosave
        p.previewQ = previewQ
        onCreate(p)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.Outlined.ArrowBack, "Back") { onBack() }
            NeoLogo(small = true)
            Spacer(Modifier.weight(1f))
            ProChip(onPro)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2A0710), NC.bg)))
                    .border(1.dp, NC.border, RoundedCornerShape(16.dp)).padding(16.dp)
            ) {
                Column {
                    Row {
                        Text("New ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Text("Project", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    }
                    Text("Let's bring your vision to life", color = NC.sub, fontSize = 13.sp)
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Project Name") },
                leadingIcon = { Icon(Icons.Outlined.Edit, "Name", tint = NC.sub) },
                trailingIcon = { IconBtn(Icons.Outlined.Close, "Clear", NC.sub, 36.dp) { name = "" } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = fieldColors()
            )

            SettingCard("Aspect Ratio", "Choose the size for your project", Icons.Outlined.Movie) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in ASPECTS.indices) {
                        val a = ASPECTS[i]
                        val sel = aspect == i
                        OptionBox(sel, Modifier.width(88.dp).height(92.dp), {
                            aspect = i
                            if (a.w == 0) showCustom = true
                        }) {
                            val bw = if (a.w == 0) 24.dp else if (a.w >= a.h) 26.dp else (26f * a.w / a.h).dp
                            val bh = if (a.w == 0) 24.dp else if (a.w >= a.h) (26f * a.h / a.w).dp else 26.dp
                            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(bw, bh).border(2.dp, if (sel) NC.red else Color.White, RoundedCornerShape(3.dp)))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(a.label, color = if (sel) NC.red else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (a.w == 0 && aspect == i) customW.toString() + "×" + customH else a.sub,
                                color = NC.sub, fontSize = 9.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            SettingCard("Frame Rate", "Higher frame rates = smoother motion", Icons.Outlined.Movie) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in FPS_OPTIONS) {
                        OptionBox(fps == f, Modifier.weight(1f).height(44.dp), { fps = f }) {
                            Text(f.toString() + " FPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            SettingCard("Resolution", "Choose the quality of your video", Icons.Outlined.Image) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in RESOLUTIONS.indices) {
                        val r = RESOLUTIONS[i]
                        OptionBox(res == i, Modifier.width(76.dp).height(54.dp), { res = i }) {
                            Text(r.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(r.sub, color = NC.sub, fontSize = 10.sp)
                        }
                    }
                }
            }

            SettingCard("Background", "Set a background for your project", Icons.Outlined.Image) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in BG_NAMES.indices) {
                        OptionBox(bg == i, Modifier.width(84.dp).height(74.dp), {
                            if (i == 4) {
                                bgPicker.launch(arrayOf("image/*"))
                            } else {
                                bg = i
                            }
                        }) {
                            val swatch: Brush = when (i) {
                                0 -> Brush.linearGradient(listOf(Color.Black, Color.Black))
                                1 -> Brush.linearGradient(listOf(Color.White, Color.White))
                                2 -> Brush.linearGradient(listOf(Color(0xFF1A1A8C), Color(0xFFB91FD8), Color(0xFFF23373)))
                                3 -> Brush.linearGradient(listOf(Color(0xFF3A3C4A), Color(0xFF14151C)))
                                else -> Brush.linearGradient(listOf(NC.card2, NC.card2))
                            }
                            Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(swatch), contentAlignment = Alignment.Center) {
                                if (i == 4) Icon(Icons.Outlined.Image, "Custom", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(BG_NAMES[i], color = if (bg == i) NC.red else Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp)).background(NC.card)
                    .border(1.dp, NC.border, RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { adv = !adv }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Advanced Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Optional settings for more control", color = NC.sub, fontSize = 11.sp)
                    }
                    Icon(if (adv) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, "Toggle", tint = Color.White)
                }
                if (adv) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-save every 30 seconds", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(
                                checked = autosave, onCheckedChange = { autosave = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = NC.red)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Preview quality", color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val labels = listOf("Fast", "Balanced", "Sharp")
                            for (i in labels.indices) {
                                OptionBox(previewQ == i, Modifier.weight(1f).height(38.dp), { previewQ = i }) {
                                    Text(labels[i], color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(
            Modifier.fillMaxWidth().padding(12.dp).height(54.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(NC.red, Color(0xFFB00828))))
                .clickable { create() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Movie, "Create", tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Create Project", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }

    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            containerColor = NC.card,
            title = { Text("Custom size", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = cwText, onValueChange = { cwText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Width (px)") }, singleLine = true, colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chText, onValueChange = { chText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Height (px)") }, singleLine = true, colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    customW = (cwText.toIntOrNull() ?: 1080).coerceIn(64, 7680)
                    customH = (chText.toIntOrNull() ?: 1080).coerceIn(64, 7680)
                    showCustom = false
                }) { Text("Set", color = NC.red) }
            }
        )
    }
}
