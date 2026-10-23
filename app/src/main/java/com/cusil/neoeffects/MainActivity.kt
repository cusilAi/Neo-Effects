@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = NC.red)) {
                NeoApp()
            }
        }
    }
}

@Composable
fun NeoApp() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var project by remember { mutableStateOf<Project?>(null) }
    var editorTab by remember { mutableStateOf("edit") }
    var pendingTemplate by remember { mutableStateOf("") }
    var labInitial by remember { mutableStateOf<NeoEffect?>(null) }
    var showPro by remember { mutableStateOf(false) }
    var showBell by remember { mutableStateOf(false) }
    val effects = remember { mutableStateListOf<NeoEffect>().apply { addAll(EffectStore.load(ctx)) } }

    val gradePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val p = projectFromMedia(ctx, uris, "Color grade")
            if (p != null) {
                project = p
                editorTab = "color"
                screen = "editor"
            }
        }
    }

    fun openProject(id: String) {
        val p = ProjectStore.load(ctx, id, effects)
        if (p != null) {
            project = p
            editorTab = "edit"
            screen = "editor"
        }
    }

    fun go(k: String) {
        if (k == "color") {
            gradePicker.launch(arrayOf("video/*", "image/*"))
        } else {
            screen = k
        }
    }

    BackHandler(enabled = screen != "home" && screen != "editor") {
        screen = if (screen == "lab") "import_fx" else "home"
    }

    val barScreens = setOf("home", "projects", "templates", "profile")

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = NC.bg
    ) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                "home" -> HomeScreen(
                    onNewProject = {
                        pendingTemplate = ""
                        screen = "newproject"
                    },
                    onOpenProject = { openProject(it) },
                    onTemplate = {
                        pendingTemplate = it
                        screen = "newproject"
                    },
                    onNav = { go(it) },
                    onPro = { showPro = true },
                    onBell = { showBell = true }
                )
                "newproject" -> NewProjectScreen(
                    defaultName = "Project " + (ProjectStore.list(ctx).size + 1),
                    onBack = { screen = "home" },
                    onPro = { showPro = true },
                    onCreate = { p ->
                        p.templateId = pendingTemplate
                        ProjectStore.save(ctx, p)
                        project = p
                        editorTab = "edit"
                        screen = "editor"
                    }
                )
                "editor" -> {
                    val p = project
                    if (p != null) {
                        key(p.id) {
                            EditorScreen(p, effects, editorTab, { screen = "home" }, { showPro = true })
                        }
                    }
                }
                "projects" -> MyProjectsScreen(
                    onOpen = { openProject(it) },
                    onNew = {
                        pendingTemplate = ""
                        screen = "newproject"
                    }
                )
                "templates" -> TemplatesScreen {
                    pendingTemplate = it
                    screen = "newproject"
                }
                "profile" -> ProfileScreen(effects, { showPro = true }, { screen = "settings" })
                "settings" -> SettingsScreen { screen = "home" }
                "tutorial" -> TutorialScreen { screen = "home" }
                "removebg" -> RemoveBgScreen({ screen = "home" }, { showPro = true })
                "quality" -> EnhancerScreen({ screen = "home" }, { showPro = true })
                "import_fx" -> ImportEffectsScreen(
                    effects, { screen = "home" },
                    { e ->
                        labInitial = e
                        screen = "lab"
                    },
                    { showPro = true }
                )
                "lab" -> LabScreen(effects, labInitial) { screen = "import_fx" }
                "import_project" -> ImportProjectScreen(effects, { screen = "home" }, { showPro = true }) { p ->
                    project = p
                    editorTab = "edit"
                    screen = "editor"
                }
                else -> SoonScreen(screen) { screen = "home" }
            }
            if (screen in barScreens) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    NeoBottomBar(
                        selected = screen,
                        onNav = { screen = it },
                        onPlus = {
                            pendingTemplate = ""
                            screen = "newproject"
                        }
                    )
                }
            }
        }
    }

    if (showPro) ProDialog { showPro = false }
    if (showBell) {
        AlertDialog(
            onDismissRequest = { showBell = false },
            confirmButton = { TextButton(onClick = { showBell = false }) { Text("OK", color = NC.red) } },
            title = { Text("Notifications", color = Color.White) },
            text = { Text("You're all caught up. No new notifications.", color = NC.sub) },
            containerColor = NC.card
        )
    }
}
