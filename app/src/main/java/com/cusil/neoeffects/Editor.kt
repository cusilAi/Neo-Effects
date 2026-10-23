@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

fun fmtMs(ms: Long): String {
    val m = ms / 60000L
    val s = (ms / 1000L) % 60L
    val d = (ms % 1000L) / 100L
    return String.format("%d:%02d.%d", m, s, d)
}

class UndoStack(private val project: Project, private val lib: List<NeoEffect>) {
    private val undos = ArrayList<String>()
    private val redos = ArrayList<String>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun snap(): String = ProjectStore.toJson(project).toString()

    private fun sync() {
        canUndo = undos.isNotEmpty()
        canRedo = redos.isNotEmpty()
    }

    fun push() {
        undos.add(snap())
        if (undos.size > 40) undos.removeAt(0)
        redos.clear()
        sync()
    }

    private fun restore(s: String) {
        val p2 = ProjectStore.fromJson(JSONObject(s), lib)
        project.clips.clear()
        project.clips.addAll(p2.clips)
        project.playheadMs = p2.playheadMs
    }

    fun undo(): Boolean {
        if (undos.isEmpty()) return false
        redos.add(snap())
        restore(undos.removeAt(undos.size - 1))
        sync()
        return true
    }

    fun redo(): Boolean {
        if (redos.isEmpty()) return false
        undos.add(snap())
        restore(redos.removeAt(redos.size - 1))
        sync()
        return true
    }
}

@Composable
fun ChainFeeder(project: Project, gl: GlEngine, bypassGrade: Boolean) {
    val ph = project.playheadMs
    val loc = project.clipAt(ph)
    val chain = ArrayList<Pass>()
    if (loc != null) {
        for (inst in loc.first.fx) {
            if (bypassGrade && inst.effect.id == "color_grade") continue
            val vals = HashMap<String, Float>()
            for (p in inst.effect.params) vals[p.id] = inst.valueAt(p, loc.second)
            chain.add(Pass(inst.effect, vals))
        }
    }
    SideEffect {
        gl.chain = chain
        gl.timeSec = ph / 1000f
    }
}

@Composable
fun NeoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.clip(shape).background(if (selected) Color(0xFF3A0914) else NC.card2)
            .border(1.dp, if (selected) NC.red else NC.border, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = if (selected) NC.red else Color.White, fontSize = 12.sp, maxLines = 1) }
}

@Composable
fun ToolPill(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(NC.card2).border(1.dp, NC.border, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (enabled) NC.red else NC.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (enabled) Color.White else NC.dim, fontSize = 12.sp)
    }
}

@Composable
fun ParamControl(inst: FxInst, p: NeoParam, clip: Clip, project: Project, undo: UndoStack, modifier: Modifier = Modifier) {
    val off = (project.playheadMs - project.startOf(clip)).coerceIn(0L, clip.durMs)
    val v = inst.valueAt(p, off)
    val ks = inst.keys[p.id]
    val hasKeys = ks != null && ks.isNotEmpty()
    val onKey = ks != null && ks.any { abs(it.t - off) < 50L }
    val hi = maxOf(p.max, p.min + 0.01f)
    var drag by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(String.format("%.2f", v), color = NC.sub, fontSize = 11.sp)
            Text(
                if (onKey) " ◆" else " ◇", color = if (hasKeys) NC.red else NC.sub, fontSize = 15.sp,
                modifier = Modifier.clickable {
                    undo.push()
                    if (onKey) {
                        inst.values[p.id] = v
                        inst.removeKey(p.id, off)
                    } else {
                        inst.setKey(p.id, off, v)
                    }
                }.padding(horizontal = 6.dp)
            )
        }
        Slider(
            value = v.coerceIn(p.min, hi),
            onValueChange = { nv ->
                if (!drag) {
                    undo.push()
                    drag = true
                }
                if (hasKeys) inst.setKey(p.id, off, nv) else inst.values[p.id] = nv
            },
            onValueChangeFinished = { drag = false },
            valueRange = p.min..hi,
            colors = SliderDefaults.colors(thumbColor = NC.red, activeTrackColor = NC.red, inactiveTrackColor = Color(0xFF2A2C3A))
        )
    }
}

