@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NeoprojItem(val uri: Uri, val name: String, val size: Long, val dateSec: Long)

private fun safeName(s: String): String {
    val t = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return if (t.isEmpty()) "file" else t.take(60)
}

fun saveToDownloads(ctx: Context, src: File, name: String): String {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues()
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/NEOeffects")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                "Could not create the file"
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                "Saved to Downloads/NEOeffects/" + name
            }
        } else {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val dst = File(dir, name)
            src.copyTo(dst, true)
            "Saved to " + dst.absolutePath
        }
    } catch (e: Exception) {
        "Save failed: " + (e.message ?: "unknown")
    }
}

fun exportNeoproj(ctx: Context, p: Project): String {
    try {
        val json = ProjectStore.toJson(p)
        val map = HashMap<String, String>()
        val tmp = File(ctx.cacheDir, p.id + ".neoproj")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
            val clips = json.getJSONArray("clips")
            var n = 0
            for (i in 0 until clips.length()) {
                val co = clips.getJSONObject(i)
                val uri = co.getString("uri")
                val existing = map[uri]
                if (existing != null) {
                    co.put("uri", existing)
                    continue
                }
                val ins = ctx.contentResolver.openInputStream(Uri.parse(uri)) ?: continue
                n++
                val rel = "media/" + n + "_" + safeName(co.optString("name", "clip"))
                zos.putNextEntry(ZipEntry(rel))
                ins.use { it.copyTo(zos) }
                zos.closeEntry()
                map[uri] = rel
                co.put("uri", rel)
            }
            zos.putNextEntry(ZipEntry("project.json"))
            zos.write(json.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val msg = saveToDownloads(ctx, tmp, safeName(p.name) + ".neoproj")
        tmp.delete()
        return msg
    } catch (e: Exception) {
        return "Export failed: " + (e.message ?: "unknown")
    }
}

fun importNeoproj(ctx: Context, uri: Uri, lib: List<NeoEffect>): Project? {
    return try {
        val newId = System.currentTimeMillis().toString()
        val dir = File(ctx.filesDir, "imports/" + newId)
        dir.mkdirs()
        var js: String? = null
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val name = e.name
                if (name == "project.json") {
                    js = zis.readBytes().toString(Charsets.UTF_8)
                } else if (name.startsWith("media/") && !name.contains("..")) {
                    val f = File(dir, name)
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        val text = js ?: return null
        val o = JSONObject(text)
        val cs = o.getJSONArray("clips")
        for (i in 0 until cs.length()) {
            val co = cs.getJSONObject(i)
            val u = co.getString("uri")
            if (u.startsWith("media/")) co.put("uri", Uri.fromFile(File(dir, u)).toString())
        }
        o.put("id", newId)
        o.put("name", o.optString("name", "Project") + " (imported)")
        val p = ProjectStore.fromJson(o, lib)
        ProjectStore.save(ctx, p)
        p
    } catch (e: Exception) {
        null
    }
}

fun videoSize(ctx: Context, uri: Uri): Pair<Int, Int>? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) null else if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
    } catch (e: Exception) {
        null
    } finally {
        try { r.release() } catch (e: Exception) { }
    }
}

fun projectFromMedia(ctx: Context, uris: List<Uri>, name: String): Project? {
    val p = Project(System.currentTimeMillis().toString(), name)
    var sized = false
    for (u in uris) {
        try {
            ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        val pr = probeMedia(ctx, u) ?: continue
        val c = Clip(newClipId(), u.toString(), pr.isVideo, pr.durMs, pr.name)
        if (!pr.isVideo) c.outMs = 3000L
        p.clips.add(c)
        if (!sized && pr.isVideo) {
            val d = videoSize(ctx, u)
            if (d != null) {
                var w = d.first
                var h = d.second
                val short = minOf(w, h)
                if (short > 1080) {
                    val f = 1080f / short
                    w = (w * f).toInt()
                    h = (h * f).toInt()
                }
                p.width = maxOf(2, w - w % 2)
                p.height = maxOf(2, h - h % 2)
                val s2 = minOf(p.width, p.height)
                p.resLabel = if (s2 >= 1080) "1080p" else if (s2 >= 720) "720p" else "480p"
                p.aspectLabel = "Auto"
                sized = true
            }
        }
    }
    if (p.clips.isEmpty()) return null
    ProjectStore.save(ctx, p)
    return p
}

fun listNeoprojs(ctx: Context): List<NeoprojItem> {
    val out = ArrayList<NeoprojItem>()
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val proj = arrayOf(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED
            )
            ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj,
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?", arrayOf("%.neoproj"),
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out.add(
                        NeoprojItem(
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                            c.getString(1) ?: "project.neoproj", c.getLong(2), c.getLong(3)
                        )
                    )
                }
            }
        } else {
            val dir = ctx.getExternalFilesDir(null)
            dir?.listFiles()?.forEach {
                if (it.name.endsWith(".neoproj")) out.add(NeoprojItem(Uri.fromFile(it), it.name, it.length(), it.lastModified() / 1000L))
            }
        }
    } catch (e: Exception) {
    }
    return out
}

