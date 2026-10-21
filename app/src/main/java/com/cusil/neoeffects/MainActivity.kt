@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CFF))) {
                NeoApp()
            }
        }
    }
}

data class MenuItem(val key: String, val icon: String, val title: String, val sub: String)

val menu = listOf(
    MenuItem("editor", "➕", "New project", "Start editing"),
    MenuItem("quality", "✨", "Quality boost", "FPS 0-120 · 320p-4K"),
    MenuItem("removebg", "✂️", "Remove BG", "HQ model + brush"),
    MenuItem("import_fx", "🧩", "Import effects", ".neofx files"),
    MenuItem("import_project", "📂", "Import project", "Lottie and more"),
    MenuItem("templates", "🎞️", "Templates", "Built-in and AE"),
    MenuItem("color", "🎨", "Color grading", "Pro grading")
)

val soonInfo = mapOf(
    "quality" to Pair("Quality boost", "Boost FPS (0-120), enhance quality Topaz-style, pick output 320p to 4K."),
    "removebg" to Pair("Remove BG", "High-quality background removal model, plus a brush to mark what to keep."),
    "import_project" to Pair("Import project", "Import projects and animations (Lottie first)."),
    "templates" to Pair("Templates", "Built-in templates and After Effects style templates."),
    "color" to Pair("Color grading", "After Effects style color grading tools.")
)

@Composable
fun NeoApp() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    val effects = remember { mutableStateListOf<NeoEffect>().apply { addAll(EffectStore.load(ctx)) } }

    BackHandler(enabled = screen != "home") { screen = "home" }

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = Color(0xFF0B0B12)
    ) {
        when (screen) {
            "home" -> HomeScreen { screen = it }
            "editor" -> EditorScreen(effects) { screen = "home" }
            "import_fx" -> ImportScreen(effects) { screen = "home" }
            else -> SoonScreen(screen) { screen = "home" }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("←  Home") }
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("NeoEffects", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Edit. Effect. Create.", color = Color(0xFF9A9AB0))
            }
        }
        item(span = { GridItemSpan(2) }) {
            MenuCard(menu[0], true) { onOpen(menu[0].key) }
        }
        items(menu.drop(1)) { m ->
            MenuCard(m, false) { onOpen(m.key) }
        }
    }
}

@Composable
fun MenuCard(m: MenuItem, big: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (big) Color(0xFF5B2EE6) else Color(0xFF1A1A2A))
    ) {
        Column(Modifier.padding(if (big) 24.dp else 16.dp)) {
            Text(m.icon, fontSize = if (big) 36.sp else 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(m.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 16.sp)
            Text(m.sub, color = Color(0xFFB8B8D0), fontSize = 12.sp)
        }
    }
}

@Composable
fun SoonScreen(key: String, onBack: () -> Unit) {
    val info = soonInfo[key] ?: Pair("Coming soon", "")
    Column(Modifier.fillMaxSize()) {
        TopBar(info.first, onBack)
        Column(Modifier.padding(24.dp)) {
            Text(info.first, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(info.second, color = Color(0xFFB8B8D0))
            Spacer(Modifier.height(16.dp))
            Text("Coming in a later build.", color = Color(0xFF8B5CFF))
        }
    }
}

fun testCard(): Bitmap {
    val w = 900
    val h = 1400
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(0xFF6C3BFF.toInt(), 0xFF00D4FF.toInt(), 0xFFFF3B8D.toInt()),
        null, Shader.TileMode.CLAMP
    )
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null
    p.color = 0xFFFFFFFF.toInt()
    c.drawCircle(w * 0.3f, h * 0.35f, 170f, p)
    p.color = 0xFF000000.toInt()
    c.drawRect(w * 0.5f, h * 0.55f, w * 0.85f, h * 0.7f, p)
    p.color = 0xFFFFFFFF.toInt()
    p.textSize = 120f
    p.isFakeBoldText = true
    c.drawText("NEO FX", 90f, h * 0.88f, p)
    return bmp
}

@Composable
fun EditorScreen(effects: List<NeoEffect>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var bitmap by remember { mutableStateOf(testCard()) }
    var selected by remember { mutableStateOf<NeoEffect?>(effects.firstOrNull()) }
    var err by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val b = try {
                loadBitmap(ctx, uri)
            } catch (e: Exception) {
                null
            }
            if (b != null) bitmap = b
        }
    }

    val fx = selected
    val values = remember(fx) {
        mutableStateMapOf<String, Float>().apply {
            fx?.params?.forEach { put(it.id, it.def) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("New project", onBack) {
            Button(onClick = { picker.launch("image/*") }) { Text("Open photo") }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            GlPreview(bitmap, fx, values, { err = it }, Modifier.fillMaxSize())
        }

        if (err.isNotEmpty()) {
            Text(
                "Shader error: $err",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp).heightIn(max = 80.dp).verticalScroll(rememberScrollState())
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            item {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("None") })
            }
            items(effects) { e ->
                FilterChip(selected = selected?.id == e.id, onClick = { selected = e }, label = { Text(e.name) })
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 220.dp).verticalScroll(rememberScrollState())
        ) {
            if (fx == null) {
                Text("No effect selected", color = Color(0xFF9A9AB0))
            } else if (fx.params.isEmpty()) {
                Text("${fx.name}: no settings", color = Color(0xFF9A9AB0))
            } else {
                for (p in fx.params) {
                    val v = values[p.id] ?: p.def
                    Text("${p.name}: ${String.format("%.1f", v)}", color = Color.White)
                    Slider(
                        value = v,
                        onValueChange = { values[p.id] = it },
                        valueRange = p.min..maxOf(p.max, p.min + 0.01f)
                    )
                }
            }
        }
    }
}

@Composable
fun ImportScreen(effects: MutableList<NeoEffect>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }

    fun importText(text: String) {
        msg = try {
            val fx = EffectStore.saveImported(ctx, text)
            effects.removeAll { it.id == fx.id && !it.builtIn }
            effects.add(fx)
            "✅ Imported: ${fx.name}"
        } catch (e: Exception) {
            "❌ ${e.message}"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                importText(text)
            } catch (e: Exception) {
                msg = "❌ ${e.message}"
            }
        }
    }

    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TopBar("Import effects", onBack) }
        item {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import .neofx file")
            }
        }
        item {
            Button(
                onClick = {
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (t.isNullOrBlank()) msg = "❌ Clipboard is empty" else importText(t)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Paste effect from clipboard") }
        }
        item {
            OutlinedButton(
                onClick = {
                    cm.setPrimaryClip(ClipData.newPlainText("NeoFx prompt", AI_PROMPT))
                    msg = "Prompt copied. Paste it into any AI and describe your effect at the end."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copy AI prompt") }
        }
        if (msg.isNotEmpty()) {
            item { Text(msg, color = Color.White) }
        }
        item { Text("Installed effects (${effects.size})", color = Color(0xFF9A9AB0)) }
        items(effects) { e ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(e.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        e.category + if (e.builtIn) " · built-in" else " · imported",
                        color = Color(0xFFB8B8D0),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