@Composable
fun EditorScreen(
    project: Project,
    library: List<NeoEffect>,
    startTab: String,
    onExit: () -> Unit,
    onPro: () -> Unit
) {
    val ctx = LocalContext.current
    val gl = remember { GlEngine() }
    val engine = remember { PlaybackEngine(ctx, project, gl) }
    val undo = remember { UndoStack(project, library) }
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf(project.clips.firstOrNull()?.id ?: -1L) }
    var tab by remember { mutableStateOf(startTab) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var shaderErr by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(40f) }
    var full by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var hiddenTracks by remember { mutableStateOf(setOf<Int>()) }
    var activeFx by remember { mutableStateOf("") }
    var bypass by remember { mutableStateOf(false) }

    remember {
        gl.onSurface = { s -> engine.onSurface(s) }
        gl.onError = { m -> shaderErr = m }
        0
    }

    SideEffect {
        gl.canvasAspect = project.width.toFloat() / project.height.toFloat()
        gl.bgMode = project.bgMode
        gl.maxSide = when (project.previewQ) {
            0 -> 720
            2 -> 1920
            else -> 1280
        }
        gl.hidden = hiddenTracks.contains(0)
    }
    LaunchedEffect(project.bgUri, project.bgMode) {
        if (project.bgMode == 4 && project.bgUri.isNotEmpty()) {
            val b = withContext(Dispatchers.IO) {
                try {
                    loadBitmap(ctx, Uri.parse(project.bgUri), 1024)
                } catch (e: Exception) {
                    null
                }
            }
            gl.setBgBitmap(b)
        } else {
            gl.setBgBitmap(null)
        }
    }
    LaunchedEffect(project.autosave) {
        while (project.autosave) {
            delay(30000L)
            try { ProjectStore.save(ctx, project) } catch (e: Exception) { }
        }
    }

    BackHandler {
        if (tab == "effects" || tab == "color") tab = "edit" else onExit()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) engine.pause()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { ProjectStore.save(ctx, project) } catch (e: Exception) { }
            engine.releaseAll()
        }
    }
    LaunchedEffect(Unit) { engine.show(project.playheadMs) }
    LaunchedEffect(engine.playing) {
        if (engine.playing) {
            var last = withFrameNanos { it }
            while (engine.playing) {
                val now = withFrameNanos { it }
                engine.tick((now - last) / 1_000_000L)
                last = now
            }
        }
    }

    fun addUris(uris: List<Uri>) {
        undo.push()
        var added = false
        for (u in uris) {
            try {
                ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val pr = probeMedia(ctx, u)
            if (pr == null) {
                msg = "Could not read that file"
                continue
            }
            val c = Clip(newClipId(), u.toString(), pr.isVideo, pr.durMs, pr.name)
            if (!pr.isVideo) c.outMs = 3000L
            applyTemplate(project.templateId, c, library)
            project.clips.add(c)
            selectedId = c.id
            added = true
        }
        if (added) engine.show(project.playheadMs)
    }
    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addUris(uris)
    }

    val sel = project.clips.firstOrNull { it.id == selectedId }
    val sheet = tab == "effects" || tab == "color"

    fun doSplit() {
        undo.push()
        if (project.splitAtPlayhead()) engine.show(project.playheadMs) else msg = "Move the playhead inside a clip first"
    }
    fun doDuplicate() {
        val s = sel ?: return
        undo.push()
        val n = s.duplicate(newClipId(), 0L)
        project.clips.add(project.clips.indexOf(s) + 1, n)
        selectedId = n.id
        engine.show(project.playheadMs)
    }
    fun doDelete() {
        val s = sel ?: return
        undo.push()
        project.clips.remove(s)
        selectedId = -1L
        engine.show(project.playheadMs)
    }
    fun doUndo() {
        if (undo.undo()) engine.show(project.playheadMs)
    }
    fun doRedo() {
        if (undo.redo()) engine.show(project.playheadMs)
    }
    fun jump(dir: Int) {
        engine.pause()
        var t = 0L
        val starts = ArrayList<Long>()
        for (c in project.clips) {
            starts.add(t)
            t += c.durMs
        }
        starts.add(t)
        val ph = project.playheadMs
        val target = if (dir > 0) starts.firstOrNull { it > ph + 50L } ?: t else starts.lastOrNull { it < ph - 50L } ?: 0L
        engine.show(target)
    }
    fun shareProject() {
        scope.launch {
            busy = "Packing project..."
            val r = withContext(Dispatchers.IO) { exportNeoproj(ctx, project) }
            busy = ""
            msg = r
        }
    }

    Column(Modifier.fillMaxSize().background(NC.bg)) {
        if (!full) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.Outlined.ArrowBack, "Back", size = 36.dp) { onExit() }
                NeoLogo(small = true)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 110.dp)) {
                    Text(
                        project.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(project.resLabel + " · " + project.fps + "fps", color = NC.sub, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                ProChip(onPro)
                IconBtn(Icons.Outlined.Save, "Save", size = 36.dp) {
                    ProjectStore.save(ctx, project)
                    msg = "Saved"
                }
                Box {
                    IconBtn(Icons.Outlined.MoreVert, "Menu", size = 36.dp) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Export video (coming soon)") }, onClick = {
                            menuOpen = false
                            msg = "Video export arrives in the next build"
                        })
                        DropdownMenuItem(text = { Text("Share project file (.neoproj)") }, onClick = {
                            menuOpen = false
                            shareProject()
                        })
                        DropdownMenuItem(text = { Text("Fullscreen preview") }, onClick = {
                            menuOpen = false
                            full = true
                        })
                    }
                }
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth().heightIn(min = 150.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(6.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                GlPreview(gl, Modifier.fillMaxSize())
                ChainFeeder(project, gl, bypass)
                Text(
                    project.resLabel + "  ·  " + project.fps + "fps", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconBtn(if (full) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Fullscreen", size = 34.dp) { full = !full }
                }
                if (project.clips.isEmpty()) {
                    Text("Tap  Add Media  to start", color = NC.sub, modifier = Modifier.align(Alignment.Center))
                }
                if (busy.isNotEmpty()) {
                    Text(busy, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(10.dp))
                }
                if (engine.error.isNotEmpty()) {
                    Text(engine.error, color = Color(0xFFFF6B6B), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
            if (!full) {
                ToolRail(tab) { k ->
                    if (k == "ai") {
                        msg = "AI Enhance: open Home > Quality Boost"
                    } else {
                        tab = if (tab == k) "edit" else k
                    }
                }
            }
        }

        TransportBar(
            project, engine, undo,
            onZoom = { zoom = if (zoom >= 120f) 20f else zoom * 2f },
            onPrev = { jump(-1) }, onNext = { jump(1) },
            onUndo = { doUndo() }, onRedo = { doRedo() }
        )

        if (!full) {
            if (shaderErr.isNotEmpty()) {
                Text(
                    "Shader error: " + shaderErr, color = Color(0xFFFF6B6B), fontSize = 10.sp, maxLines = 2,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            TimelineBlock(
                project, selectedId, zoom, engine.playing, hiddenTracks, sheet,
                onSeek = { t, fast ->
                    engine.pause()
                    engine.show(minOf(t, project.totalMs), fast)
                },
                onSelectAt = { t ->
                    val loc = project.clipAt(t)
                    if (loc != null) selectedId = loc.first.id
                    engine.pause()
                    engine.show(minOf(t, project.totalMs))
                },
                onEye = { i ->
                    hiddenTracks = if (hiddenTracks.contains(i)) hiddenTracks - i else hiddenTracks + i
                },
                onPlus = { i ->
                    if (i == 0) addLauncher.launch(arrayOf("video/*", "image/*")) else msg = "This track type is coming soon"
                }
            )

            if (sheet) {
                Box(Modifier.weight(1.45f).fillMaxWidth()) {
                    if (tab == "effects") {
                        EffectsSheet(sel, library, project, undo, activeFx, { activeFx = it }, { tab = "edit" })
                    } else {
                        ColorGradePanel(sel, library, undo, bypass, { bypass = it }, { tab = "edit" })
                    }
                }
            } else {
                BottomTabs(tab) { tab = it }
                Box(Modifier.fillMaxWidth().height(176.dp)) {
                    when (tab) {
                        "edit" -> EditPanel(
                            project, selectedId, sel != null,
                            onSelect = { c ->
                                selectedId = c.id
                                engine.pause()
                                engine.show(project.startOf(c) + 1L)
                            },
                            onAdd = { addLauncher.launch(arrayOf("video/*", "image/*")) },
                            onSplit = { doSplit() }, onDup = { doDuplicate() }, onDelete = { doDelete() },
                            onSpeed = { tab = "speed" }
                        )
                        "filters" -> FiltersPanel(sel, library, project, undo)
                        "adjust" -> AdjustPanel(sel, library, project, undo)
                        "speed" -> {
                            if (sel == null) {
                                Text("Select a clip first.", color = NC.sub, modifier = Modifier.padding(16.dp))
                            } else {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                    TrimPanel(sel, project, engine, undo)
                                }
                            }
                        }
                        "more" -> Row(
                            Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ToolPill(Icons.Outlined.Save, "Share project file") { shareProject() }
                            ToolPill(Icons.Outlined.Fullscreen, "Fullscreen") { full = true }
                        }
                        else -> Text(
                            "This tool is coming in a later build.", color = NC.sub,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            if (msg.isNotEmpty() && !sheet) {
                Text(msg, color = NC.sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun ToolRail(tab: String, onPick: (String) -> Unit) {
    Column(
        Modifier.width(60.dp).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailItem(Icons.Outlined.AutoAwesome, "Effects", tab == "effects") { onPick("effects") }
        RailItem(Icons.Outlined.Palette, "Filters", tab == "filters") { onPick("filters") }
        RailItem(Icons.Outlined.Tune, "Adjust", tab == "adjust") { onPick("adjust") }
        RailItem(Icons.Outlined.AutoFixHigh, "AI Enhance", false) { onPick("ai") }
        RailItem(Icons.Outlined.Speed, "Speed", tab == "speed") { onPick("speed") }
        RailItem(Icons.Outlined.MoreHoriz, "More", tab == "more") { onPick("more") }
    }
}

@Composable
fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Color(0xFF3A0914) else Color.Transparent)
            .border(1.dp, if (selected) NC.red else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) NC.red else Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) NC.red else Color.White, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
fun TransportBar(
    project: Project, engine: PlaybackEngine, undo: UndoStack,
    onZoom: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fmtClock(project.playheadMs), color = Color.White, fontSize = 12.sp)
        Text(" / " + fmtClock(project.totalMs), color = NC.sub, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Outlined.SkipPrevious, "Previous", size = 36.dp) { onPrev() }
        Box(
            Modifier.size(42.dp).clip(CircleShape).clickable { if (engine.playing) engine.pause() else engine.play() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (engine.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        IconBtn(Icons.Outlined.SkipNext, "Next", size = 36.dp) { onNext() }
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Outlined.ZoomIn, "Timeline zoom", NC.sub, 34.dp) { onZoom() }
        IconBtn(Icons.Outlined.Undo, "Undo", if (undo.canUndo) Color.White else NC.dim, 34.dp) { onUndo() }
        IconBtn(Icons.Outlined.Redo, "Redo", if (undo.canRedo) Color.White else NC.dim, 34.dp) { onRedo() }
    }
}

@Composable
fun ClipBlock(c: Clip, selected: Boolean, zoom: Float) {
    val ctx = LocalContext.current
    LaunchedEffect(c) {
        if (c.thumb == null) {
            c.thumb = withContext(Dispatchers.IO) { loadThumb(ctx, c) }
        }
    }
    val w = maxOf(30f, c.durMs / 1000f * zoom).dp
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.width(w).fillMaxHeight().padding(horizontal = 0.5.dp).clip(shape)
            .background(Color(0xFF2A0A12))
            .border(if (selected) 2.dp else 1.dp, if (selected) NC.redGlow else Color(0xFF5A1626), shape)
    ) {
        val t = c.thumb
        if (t != null) {
            Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.7f)
        }
        val label = (if (c.fx.isNotEmpty()) "✦" + c.fx.size + " " else "") + fmtClock(c.durMs)
        Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(3.dp))
    }
}

@Composable
fun TrackLabel(name: String, icon: ImageVector, hidden: Boolean, dimmed: Boolean, onEye: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.width(76.dp).height(34.dp).clip(shape).background(NC.card2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Visibility",
            tint = if (hidden) NC.dim else NC.sub,
            modifier = Modifier.padding(start = 4.dp).size(22.dp).clickable { onEye() }.padding(3.dp)
        )
        Icon(icon, name, tint = if (dimmed) NC.dim else NC.red, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        Text(name, color = if (dimmed) NC.dim else Color.White, fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
fun Ruler(totalMs: Long, zoom: Float, onSeek: (Long, Boolean) -> Unit, pxPerMs: Float) {
    val step = if (zoom >= 100f) 1 else if (zoom >= 50f) 2 else if (zoom >= 25f) 5 else 10
    val secs = (totalMs / 1000L).toInt() + 12
    Box(
        Modifier.fillMaxWidth().height(20.dp).pointerInput(zoom) {
            var lastT = 0L
            detectHorizontalDragGestures(
                onDragEnd = { onSeek(lastT, false) },
                onDragCancel = { onSeek(lastT, false) }
            ) { change, _ ->
                change.consume()
                lastT = ((change.position.x - 8.dp.toPx()) / pxPerMs).toLong().coerceAtLeast(0L)
                onSeek(lastT, true)
            }
        }
    ) {
        var s = 0
        while (s <= secs) {
            val x = (8f + s * zoom).dp
            Box(Modifier.offset(x = x, y = 13.dp).width(1.dp).height(6.dp).background(NC.border))
            Text(fmtClock(s * 1000L), color = NC.sub, fontSize = 8.sp, modifier = Modifier.offset(x = x + 2.dp, y = 2.dp))
            s += step
        }
    }
}

@Composable
fun FollowPlayhead(project: Project, scroll: ScrollState, pxPerMs: Float, viewW: Int, playing: Boolean) {
    val x = project.playheadMs * pxPerMs
    LaunchedEffect((x / 40f).toInt(), playing) {
        if (playing && viewW > 0 && (x > scroll.value + viewW - 60 || x < scroll.value)) {
            scroll.scrollTo(maxOf(0, (x - viewW / 2f).toInt()))
        }
    }
}

@Composable
fun TimelineBlock(
    project: Project, selectedId: Long, zoom: Float, playing: Boolean, hidden: Set<Int>, compact: Boolean,
    onSeek: (Long, Boolean) -> Unit, onSelectAt: (Long) -> Unit, onEye: (Int) -> Unit, onPlus: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pxPerMs = with(density) { (zoom / 1000f).dp.toPx() }
    val names = listOf("Video 1", "Video 2", "Overlay", "Text", "Audio")
    val icons = listOf(Icons.Outlined.Movie, Icons.Outlined.VideoLibrary, Icons.Outlined.Layers, Icons.Outlined.TextFields, Icons.Outlined.MusicNote)
    val tracks = if (compact) 1 else 5
    val contentW = ((project.totalMs / 1000f) * zoom + 200f).dp
    var viewW by remember { mutableStateOf(0) }

    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.width(80.dp).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(20.dp))
            for (i in 0 until tracks) {
                TrackLabel(names[i], icons[i], hidden.contains(i), i != 0) { onEye(i) }
            }
        }
        Box(Modifier.weight(1f).onSizeChanged { viewW = it.width }.horizontalScroll(scroll)) {
            Box(
                Modifier.width(contentW).pointerInput(project.clips.size, zoom) {
                    detectTapGestures { off ->
                        onSelectAt(((off.x - 8.dp.toPx()) / pxPerMs).toLong().coerceAtLeast(0L))
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ruler(project.totalMs, zoom, onSeek, pxPerMs)
                    Row(Modifier.height(34.dp)) {
                        Spacer(Modifier.width(8.dp))
                        for (c in project.clips) {
                            ClipBlock(c, c.id == selectedId, zoom)
                        }
                    }
                    for (i in 1 until tracks) {
                        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0B0C12)))
                    }
                }
                Box(
                    Modifier
                        .offset { IntOffset((8.dp.toPx() + project.playheadMs * pxPerMs).toInt() - 1, 0) }
                        .width(2.dp).fillMaxHeight()
                        .drawBehind {
                            drawLine(NC.redGlow, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = size.width)
                            drawCircle(NC.redGlow, radius = 5.dp.toPx(), center = Offset(size.width / 2f, 6.dp.toPx()))
                        }
                )
            }
        }
        Column(Modifier.width(38.dp).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(20.dp))
            for (i in 0 until tracks) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(NC.card2)
                        .border(1.dp, NC.border, RoundedCornerShape(8.dp)).clickable { onPlus(i) },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color.White, fontSize = 18.sp) }
            }
        }
    }
    FollowPlayhead(project, scroll, pxPerMs, viewW, playing)
}

@Composable
fun BottomTabs(tab: String, onPick: (String) -> Unit) {
    val items = listOf(
        Triple("edit", "Edit", Icons.Outlined.ContentCut),
        Triple("audio", "Audio", Icons.Outlined.MusicNote),
        Triple("text", "Text", Icons.Outlined.TextFields),
        Triple("overlay", "Overlay", Icons.Outlined.Layers),
        Triple("effects", "Effects", Icons.Outlined.AutoAwesome),
        Triple("transitions", "Transitions", Icons.Outlined.Compare),
        Triple("filters", "Filters", Icons.Outlined.Palette),
        Triple("adjust", "Adjust", Icons.Outlined.Tune),
        Triple("color", "Color", Icons.Outlined.Palette)
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (tb in items) {
            val sel = tab == tb.first
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(68.dp).clip(shape)
                    .background(if (sel) Color(0xFF3A0914) else Color.Transparent)
                    .border(1.dp, if (sel) NC.red else Color.Transparent, shape)
                    .clickable { onPick(tb.first) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tb.third, tb.second, tint = if (sel) NC.red else Color.White, modifier = Modifier.size(20.dp))
                Text(tb.second, color = if (sel) NC.red else Color.White, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun EditPanel(
    project: Project, selectedId: Long, hasSel: Boolean,
    onSelect: (Clip) -> Unit, onAdd: () -> Unit, onSplit: () -> Unit, onDup: () -> Unit, onDelete: () -> Unit, onSpeed: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolPill(Icons.Outlined.ContentCut, "Split") { onSplit() }
            ToolPill(Icons.Outlined.ContentCopy, "Duplicate", hasSel) { onDup() }
            ToolPill(Icons.Outlined.Delete, "Delete", hasSel) { onDelete() }
            ToolPill(Icons.Outlined.Speed, "Trim / Speed", hasSel) { onSpeed() }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(project.clips.toList()) { c ->
                val shape = RoundedCornerShape(10.dp)
                val sel = c.id == selectedId
                Column(
                    Modifier.width(96.dp).clip(shape).background(NC.card2)
                        .border(1.dp, if (sel) NC.red else NC.border, shape).clickable { onSelect(c) }
                ) {
                    Box(Modifier.fillMaxWidth().height(54.dp).background(Brush.linearGradient(listOf(NC.redDeep, NC.bg)))) {
                        val t = c.thumb
                        if (t != null) Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text(c.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    Text(fmtMs(c.durMs) + "s", color = NC.sub, fontSize = 9.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                }
            }
            item {
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier.width(96.dp).height(92.dp).clip(shape).background(NC.card2)
                        .border(1.dp, NC.border, shape).clickable { onAdd() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("+", color = Color.White, fontSize = 26.sp)
                    Text("Add Media", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun FiltersPanel(clip: Clip?, library: List<NeoEffect>, project: Project, undo: UndoStack) {
    if (clip == null) {
        Text("Select a clip first.", color = NC.sub, modifier = Modifier.padding(16.dp))
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(library.filter { it.category == "Color" && it.id != "color_grade" }) { e ->
                NeoChip(e.name, clip.fx.any { it.effect.id == e.id }) {
                    undo.push()
                    val ex = clip.fx.firstOrNull { it.effect.id == e.id }
                    if (ex != null) clip.fx.remove(ex) else clip.fx.add(FxInst(e))
                }
            }
        }
        for (inst in clip.fx.filter { it.effect.category == "Color" && it.effect.id != "color_grade" }) {
            Spacer(Modifier.height(8.dp))
            Text(inst.effect.name, color = NC.red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            for (p in inst.effect.params) ParamControl(inst, p, clip, project, undo)
        }
    }
}

@Composable
fun AdjustPanel(clip: Clip?, library: List<NeoEffect>, project: Project, undo: UndoStack) {
    if (clip == null) {
        Text("Select a clip first.", color = NC.sub, modifier = Modifier.padding(16.dp))
        return
    }
    val inst = clip.fx.firstOrNull { it.effect.id == "color_adjust" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (inst == null) {
            val e = library.firstOrNull { it.id == "color_adjust" }
            ToolPill(Icons.Outlined.Tune, "Enable Adjust (brightness, contrast, saturation)", e != null) {
                if (e != null) {
                    undo.push()
                    clip.fx.add(FxInst(e))
                }
            }
        } else {
            for (p in inst.effect.params) ParamControl(inst, p, clip, project, undo)
        }
    }
}

@Composable
fun TrimPanel(c: Clip, project: Project, engine: PlaybackEngine, undo: UndoStack) {
    var drag by remember { mutableStateOf(false) }
    val sc = SliderDefaults.colors(thumbColor = NC.red, activeTrackColor = NC.red, inactiveTrackColor = Color(0xFF2A2C3A))
    fun start() {
        if (!drag) {
            undo.push()
            drag = true
        }
    }
    fun done() {
        drag = false
        engine.show(project.playheadMs)
    }
    if (c.isVideo) {
        Text("Start  " + fmtMs(c.inMs), color = Color.White, fontSize = 12.sp)
        Slider(
            value = c.inMs.toFloat(),
            onValueChange = {
                start()
                c.inMs = minOf(it.toLong(), c.outMs - 200L).coerceAtLeast(0L)
            },
            onValueChangeFinished = { done() },
            valueRange = 0f..maxOf(1f, c.srcDurMs.toFloat()), colors = sc
        )
        Text("End  " + fmtMs(c.outMs), color = Color.White, fontSize = 12.sp)
        Slider(
            value = c.outMs.toFloat(),
            onValueChange = {
                start()
                c.outMs = maxOf(it.toLong(), c.inMs + 200L).coerceAtMost(c.srcDurMs)
            },
            onValueChangeFinished = { done() },
            valueRange = 0f..maxOf(1f, c.srcDurMs.toFloat()), colors = sc
        )
    } else {
        Text("Duration  " + fmtMs(c.outMs), color = Color.White, fontSize = 12.sp)
        Slider(
            value = c.outMs.toFloat().coerceIn(500f, c.srcDurMs.toFloat()),
            onValueChange = {
                start()
                c.outMs = it.toLong().coerceAtLeast(500L)
            },
            onValueChangeFinished = { done() },
            valueRange = 500f..c.srcDurMs.toFloat(), colors = sc
        )
    }
    Text("Speed  x" + String.format("%.2f", c.speed), color = Color.White, fontSize = 12.sp)
    Slider(
        value = c.speed,
        onValueChange = {
            start()
            c.speed = (Math.round(it * 20f) / 20f).coerceIn(0.1f, 4f)
        },
        onValueChangeFinished = { done() },
        valueRange = 0.1f..4f, colors = sc
    )
    Text("Time remapping curves come in a later build.", color = NC.sub, fontSize = 11.sp)
}
