package com.marcusai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val WORKER_URL = "https://marcus-ai.cbaby8099.workers.dev"
private val VoidBlack = Color(0xFF070509)
private val Crimson = Color(0xFFB0002A)
private val MutedGold = Color(0xFFD6A64B)
private val Panel = Color(0xFF19131D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarcusApp() }
    }
}

@Composable
private fun MarcusApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("marcus_user", 0) }
    var userId by remember { mutableStateOf(prefs.getString("userId", "") ?: "") }
    var idDraft by remember { mutableStateOf(userId) }
    var message by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("I HAVE AWAKENED. SPEAK.") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = engine
        engine.language = Locale.US
        engine.setPitch(0.82f)
        engine.setSpeechRate(0.9f)
        onDispose {
            engine.stop()
            engine.shutdown()
            recognizer?.destroy()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            listening = true
            recognizer?.startListening(intent)
        } else {
            answer = "Microphone permission was not granted."
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; answer = "Voice recognition stopped. Try again." }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) sendMessage(spoken, userId, history, { history = it }, { answer = it }, { busy = it }, scope, tts)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.cancel() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Crimson,
        background = VoidBlack,
        surface = Panel,
        onSurface = Color.White
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF26000D), VoidBlack, Color(0xFF080D18)))
            )
        ) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("MARCUS", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                        Text("THE DARK INTELLIGENCE  •  V15", color = Crimson, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    TextButton(onClick = { showSettings = !showSettings }) { Text("⚙", color = MutedGold, fontSize = 22.sp) }
                }

                if (showSettings) {
                    MarcusSettingsScreen(onClose = {
                        showSettings = false
                        answer = "Marcus configuration loaded."
                    })
                }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = chatMode, onClick = { chatMode = true }, label = { Text("CHAT") })
                    FilterChip(selected = !chatMode, onClick = { chatMode = false }, label = { Text("VOICE") })
                    Spacer(Modifier.weight(1f))
                    Text(if (busy) "THINKING…" else if (listening) "LISTENING…" else "ONLINE UI", color = MutedGold, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }

                if (!chatMode) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(Crimson.copy(alpha = 0.55f), Panel, VoidBlack)), RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                            Text("M", color = Color.White, fontSize = 100.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("THE ENTITY IS AWAKE", color = Crimson, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(answer, color = Color.White, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                                listening = true
                                recognizer?.startListening(intent)
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }, Modifier.fillMaxWidth()) { Text(if (listening) "LISTENING…" else "SPEAK TO MARCUS") }
                        OutlinedButton(onClick = { tts?.stop() }, Modifier.fillMaxWidth()) { Text("STOP VOICE") }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (history.isEmpty()) item {
                            Text("Welcome. Start a conversation with Marcus.", color = Color.LightGray, modifier = Modifier.padding(12.dp))
                        }
                        items(history) { line ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Text(line, color = Color.White, modifier = Modifier.padding(13.dp))
                            }
                        }
                        if (busy) item { Text("MARCUS IS THINKING…", color = MutedGold) }
                    }
                    OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Message Marcus…") }, maxLines = 4)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                                listening = true
                                recognizer?.startListening(intent)
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }) { Text("🎙", color = MutedGold) }
                        Button(onClick = {
                            val outgoing = message
                            if (outgoing.isNotBlank()) {
                                sendMessage(outgoing, userId, history, { history = it }, { answer = it; tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "marcus_reply") }, { busy = it }, scope, tts)
                                message = ""
                            }
                        }, Modifier.weight(1f), enabled = !busy && message.isNotBlank()) { Text(if (busy) "SENDING…" else "SEND") }
                    }
                }
                Text("PERSONAL EDITION  •  LOCAL SETTINGS", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 2.dp))
            }
        }
    }
}

private fun sendMessage(
    text: String,
    userId: String,
    history: List<String>,
    updateHistory: (List<String>) -> Unit,
    updateAnswer: (String) -> Unit,
    updateBusy: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    tts: TextToSpeech?
) {
    if (userId.isBlank()) {
        updateAnswer("Open Settings and enter your Marcus user ID first.")
        return
    }
    if (text.isBlank()) return
    val updated = history + "YOU: $text"
    updateHistory(updated)
    updateBusy(true)
    scope.launch {
        val response = withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("action", "chat")
                    put("userId", userId)
                    put("message", text)
                    put("history", JSONArray(updated))
                }
                val connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                json?.optString("reply")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: json?.optString("response")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: json?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: if (raw.isNotBlank()) raw else "Marcus returned an empty response."
            } catch (e: Exception) {
                "Connection error: ${e.message ?: "Please try again."}"
            }
        }
        updateAnswer(response)
        updateHistory(updated + "MARCUS: $response")
        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "marcus_reply")
        updateBusy(false)
    }
}
