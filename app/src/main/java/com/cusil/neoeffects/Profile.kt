@file:OptIn(ExperimentalMaterial3Api::class)

package com.cusil.neoeffects

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NC.red,
    unfocusedBorderColor = NC.border,
    cursorColor = NC.red,
    focusedLabelColor = NC.red,
    unfocusedLabelColor = NC.sub,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

@Composable
fun ProfileScreen(effects: List<NeoEffect>, onPro: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    var user by remember { mutableStateOf(AuthStore.current(ctx)) }
    var signUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                ProChip(onPro)
            }
        }
        val u = user
        if (u != null) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.neo_logo), "NEOeffects",
                        Modifier.size(96.dp).clip(CircleShape).border(2.dp, NC.red, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(u.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(u.second, color = NC.sub, fontSize = 13.sp)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Projects", ProjectStore.list(ctx).size.toString(), Modifier.weight(1f))
                    StatBox("My effects", effects.count { !it.builtIn }.toString(), Modifier.weight(1f))
                }
            }
            item { RowCard("👑  NEOeffects Pro", "Coming Soon!") { onPro() } }
            item { RowCard("⚙️  Settings", "Version, about") { onSettings() } }
            item {
                OutlinedButton(onClick = {
                    AuthStore.signOut(ctx)
                    user = null
                }, modifier = Modifier.fillMaxWidth()) { Text("Sign out", color = NC.red) }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NC.card2)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (!signUp) NC.red else Color.Transparent)
                            .clickable { signUp = false; err = "" }.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Sign in", color = Color.White, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (signUp) NC.red else Color.Transparent)
                            .clickable { signUp = true; err = "" }.padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Sign up", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            if (signUp) {
                item {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
            item {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
            if (signUp) {
                item {
                    OutlinedTextField(
                        value = pass2, onValueChange = { pass2 = it }, label = { Text("Confirm password") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
            if (err.isNotEmpty()) {
                item { Text(err, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
            }
            item {
                Button(
                    onClick = {
                        val e = if (signUp) AuthStore.signUp(ctx, name, email, pass, pass2) else AuthStore.signIn(ctx, email, pass)
                        if (e == null) {
                            err = ""
                            pass = ""
                            pass2 = ""
                            user = AuthStore.current(ctx)
                        } else {
                            err = e
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NC.red)
                ) { Text(if (signUp) "Create account" else "Sign in", fontWeight = FontWeight.Bold, color = Color.White) }
            }
            item {
                Text(
                    "Accounts are saved on this phone for now. Online login with cloud backup comes in a later update.",
                    color = NC.sub, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(NC.card).border(1.dp, NC.border, shape).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = NC.sub, fontSize = 12.sp)
    }
}

@Composable
fun RowCard(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(NC.card).border(1.dp, NC.border, shape)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(sub, color = NC.sub, fontSize = 12.sp)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RowCard("App version", ver) { }
            RowCard("Update alerts", "Coming soon") { }
            RowCard("Cloud backup", "Coming soon") { }
            Text("NEOeffects · Edit · Create · Inspire", color = NC.sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun TutorialScreen(onBack: () -> Unit) {
    val steps = listOf(
        "1. Create a project" to "Tap New Project, pick the aspect ratio, frame rate and resolution, then Create Project.",
        "2. Add your clips" to "Use Add Media in the bottom panel. Videos and photos both work. Tap a clip in the timeline to select it.",
        "3. Cut and trim" to "Move the playhead (drag the ruler), then tap Split. Use the Speed tool to trim and change speed.",
        "4. Add effects" to "Open the Effects tab and tap an effect. Change its sliders. Tap the key button to animate a setting over time.",
        "5. Make your own effect" to "Home > Import Effects. Tap Copy AI prompt, paste it in any AI, describe the effect, then paste the answer back.",
        "6. Templates" to "Pick a template, then add clips. Its effects are applied for you and you can still edit them.",
        "7. Remove background" to "Home > Remove BG. Pick a photo, let the AI cut it out, then fix edges with the Keep and Erase brushes. Save as PNG.",
        "8. Save and share projects" to "Projects save automatically. In the editor menu you can share a .neoproj file and open it from Import Project."
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { TopBar("Tutorial", onBack) }
        items(steps.size) { i ->
            val shape = RoundedCornerShape(14.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(NC.card).border(1.dp, NC.border, shape).padding(14.dp)) {
                Text(steps[i].first, color = NC.red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(steps[i].second, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

val soonInfo = mapOf(
    "quality" to Pair("Quality Boost", "Boost FPS (0-120), enhance quality Topaz-style and pick the output from 320p to 4K."),
    "color" to Pair("Color Grading", "After Effects style color grading tools.")
)

@Composable
fun SoonScreen(key: String, onBack: () -> Unit) {
    val info = soonInfo[key] ?: Pair("Coming soon", "")
    Column(Modifier.fillMaxSize()) {
        TopBar(info.first, onBack)
        Column(Modifier.padding(24.dp)) {
            Text(info.first, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(info.second, color = NC.sub)
            Spacer(Modifier.height(16.dp))
            Text("Coming Soon!", color = NC.red, fontWeight = FontWeight.Bold)
        }
    }
}
