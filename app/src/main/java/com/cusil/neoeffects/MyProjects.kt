@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        cursorBrush = SolidColor(NC.red),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(NC.card2)
                    .border(1.dp, NC.border, RoundedCornerShape(21.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, "Search", tint = NC.sub, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = NC.dim, fontSize = 13.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
fun ProjGridCard(
    info: ProjInfo, onOpen: () -> Unit, onFav: () -> Unit, onRename: () -> Unit, onDup: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val thumb = remember(info.id, info.modified) {
        try {
            BitmapFactory.decodeFile(ProjectStore.thumbFile(ctx, info.id).path)
        } catch (e: Exception) {
            null
        }
    }
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.clip(shape).background(NC.card).border(1.dp, NC.border, shape).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.linearGradient(listOf(NC.redDeep, NC.bg)))) {
            if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                if (info.fav) "★" else "☆", color = if (info.fav) Color(0xFFFFC21A) else Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0x99000000)).clickable(onClick = onFav).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                fmtClock(info.durMs), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(Modifier.padding(start = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(info.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                IconBtn(Icons.Outlined.MoreVert, "Menu", NC.sub, 30.dp) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDup() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(
            info.res + " · " + info.fps + "fps · " + info.clips + " clips", color = NC.sub, fontSize = 10.sp,
            maxLines = 1, modifier = Modifier.padding(start = 10.dp, end = 6.dp, bottom = 10.dp)
        )
    }
}

@Composable
fun MyProjectsScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    val ctx = LocalContext.current
    var infos by remember { mutableStateOf(ProjectStore.list(ctx)) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(0) }
    var favOnly by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjInfo?>(null) }
    var newName by remember { mutableStateOf("") }

    val filtered = infos.filter { (query.isBlank() || it.name.contains(query, ignoreCase = true)) && (!favOnly || it.fav) }
    val shown = when (sort) {
        1 -> filtered.sortedBy { it.modified }
        2 -> filtered.sortedBy { it.name.lowercase() }
        else -> filtered.sortedByDescending { it.modified }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Projects", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.weight(1f))
            Text(infos.size.toString() + " total", color = NC.sub, fontSize = 12.sp)
        }
        Text("Open, organize and continue your edits", color = NC.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        SearchField(query, "Search projects...", Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { query = it }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeoChip("All", !favOnly) { favOnly = false }
            NeoChip("★ Favorites", favOnly) { favOnly = true }
            Spacer(Modifier.weight(1f))
            NeoChip(
                when (sort) {
                    1 -> "Oldest"
                    2 -> "A–Z"
                    else -> "Newest"
                }, false
            ) { sort = (sort + 1) % 3 }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().height(190.dp).clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A0710), NC.card)))
                        .border(1.5.dp, NC.red, shape).clickable(onClick = onNew),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("+", color = NC.red, fontSize = 40.sp)
                    Text("New Project", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Start from scratch", color = NC.sub, fontSize = 11.sp)
                }
            }
            items(shown) { info ->
                ProjGridCard(
                    info,
                    onOpen = { onOpen(info.id) },
                    onFav = {
                        ProjectStore.setFav(ctx, info.id, !info.fav)
                        infos = ProjectStore.list(ctx)
                    },
                    onRename = {
                        newName = info.name
                        renaming = info
                    },
                    onDup = {
                        ProjectStore.duplicate(ctx, info.id)
                        infos = ProjectStore.list(ctx)
                    },
                    onDelete = {
                        ProjectStore.delete(ctx, info.id)
                        infos = ProjectStore.list(ctx)
                    }
                )
            }
        }
    }

    val rn = renaming
    if (rn != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = NC.card,
            title = { Text("Rename project", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true, colors = fieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) ProjectStore.rename(ctx, rn.id, newName.trim())
                    infos = ProjectStore.list(ctx)
                    renaming = null
                }) { Text("Save", color = NC.red) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = NC.sub) } }
        )
    }
}
