package com.humale.yaar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4C9AFF), background = Color(0xFF15161B), surface = Color(0xFF15161B))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private val PERMS = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val voice = remember { VoiceIO(ctx) }
    val brain = remember { Brain(ctx) }
    val msgs = remember { mutableStateListOf<Pair<Boolean, String>>() }
    val listState = rememberLazyListState()
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("ne-NP") }
    var showSettings by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { permLauncher.launch(PERMS) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    fun send(text: String) {
        if (text.isBlank()) return
        msgs.add(true to text)
        thinking = true
        scope.launch {
            val r = brain.ask(text)
            thinking = false
            msgs.add(false to r)
            voice.speak(r)
        }
    }

    fun micTap() {
        if (listening) { voice.stopListening(); listening = false; partial = ""; return }
        voice.stopSpeaking()
        listening = true
        voice.listen(lang,
            onPartial = { partial = it },
            onFinal = { listening = false; partial = ""; send(it) },
            onError = { listening = false; partial = "" })
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("हुमाले यार", fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { lang = if (lang == "ne-NP") "en-US" else "ne-NP" }) {
                Text(if (lang == "ne-NP") "नेपाली" else "English")
            }
            TextButton(onClick = { showSettings = true }) { Text("सेटिङ") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) item {
                Text("माइक थिचेर बोल्नुस्, जस्तै \"आमालाई फोन गर\" वा \"टर्च बाल\"।", color = Color.Gray, modifier = Modifier.padding(top = 24.dp))
            }
            items(msgs) { (mine, t) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Color(0xFF1E6FD9) else Color(0xFF2B2D36)).padding(12.dp)
                    ) { Text(t) }
                }
            }
            if (thinking) item { Text("सोचिरहेको छु…", color = Color.Gray) }
        }

        if (partial.isNotBlank()) Text(partial, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("यहाँ लेख्नुस्…") }, maxLines = 3
            )
            TextButton(onClick = { send(input); input = "" }) { Text("पठाउ") }
        }

        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(84.dp).clip(CircleShape)
                    .background(if (listening) Color(0xFFE5484D) else Color(0xFF1E6FD9))
                    .clickable { micTap() },
                contentAlignment = Alignment.Center
            ) { Text(if (listening) "⏹" else "🎙", fontSize = 32.sp) }
        }
    }

    if (showSettings) SettingsDialog(onClose = { showSettings = false }, onClear = { brain.reset(); msgs.clear() })
}

@Composable
fun SettingsDialog(onClose: () -> Unit, onClear: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Store.apiKey(ctx)) }
    var model by remember { mutableStateOf(Store.model(ctx)) }
    var wake by remember { mutableStateOf(Store.wake(ctx)) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("सेटिङ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(key, { key = it }, label = { Text("Claude API key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("मोडेल") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\"हे यार\" वेक वर्ड", modifier = Modifier.weight(1f))
                    Switch(checked = wake, onCheckedChange = { wake = it })
                }
                TextButton(onClick = onClear) { Text("कुराकानी मेट्नुस्") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.setApiKey(ctx, key); Store.setModel(ctx, model); Store.setWake(ctx, wake)
                val i = Intent(ctx, WakeService::class.java)
                if (wake) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
                onClose()
            }) { Text("सेभ") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("रद्द") } }
    )
}