@Composable
fun ImportProjectScreen(
    effects: List<NeoEffect>,
    onBack: () -> Unit,
    onPro: () -> Unit,
    onOpened: (Project) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<NeoprojItem>()) }
    var selected by remember { mutableStateOf(-1) }
    var keep by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { listNeoprojs(ctx) }
        if (items.isNotEmpty()) selected = 0
    }

    fun doImport(uri: Uri, deleteAfter: Boolean) {
        scope.launch {
            busy = true
            msg = "Importing project..."
            val p = withContext(Dispatchers.IO) { importNeoproj(ctx, uri, effects) }
            busy = false
            if (p == null) {
                msg = "That file is not a valid NEOeffects project."
            } else {
                if (deleteAfter) {
                    try { ctx.contentResolver.delete(uri, null, null) } catch (e: Exception) { }
                }
                onOpened(p)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) doImport(uri, false)
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val p = projectFromMedia(ctx, uris, "Imported project")
            if (p == null) msg = "Could not read those files" else onOpened(p)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowBack, "Back") { onBack() }
            NeoLogo(small = true)
            Spacer(Modifier.weight(1f))
            ProChip(onPro)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row {
                Text("Import ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("Project", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
            Text("Open your saved projects and continue your edits", color = NC.sub, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceCard("From Device", "Open a .neoproj file", "📱", true, Modifier.weight(1f)) { filePicker.launch(arrayOf("*/*")) }
                SourceCard("From Media", "Videos & photos", "🎞️", true, Modifier.weight(1f)) { mediaPicker.launch(arrayOf("video/*", "image/*")) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceCard("From Cloud", "Coming soon", "☁️", false, Modifier.weight(1f)) { msg = "Cloud import is coming soon" }
                SourceCard("From QR Code", "Coming soon", "🔳", false, Modifier.weight(1f)) { msg = "QR import is coming soon" }
            }
            Spacer(Modifier.height(12.dp))
            GradeCard("Projects on Device") {
                if (items.isEmpty()) {
                    Text(
                        "No .neoproj files found yet. In the editor, open the ⋮ menu and choose Share project file. It will appear here.",
                        color = NC.sub, fontSize = 12.sp
                    )
                }
                for (i in items.indices) {
                    val it2 = items[i]
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(NC.card)
                            .border(if (selected == i) 1.5.dp else 1.dp, if (selected == i) NC.red else NC.border, shape)
                            .clickable { selected = i }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(NC.redDeep, NC.bg))), contentAlignment = Alignment.Center) { Text("🎬") }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it2.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text(
                                String.format("%.1f MB", it2.size / 1048576.0) + " · " + DateFormat.getDateInstance().format(Date(it2.dateSec * 1000L)),
                                color = NC.sub, fontSize = 10.sp
                            )
                        }
                        Text(
                            "Import", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(NC.red)
                                .clickable { if (!busy) doImport(it2.uri, !keep) }.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Text(
                "Note: After Effects (.aep) and Alight Motion files use closed formats that no other app can open. Export from those apps as video, PNG or Lottie and import the result as media.",
                color = NC.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)
            )
            if (msg.isNotEmpty()) Text(msg, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(NC.card)
                .border(1.dp, NC.border, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = keep, onCheckedChange = { keep = it }, colors = CheckboxDefaults.colors(checkedColor = NC.red))
            Column(Modifier.weight(1f)) {
                Text("Keep original file", color = Color.White, fontSize = 12.sp)
                Text("Untick to delete the .neoproj after importing", color = NC.sub, fontSize = 9.sp)
            }
            Button(
                onClick = {
                    if (selected in items.indices && !busy) doImport(items[selected].uri, !keep)
                    else msg = "Pick a project from the list, or use From Device"
                },
                colors = ButtonDefaults.buttonColors(containerColor = NC.red)
            ) { Text("Import Project  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}
