@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

fun fmtMs(ms: Long): String {
    val m = ms / 60000L
    val s = (ms / 1000L) % 60L
    val d = (ms % 1000L) / 100L
    return String.format("%d:%02d.%d", m, s, d)
}

@Composable
fun ChainFeeder(project: Project, gl: GlEngine) {
    val ph = project.playheadMs
    val loc = project.clipAt(ph)
    val chain = ArrayList<Pass>()
    if (loc != null) {
        for (inst in loc.first.fx) {
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
fun ToolBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(label, fontSize = 13.sp) }
}

@Composable
fun EditorScreen(project: Project, library: List<NeoEffect>, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val gl = remember { GlEngine() }
    val engine = remember { PlaybackEngine(ctx, project, gl) }
    var selectedId by remember { mutableStateOf(-1L) }
    var tab by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var shaderErr by remember { mutableStateOf("") }

    remember {
        gl.onSurface = { s -> engine.onSurface(s) }
        gl.onError = { m -> shaderErr = m }
        0
    }

    BackHandler { onExit() }

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

    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
            project.clips.add(c)
            selectedId = c.id
            added = true
        }
        if (added) engine.show(project.playheadMs)
    }

    val sel = project.clips.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onExit) { Text("←  Home") }
            Spacer(Modifier.weight(1f))
            Text(project.name, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                ProjectStore.save(ctx, project)
                msg = "Saved"
            }) { Text("Save") }
            Button(
                onClick = { msg = "Export arrives in the next build" },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) { Text("Export") }
        }

        Box(Modifier.fillMaxWidth().height(250.dp).background(Color.Black)) {
            GlPreview(gl, Modifier.fillMaxSize())
            ChainFeeder(project, gl)
            if (project.clips.isEmpty()) {
                Text(
                    "Tap  ＋ Add clip  to start",
                    color = Color(0xFF9A9AB0),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (engine.error.isNotEmpty()) {
                Text(
                    engine.error,
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                )
            }
        }

        if (shaderErr.isNotEmpty()) {
            Text(
                "Shader error: " + shaderErr,
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        Transport(project, engine)

        Timeline(project, selectedId) { t ->
            val loc = project.clipAt(t)
            if (loc != null) selectedId = loc.first.id
            engine.pause()
            engine.show(minOf(t, project.totalMs))
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { addLauncher.launch(arrayOf("video/*", "image/*")) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("＋ Add clip", fontSize = 13.sp) }
            ToolBtn("✂ Split") {
                if (project.splitAtPlayhead()) {
                    engine.show(project.playheadMs)
                } else {
                    msg = "Move the playhead inside a clip first"
                }
            }
            ToolBtn("⧉ Duplicate", sel != null) {
                val s = sel
                if (s != null) {
                    val n = s.duplicate(newClipId(), 0L)
                    project.clips.add(project.clips.indexOf(s) + 1, n)
                    selectedId = n.id
                    engine.show(project.playheadMs)
                }
            }
            ToolBtn("🗑 Delete", sel != null) {
                val s = sel
                if (s != null) {
                    project.clips.remove(s)
                    selectedId = -1L
                    engine.show(project.playheadMs)
                }
            }
        }

        if (msg.isNotEmpty()) {
            Text(msg, color = Color(0xFFB8B8D0), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            if (sel == null) {
                Text("Tap a clip in the timeline to edit it.", color = Color(0xFF9A9AB0))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Effects") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Trim & Speed") })
                }
                Spacer(Modifier.height(8.dp))
                if (tab == 0) {
                    EffectsPanel(sel, project, library)
                } else {
                    TrimPanel(sel, project, engine)
                }
            }
        }
    }
}

@Composable
fun Transport(project: Project, engine: PlaybackEngine) {
    val total = project.totalMs
    val maxV = maxOf(total, 1L).toFloat()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { if (engine.playing) engine.pause() else engine.play() },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) { Text(if (engine.playing) "⏸" else "▶") }
        Spacer(Modifier.width(8.dp))
        Text(fmtMs(project.playheadMs) + " / " + fmtMs(total), color = Color.White, fontSize = 12.sp)
        Slider(
            value = project.playheadMs.toFloat().coerceIn(0f, maxV),
            onValueChange = { v ->
                engine.pause()
                engine.show(v.toLong(), true)
            },
            onValueChangeFinished = { engine.show(project.playheadMs) },
            valueRange = 0f..maxV,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}

@Composable
fun ClipBlock(c: Clip, selected: Boolean) {
    val ctx = LocalContext.current
    LaunchedEffect(c.id) {
        if (c.thumb == null) {
            c.thumb = withContext(Dispatchers.IO) { loadThumb(ctx, c) }
        }
    }
    val w = maxOf(48f, c.durMs * 0.06f).dp
    Box(
        Modifier
            .width(w)
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF6D42F0) else Color(0xFF2A2A40))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0xFF444460), RoundedCornerShape(6.dp))
    ) {
        val t = c.thumb
        if (t != null) {
            Image(
                bitmap = t.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.fillMaxSize()
            )
        }
        val label = (if (c.isVideo) "🎬 " else "🖼 ") + fmtMs(c.durMs) + (if (c.fx.isNotEmpty()) "  ✦" + c.fx.size else "")
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(4.dp))
    }
}

