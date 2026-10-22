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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun LabScreen(effects: MutableList<NeoEffect>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val gl = remember { GlEngine().also { it.setPhoto(testCard()) } }
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var testFx by remember { mutableStateOf<NeoEffect?>(null) }
    val vals = remember(testFx) {
        mutableStateMapOf<String, Float>().apply {
            testFx?.params?.forEach { put(it.id, it.def) }
        }
    }
    remember {
        gl.onError = { m -> err = m }
        0
    }

    val fx0 = testFx
    val chain: List<Pass> = if (fx0 == null) emptyList() else listOf(Pass(fx0, vals.toMap()))
    SideEffect {
        gl.chain = chain
        gl.timeSec = -1f
    }

    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun doTest(src: String) {
        err = ""
        try {
            val fx = NeoFx.parse(src, false)
            gl.retry()
            testFx = fx
            msg = "Testing: " + fx.name
        } catch (e: Exception) {
            msg = "❌ " + (e.message ?: "invalid effect")
            testFx = null
        }
    }

    fun doSave() {
        try {
            val fx = EffectStore.saveImported(ctx, text)
            effects.removeAll { it.id == fx.id && !it.builtIn }
            effects.add(fx)
            msg = "✅ Saved to your library: " + fx.name
        } catch (e: Exception) {
            msg = "❌ " + (e.message ?: "could not save")
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                text = t
                doTest(t)
            } catch (e: Exception) {
                msg = "❌ " + (e.message ?: "could not read file")
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TopBar("Effect Lab", onBack) }
        item {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().height(230.dp).padding(vertical = 4.dp)
            ) {
                GlPreview(gl, Modifier.fillMaxSize())
            }
        }
        val f = testFx
        if (f != null) {
            for (p in f.params) {
                item {
                    Column {
                        Text(p.name + ": " + String.format("%.2f", vals[p.id] ?: p.def), color = Color.White)
                        Slider(
                            value = (vals[p.id] ?: p.def).coerceIn(p.min, maxOf(p.max, p.min + 0.01f)),
                            onValueChange = { vals[p.id] = it },
                            valueRange = p.min..maxOf(p.max, p.min + 0.01f)
                        )
                    }
                }
            }
        }
        if (err.isNotEmpty()) {
            item { Text("Shader error:\n" + err, color = Color(0xFFFF6B6B), fontSize = 11.sp) }
        }
        if (msg.isNotEmpty()) {
            item { Text(msg, color = Color.White) }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 8,
                label = { Text("Effect JSON (paste here)") }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (t.isNullOrBlank()) {
                        msg = "❌ Clipboard is empty"
                    } else {
                        text = t
                        doTest(t)
                    }
                }) { Text("Paste + test") }
                Button(onClick = { doTest(text) }) { Text("Test") }
                Button(onClick = { doSave() }) { Text("Save") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Import file") }
                OutlinedButton(onClick = {
                    cm.setPrimaryClip(ClipData.newPlainText("NeoFx prompt", AI_PROMPT))
                    msg = "AI prompt copied. Paste it in any AI and describe your effect at the end."
                }) { Text("Copy AI prompt") }
            }
        }
        if (err.isNotEmpty() || msg.startsWith("❌")) {
            item {
                OutlinedButton(onClick = {
                    val why = if (err.isNotEmpty()) err else msg
                    cm.setPrimaryClip(ClipData.newPlainText("NeoFx fix", NeoFx.fixPrompt(text, why)))
                    msg = "Fix prompt copied. Paste it to the AI, it will return a corrected effect."
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy fix request for the AI") }
            }
        }
        item { Text("Library (" + effects.size + ")  ·  tap Test to preview, Edit to load its JSON", color = Color(0xFF9A9AB0), fontSize = 12.sp) }
        items(effects.toList()) { e ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2A))
            ) {
                Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(e.category + (if (e.builtIn) " · built-in" else " · imported"), color = Color(0xFFB8B8D0), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val j = NeoFx.toJson(e)
                        text = j
                        doTest(j)
                    }) { Text("Edit") }
                    if (!e.builtIn) {
                        TextButton(onClick = {
                            EffectStore.delete(ctx, e.id)
                            effects.remove(e)
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
