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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

fun fmtClock(ms: Long): String {
    val s = ms / 1000L
    return String.format("%02d:%02d", s / 60L, s % 60L)
}

class QuickDef(val key: String, val label: String, val icon: ImageVector)

val QUICK = listOf(
    QuickDef("quality", "Quality\nBoost", Icons.Outlined.HighQuality),
    QuickDef("removebg", "Remove\nBG", Icons.Outlined.ContentCut),
    QuickDef("import_fx", "Import\nEffects", Icons.Outlined.AutoAwesome),
    QuickDef("import_project", "Import\nProject", Icons.Outlined.Folder),
    QuickDef("color", "Color\nGrading", Icons.Outlined.Palette),
    QuickDef("tutorial", "Tutorial", Icons.Outlined.School)
)

@Composable
fun HomeScreen(
    onNewProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onTemplate: (String) -> Unit,
    onNav: (String) -> Unit,
    onPro: () -> Unit,
    onBell: () -> Unit
) {
    val ctx = LocalContext.current
    var infos by remember { mutableStateOf(ProjectStore.list(ctx)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { Hero(onPro, onBell) { onNav("settings") } }
        item { NewProjectCard(onNewProject) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(QUICK) { q -> QuickCard(q) { onNav(q.key) } }
            }
        }
        item { SectionHeader("Recent Projects") { onNav("projects") } }
        item {
            if (infos.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(90.dp)
                        .clip(RoundedCornerShape(14.dp)).background(NC.card)
                        .border(1.dp, NC.border, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No projects yet. Tap New Project to start.", color = NC.sub, fontSize = 13.sp)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(infos.take(8)) { info ->
                        ProjectCard(info, { onOpenProject(info.id) }, {
                            ProjectStore.delete(ctx, info.id)
                            infos = ProjectStore.list(ctx)
                        })
                    }
                }
            }
        }
        item { SectionHeader("Popular Templates") { onNav("templates") } }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(TEMPLATES) { t -> TemplateCard(t, Modifier.width(128.dp)) { onTemplate(t.id) } }
            }
        }
    }
}

@Composable
fun Hero(onPro: () -> Unit, onBell: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val heroBmp = remember {
        try {
            ctx.assets.open("hero.jpg").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
    Box(
        Modifier.fillMaxWidth().height(250.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1C050B), NC.bg)))
            .drawBehind {
                val w = size.width
                val h = size.height
                val a = Path()
                a.moveTo(w * 0.52f, 0f)
                a.lineTo(w, 0f)
                a.lineTo(w, h * 0.85f)
                a.close()
                drawPath(a, Color(0x33E60A35))
                val b = Path()
                b.moveTo(w * 0.70f, 0f)
                b.lineTo(w, 0f)
                b.lineTo(w, h * 0.45f)
                b.close()
                drawPath(b, Color(0x44FF2D55))
                drawLine(Color(0x88FF2D55), Offset(w * 0.40f, h), Offset(w * 0.86f, 0f), strokeWidth = 3f)
                drawLine(Color(0x55FF2D55), Offset(w * 0.30f, h), Offset(w * 0.70f, 0f), strokeWidth = 2f)
            }
    ) {
        if (heroBmp != null) {
            Image(
                heroBmp.asImageBitmap(), null,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.62f),
                contentScale = ContentScale.Crop, alpha = 0.85f
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(NC.bg, Color.Transparent))))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoLogo()
            Spacer(Modifier.weight(1f))
            ProChip(onPro)
            Box(Modifier.size(40.dp)) {
                IconBtn(Icons.Outlined.Notifications, "Notifications") { onBell() }
                Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(NC.red))
            }
            IconBtn(Icons.Outlined.Settings, "Settings") { onSettings() }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp)) {
            Text("Turn Your Ideas Into", color = Color.White, fontSize = 20.sp)
            Row {
                Text("Epic ", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Edits", color = NC.red, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Text("Pro tools  ·  Stunning effects  ·  Limitless creativity", color = NC.sub, fontSize = 11.sp)
        }
    }
}

@Composable
fun NewProjectCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF47081A), Color(0xFF12050A))))
            .border(1.5.dp, Brush.horizontalGradient(listOf(NC.redGlow, NC.redDeep)), shape)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(NC.redGlow, NC.red))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Add, "New", tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("New Project", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Start editing your next masterpiece", color = NC.sub, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ArrowForward, "Go", tint = Color.White)
    }
}

@Composable
fun QuickCard(q: QuickDef, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(84.dp).height(82.dp).clip(shape).background(NC.card)
            .border(1.dp, NC.border, shape).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(q.icon, q.label, tint = NC.red, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(q.label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

@Composable
fun ProjectCard(info: ProjInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
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
    Column(
        Modifier.width(150.dp).clip(shape).background(NC.card)
            .border(1.dp, NC.border, shape).clickable(onClick = onOpen)
    ) {
        Box(
            Modifier.fillMaxWidth().height(120.dp)
                .background(Brush.linearGradient(listOf(NC.redDeep, NC.bg)))
        ) {
            if (thumb != null) {
                Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text(
                fmtClock(info.durMs), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xAA000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(Modifier.padding(start = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                info.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Box {
                IconBtn(Icons.Outlined.MoreVert, "Menu", NC.sub, 28.dp) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = {
                        menu = false
                        onOpen()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false
                        onDelete()
                    })
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
fun TemplateCard(t: TemplateDef, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(NC.card).border(1.dp, NC.border, shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.linearGradient(listOf(t.c1, t.c2)))) {
            Text(
                t.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(t.sub, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(if (t.tag == "Hot") "🔥 Hot" else "✨ New", color = NC.red, fontSize = 11.sp)
        }
    }
}

@Composable
fun NeoBottomBar(selected: String, onNav: (String) -> Unit, onPlus: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(88.dp)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(66.dp)
                .background(NC.card).border(1.dp, NC.border),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Outlined.Home, "Home", selected == "home", Modifier.weight(1f)) { onNav("home") }
            NavItem(Icons.Outlined.VideoLibrary, "Projects", selected == "projects", Modifier.weight(1f)) { onNav("projects") }
            Spacer(Modifier.weight(1f))
            NavItem(Icons.Outlined.GridView, "Templates", selected == "templates", Modifier.weight(1f)) { onNav("templates") }
            NavItem(Icons.Outlined.Person, "Profile", selected == "profile", Modifier.weight(1f)) { onNav("profile") }
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(60.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(NC.redGlow, NC.red)))
                .border(3.dp, NC.bg, CircleShape)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Add, "New project", tint = Color.White, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (selected) NC.red else NC.sub, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) NC.red else NC.sub, fontSize = 11.sp)
    }
}

@Composable
fun TemplatesScreen(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Templates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        Text("Pick one, then add your clips. The effects are applied for you.", color = NC.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(TEMPLATES) { t -> TemplateCard(t, Modifier.fillMaxWidth()) { onPick(t.id) } }
        }
    }
}