@Composable
fun Timeline(project: Project, selectedId: Long, onTap: (Long) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pxPerMs = with(density) { 0.06f.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(84.dp).background(Color(0xFF12121C))) {
        Box(Modifier.fillMaxHeight().horizontalScroll(scroll)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .pointerInput(project.clips.size) {
                        detectTapGestures { off ->
                            onTap(((off.x - 16.dp.toPx()) / pxPerMs).toLong().coerceAtLeast(0L))
                        }
                    }
            ) {
                Row(Modifier.fillMaxHeight().padding(vertical = 8.dp)) {
                    Spacer(Modifier.width(16.dp))
                    for (c in project.clips) {
                        ClipBlock(c, c.id == selectedId)
                    }
                    Spacer(Modifier.width(160.dp))
                }
                Box(
                    Modifier
                        .offset { IntOffset((16.dp.toPx() + project.playheadMs * pxPerMs).toInt(), 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
fun EffectsPanel(c: Clip, project: Project, library: List<NeoEffect>) {
    Text("Add effect to this clip", color = Color(0xFF9A9AB0), fontSize = 12.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(library) { e ->
            AssistChip(onClick = { c.fx.add(FxInst(e)) }, label = { Text(e.name) })
        }
    }
    Spacer(Modifier.height(8.dp))
    if (c.fx.isEmpty()) {
        Text("No effects on this clip yet.", color = Color(0xFF9A9AB0))
    }
    for (inst in c.fx.toList()) {
        FxCard(inst, c, project) { c.fx.remove(inst) }
    }
}

@Composable
fun FxCard(inst: FxInst, c: Clip, project: Project, onRemove: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(inst.effect.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("Remove") }
            }
            if (inst.effect.params.isEmpty()) {
                Text("No settings", color = Color(0xFF9A9AB0), fontSize = 12.sp)
            }
            for (p in inst.effect.params) {
                ParamRow(inst, p, c, project)
            }
        }
    }
}

@Composable
fun ParamRow(inst: FxInst, p: NeoParam, c: Clip, project: Project) {
    val off = (project.playheadMs - project.startOf(c)).coerceIn(0L, c.durMs)
    val v = inst.valueAt(p, off)
    val ks = inst.keys[p.id]
    val hasKeys = ks != null && ks.isNotEmpty()
    val onKey = ks != null && ks.any { abs(it.t - off) < 50L }
    val hi = maxOf(p.max, p.min + 0.01f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(p.name + ": " + String.format("%.2f", v), color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            if (onKey) {
                inst.values[p.id] = v
                inst.removeKey(p.id, off)
            } else {
                inst.setKey(p.id, off, v)
            }
        }) { Text(if (onKey) "◆ key" else "◇ key") }
    }
    Slider(
        value = v.coerceIn(p.min, hi),
        onValueChange = { nv ->
            if (hasKeys) inst.setKey(p.id, off, nv) else inst.values[p.id] = nv
        },
        valueRange = p.min..hi
    )
}

@Composable
fun TrimPanel(c: Clip, project: Project, engine: PlaybackEngine) {
    if (c.isVideo) {
        Text("Start  " + fmtMs(c.inMs), color = Color.White)
        Slider(
            value = c.inMs.toFloat(),
            onValueChange = { c.inMs = minOf(it.toLong(), c.outMs - 200L).coerceAtLeast(0L) },
            onValueChangeFinished = { engine.show(project.playheadMs) },
            valueRange = 0f..maxOf(1f, c.srcDurMs.toFloat())
        )
        Text("End  " + fmtMs(c.outMs), color = Color.White)
        Slider(
            value = c.outMs.toFloat(),
            onValueChange = { c.outMs = maxOf(it.toLong(), c.inMs + 200L).coerceAtMost(c.srcDurMs) },
            onValueChangeFinished = { engine.show(project.playheadMs) },
            valueRange = 0f..maxOf(1f, c.srcDurMs.toFloat())
        )
    } else {
        Text("Duration  " + fmtMs(c.outMs), color = Color.White)
        Slider(
            value = c.outMs.toFloat().coerceIn(500f, c.srcDurMs.toFloat()),
            onValueChange = { c.outMs = it.toLong().coerceAtLeast(500L) },
            onValueChangeFinished = { engine.show(project.playheadMs) },
            valueRange = 500f..c.srcDurMs.toFloat()
        )
    }
    Text("Speed  x" + String.format("%.2f", c.speed), color = Color.White)
    Slider(
        value = c.speed,
        onValueChange = { c.speed = (Math.round(it * 20f) / 20f).coerceIn(0.1f, 4f) },
        onValueChangeFinished = { engine.show(project.playheadMs) },
        valueRange = 0.1f..4f
    )
    Text("Time remapping curves come in a later build.", color = Color(0xFF9A9AB0), fontSize = 12.sp)
}
