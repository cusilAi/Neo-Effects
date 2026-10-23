package com.cusil.neoeffects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NC {
    val bg = Color(0xFF040507)
    val card = Color(0xFF0C0E15)
    val card2 = Color(0xFF12141D)
    val border = Color(0xFF2A2C3A)
    val red = Color(0xFFE60A35)
    val redGlow = Color(0xFFFF2D55)
    val redDeep = Color(0xFF5A0617)
    val sub = Color(0xFF9B9DB0)
    val dim = Color(0xFF55576A)
}

@Composable
fun NeoLogo(small: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "NEO", color = NC.red, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
                fontSize = if (small) 22.sp else 34.sp, letterSpacing = (-1).sp
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "effects", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = if (small) 16.sp else 26.sp,
                modifier = Modifier.padding(bottom = if (small) 2.dp else 3.dp)
            )
        }
        if (!small) {
            Text("EDIT  ·  CREATE  ·  INSPIRE", color = NC.sub, fontSize = 8.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = Color.White, size: Dp = 40.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

@Composable
fun ProChip(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("👑", fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text("Pro", color = NC.red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun ProDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = NC.red) } },
        title = { Text("NEOeffects Pro 👑", color = Color.White) },
        text = { Text("Coming Soon!\nPro tools are on the way.", color = NC.sub) },
        containerColor = NC.card
    )
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Icons.Outlined.ArrowBack, "Back") { onBack() }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text("See All  ›", color = NC.sub, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSeeAll))
        }
    }
}

@Composable
fun OptionBox(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0xFF2A0A12) else NC.card2)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) NC.red else NC.border, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}
