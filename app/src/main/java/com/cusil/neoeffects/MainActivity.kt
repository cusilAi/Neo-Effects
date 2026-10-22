@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
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
import java.text.DateFormat
import java.util.Date

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
    MenuItem("editor", "➕", "New project", "Start a new edit"),
    MenuItem("projects", "📁", "My projects", "Continue editing"),
    MenuItem("quality", "✨", "Quality boost", "FPS 0-120 · 320p-4K"),
    MenuItem("removebg", "✂️", "Remove BG", "HQ model + brush"),
    MenuItem("import_fx", "🧩", "Import effects", "Effect Lab · AI effects"),
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
    var project by remember { mutableStateOf<Project?>(null) }
    val effects = remember { mutableStateListOf<NeoEffect>().apply { addAll(EffectStore.load(ctx)) } }

    BackHandler(enabled = screen != "home" && screen != "editor") { screen = "home" }

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = Color(0xFF0B0B12)
    ) {
        when (screen) {
            "home" -> HomeScreen { k ->
                if (k == "editor") {
                    val n = ProjectStore.list(ctx).size + 1
                    project = Project(System.currentTimeMillis().toString(), "Project " + n)
                    screen = "editor"
                } else {
                    screen = k
                }
            }
            "editor" -> {
                val p = project
                if (p != null) {
                    key(p.id) { EditorScreen(p, effects) { screen = "home" } }
                }
            }
            "projects" -> ProjectsScreen(effects, { p ->
                project = p
                screen = "editor"
            }, { screen = "home" })
            "import_fx" -> LabScreen(effects) { screen = "home" }
            else -> SoonScreen(screen) { screen = "home" }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("←  Home") }
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.padding(horizontal = 36.dp))
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

@Composable
fun ProjectsScreen(effects: List<NeoEffect>, onOpen: (Project) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(ProjectStore.list(ctx)) }
    Column(Modifier.fillMaxSize()) {
        TopBar("My projects", onBack)
        if (list.isEmpty()) {
            Text("No saved projects yet. Start one with New project.", color = Color(0xFF9A9AB0), modifier = Modifier.padding(24.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list) { t ->
                Card(
                    onClick = {
                        val p = ProjectStore.load(ctx, t.first, effects)
                        if (p != null) onOpen(p)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.second, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(DateFormat.getDateTimeInstance().format(Date(t.third)), color = Color(0xFFB8B8D0), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            ProjectStore.delete(ctx, t.first)
                            list = ProjectStore.list(ctx)
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
