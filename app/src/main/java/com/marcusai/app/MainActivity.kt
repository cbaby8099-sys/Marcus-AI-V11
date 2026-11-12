package com.marcusai.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import java.util.UUID

private const val WORKER_URL = "https://marcus-ai.cbaby8099.workers.dev"
private val VoidBlack = Color(0xFF080D14)
private val Crimson = Color(0xFF31C8F5)
private val MutedGold = Color(0xFF9AAFC2)
private val Panel = Color(0xFF121C28)

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
    var userId by remember {
        mutableStateOf(
            prefs.getString("userId", null)?.takeIf { it.isNotBlank() }
                ?: ("MRC-" + UUID.randomUUID().toString().uppercase(Locale.US).replace("-", "").take(20)).also {
                    prefs.edit().putString("userId", it).apply()
                }
        )
    }
    var idDraft by remember { mutableStateOf(userId) }
    var message by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("I HAVE AWAKENED. SPEAK.") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf(context.getSharedPreferences("marcus_config_v15", 0).getString("theme", "Eternal Darkness") ?: "Eternal Darkness") }
    val palette = remember(themeName) { MarcusThemePalette.from(themeName) }
    var showAppManager by remember { mutableStateOf(false) }
    var showGaming by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    var recognizer by remember {
        mutableStateOf(
            runCatching {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SpeechRecognizer.createSpeechRecognizer(context)
                } else null
            }.getOrNull()
        )
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = engine
        engine.language = Locale.US
        engine.setPitch(0.78f)
        engine.setSpeechRate(context.getSharedPreferences("marcus_config_v15", 0).getFloat("speech_rate", 0.90f).coerceIn(0.70f, 1.20f))
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun startMarcusListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listening = false
            answer = "Microphone permission is not granted. Allow it in Android App info > Permissions."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listening = false
            answer = "Speech recognition is unavailable. Enable a speech recognition provider in Android settings."
            return
        }
        val speech = recognizer
        if (speech == null) {
            listening = false
            answer = "Marcus could not create a speech session. Close and reopen Marcus, then try again."
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            // Avoid cancelling a previous session immediately before starting another;
            // that cancel/start race can destabilize some Android speech providers.
            listening = true
            speech.startListening(intent)
        } catch (e: RuntimeException) {
            listening = false
            answer = "Microphone could not start (${e.javaClass.simpleName}). Check Android microphone access and speech services."
            runCatching { speech.cancel() }
            runCatching { speech.destroy() }
            recognizer = null
        } catch (e: Exception) {
            listening = false
            answer = "Microphone error: ${e.message ?: "speech service unavailable"}"
            runCatching { speech.cancel() }
            runCatching { speech.destroy() }
            recognizer = null
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMarcusListening()
        else {
            listening = false
            answer = "Microphone permission is off. Allow it in Android App info > Permissions > Microphone."
        }
    }

    fun requestMarcusMic() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startMarcusListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        val host = context as? android.app.Activity
        if (host?.intent?.getBooleanExtra("start_voice", false) == true) {
            host.intent.removeExtra("start_voice")
            requestMarcusMic()
        }
    }

    DisposableEffect(recognizer) {
        val activeRecognizer = recognizer
        if (activeRecognizer != null) {
            runCatching {
                activeRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listening = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening = false }
                    override fun onError(error: Int) {
                        listening = false
                        answer = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check that another app is not using the microphone."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing. Enable it in Android App info."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech provider network error. Check your connection or enable an offline speech pack."
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the microphone and speak again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy. Wait a moment and try again."
                            SpeechRecognizer.ERROR_CLIENT -> "Speech session stopped. Tap the microphone to start a fresh session."
                            else -> "Speech recognition error ($error). Try again."
                        }
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                            runCatching {
                                recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
                                    SpeechRecognizer.createSpeechRecognizer(context)
                                } else null
                            }.onFailure {
                                recognizer = null
                                answer = "Speech service needs to be restarted. Close and reopen Marcus."
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!spoken.isNullOrBlank()) sendMessage(spoken, userId, history, { history = it }, { answer = it }, { busy = it }, scope, tts, context)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }.onFailure {
                listening = false
                answer = "Android speech service could not attach to Marcus. Close and reopen the app."
            }
        }
        onDispose {
            if (activeRecognizer != null) {
                runCatching { activeRecognizer.cancel() }
                runCatching { activeRecognizer.destroy() }
            }
        }
    }

    val currentUserId by rememberUpdatedState(userId)
    val currentHistory by rememberUpdatedState(history)
    val currentTts by rememberUpdatedState(tts)
    val wakeReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(receiverContext: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    VoiceWakeService.ACTION_WAKE -> {
                        chatMode = false
                        answer = "I heard you. Speak your command."
                    }
                    VoiceWakeService.ACTION_COMMAND -> {
                        val spoken = intent.getStringExtra(VoiceWakeService.EXTRA_TEXT).orEmpty()
                        if (spoken.isNotBlank()) {
                            sendMessage(
                                spoken, currentUserId, currentHistory,
                                { history = it }, { answer = it }, { busy = it },
                                scope, currentTts, context
                            )
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(wakeReceiver) {
        val filter = IntentFilter().apply {
            addAction(VoiceWakeService.ACTION_WAKE)
            addAction(VoiceWakeService.ACTION_COMMAND)
        }
        ContextCompat.registerReceiver(
            context, wakeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(wakeReceiver) } }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = palette.primary,
        background = palette.background,
        surface = palette.surface,
        onSurface = Color.White
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(palette.gradientEnd, palette.background, palette.surface))
            )
        ) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MARCUS", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("PERSONAL AI  •  V100.1", color = palette.primary, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    }
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            if (busy) "THINKING" else if (listening) "LISTENING" else "ONLINE",
                            color = palette.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { showAppManager = !showAppManager; showSettings = false; showGaming = false; showMemory = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("APPS", color = palette.accent, fontSize = 10.sp) }
                    TextButton(
                        onClick = { showGaming = !showGaming; showSettings = false; showAppManager = false; showMemory = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("GAMES", color = palette.accent, fontSize = 10.sp) }
                    TextButton(
                        onClick = { showMemory = !showMemory; showSettings = false; showAppManager = false; showGaming = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("MEMORY", color = palette.accent, fontSize = 10.sp) }
                    TextButton(
                        onClick = { showSettings = !showSettings; showAppManager = false; showGaming = false; showMemory = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("SETTINGS", color = palette.accent, fontSize = 10.sp) }
                }

                if (showGaming) {
                    MarcusGamingScreen(palette = palette, onClose = { showGaming = false }, onAskMarcus = { prompt ->
                        sendMessage(prompt, userId, history, { history = it }, { answer = it }, { busy = it }, scope, tts, context)
                    })
                }

                if (showMemory) {
                    MarcusMemoryScreen(palette = palette, onClose = { showMemory = false })
                }

                if (showAppManager) {
                    MarcusAppManagerScreen(onClose = { showAppManager = false })
                }

                if (showSettings) {
                    MarcusSettingsScreen(userId = userId, onUserIdChange = { newId ->
                        userId = newId
                        prefs.edit().putString("userId", newId).apply()
                    }, onClose = {
                        showSettings = false
                        themeName = context.getSharedPreferences("marcus_config_v15", 0).getString("theme", "Eternal Darkness") ?: "Eternal Darkness"
                        answer = "Marcus configuration loaded."
                    })
                }


                Card(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (Settings.canDrawOverlays(context)) {
                                    context.startService(Intent(context, FloatingAssistantService::class.java))
                                    answer = "Floating Marcus shortcut started."
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text("FLOAT", fontSize = 10.sp) }
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.startForegroundService(context, Intent(context, VoiceWakeService::class.java))
                                    answer = "Wake listener requested. Android will show its microphone notification."
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text("HEY MARCUS", fontSize = 10.sp) }
                        OutlinedButton(
                            onClick = {
                                context.stopService(Intent(context, VoiceWakeService::class.java))
                                context.stopService(Intent(context, FloatingAssistantService::class.java))
                                answer = "Background voice and floating shortcut stopped."
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text("STOP", fontSize = 10.sp) }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = chatMode, onClick = { chatMode = true }, label = { Text("CHAT") })
                    FilterChip(selected = !chatMode, onClick = { chatMode = false }, label = { Text("VOICE") })
                    Spacer(Modifier.weight(1f))
                    Text(if (busy) "CONNECTING…" else if (listening) "LISTENING…" else "READY", color = palette.accent, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }

                if (!chatMode) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        MarcusRobotFace(palette = palette, listening = listening)
                        Spacer(Modifier.height(18.dp))
                        Text("THE ENTITY IS AWAKE", color = palette.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(answer, color = Color.White, modifier = Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp)).padding(16.dp))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                                            requestMarcusMic()
                        }, Modifier.fillMaxWidth()) { Text(if (listening) "LISTENING…" else "SPEAK TO MARCUS") }
                        OutlinedButton(onClick = { tts?.stop() }, Modifier.fillMaxWidth()) { Text("STOP VOICE") }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (history.isEmpty()) item {
                            Text("Welcome. Start a conversation with Marcus.", color = Color.LightGray, modifier = Modifier.padding(12.dp))
                        }
                        items(history) { line ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                                Text(line, color = Color.White, modifier = Modifier.padding(13.dp))
                            }
                        }
                        if (busy) item { Text("MARCUS IS THINKING…", color = palette.accent) }
                    }
                    OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Message Marcus…") }, maxLines = 4)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            requestMarcusMic()
                        }) { Text("🎙", color = palette.accent) }
                        Button(onClick = {
                            val outgoing = message
                            if (outgoing.isNotBlank()) {
                                sendMessage(outgoing, userId, history, { history = it }, { answer = it }, { busy = it }, scope, tts, context)
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
    tts: TextToSpeech?,
    context: android.content.Context
) {
    if (userId.isBlank()) {
        updateAnswer("Open Settings and enter your Marcus user ID first.")
        return
    }
    if (text.isBlank()) return
    val command = text.trim().lowercase(Locale.US).replace(Regex("[.!?]+$"), "")
    if (command == "clear chat" || command == "clear conversation" || command == "marcus clear chat") {
        android.app.AlertDialog.Builder(context)
            .setTitle("Clear Marcus chat?")
            .setMessage("This clears the conversation shown in this app on this device. It does not erase any separate cloud records.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CLEAR CHAT") { _, _ ->
                updateHistory(emptyList())
                updateAnswer("Conversation cleared. Ready for a fresh start.")
                updateBusy(false)
            }
            .show()
        return
    }
    val deviceResult = handleMarcusDeviceCommand(context, text)
    if (deviceResult != null) {
        updateHistory(history + listOf("YOU: $text", "MARCUS: $deviceResult"))
        updateAnswer(deviceResult)
        tts?.speak(deviceResult, TextToSpeech.QUEUE_FLUSH, null, "marcus_device_action")
        return
    }
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
                    put("history", JSONArray(updated.takeLast(12)))
                    put("responseMode", "fast")
                    put("maxOutputTokens", 700)
                    put("systemPrompt", "You are Marcus, a capable, accurate and direct assistant with a dark, cinematic fictional persona. Be concise by default, answer the actual request first, use clear steps when needed, avoid filler, and ask only essential clarifying questions. Be honest about tool access and uncertainty. Never claim an action succeeded unless verified.")
                }
                var finalReply: String? = null
                var finalError = "The AI server could not be reached. Check the connection and retry."
                for (attempt in 0..1) {
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 10000
                            readTimeout = 30000
                            doOutput = true
                            useCaches = false
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
                        }
                        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        val status = connection.responseCode
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (status in 500..599 && attempt == 0) {
                            finalError = "Marcus server is temporarily unavailable (HTTP $status). Retrying once."
                            Thread.sleep(700L)
                            continue
                        }
                        if (status !in 200..299) {
                            finalReply = when (status) {
                                401, 403 -> "Marcus server rejected the request (HTTP $status). The server configuration may need attention."
                                429 -> "Marcus AI is receiving too many requests. Wait a moment, then try again."
                                in 500..599 -> "Marcus AI server is temporarily unavailable (HTTP $status). Please try again shortly."
                                else -> "Marcus server returned HTTP $status. Please try again."
                            }
                            break
                        }
                        val json = runCatching { JSONObject(raw) }.getOrNull()
                        finalReply = json?.optString("reply")?.takeIf { it.isNotBlank() && it != "null" }
                            ?: json?.optString("response")?.takeIf { it.isNotBlank() && it != "null" }
                            ?: json?.optString("message")?.takeIf { it.isNotBlank() && it != "null" }
                            ?: if (raw.isNotBlank()) raw else "Marcus returned an empty response. Please retry."
                        break
                    } catch (e: java.net.SocketTimeoutException) {
                        finalError = if (attempt == 0) "Marcus AI took too long to respond. Retrying once." else "Marcus AI timed out twice. Your internet may be fine; the AI server may be slow. Tap Send to try again."
                        if (attempt == 0) Thread.sleep(700L)
                    } catch (e: java.io.IOException) {
                        finalError = if (attempt == 0) "Could not reach Marcus AI server. Retrying once." else "Could not reach Marcus AI server after two attempts. Check the server or try again shortly."
                        if (attempt == 0) Thread.sleep(700L)
                    } finally {
                        connection?.disconnect()
                    }
                }
                finalReply ?: finalError
            } catch (e: Exception) {
                "Marcus request failed: ${e.message ?: "Unknown network error"}. Please try again."
            }
        }
        updateAnswer(response)
        updateHistory(updated + "MARCUS: $response")
        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "marcus_reply")
        updateBusy(false)
    }
}


private fun handleMarcusDeviceCommand(context: android.content.Context, raw: String): String? {
    val command = raw.lowercase(Locale.US).trim().replace(Regex("[.!?]+$"), "")
    val packageTargets = listOf(
        Triple("whatsapp", "com.whatsapp", "WhatsApp"),
        Triple("tiktok", "com.zhiliaoapp.musically", "TikTok"),
        Triple("chrome", "com.android.chrome", "Chrome"),
        Triple("youtube", "com.google.android.youtube", "YouTube"),
        Triple("play store", "com.android.vending", "Google Play Store"),
        Triple("metatrader 5", "net.metaquotes.metatrader5", "MetaTrader 5"),
        Triple("mt5", "net.metaquotes.metatrader5", "MetaTrader 5")
    )
    val target = packageTargets.firstOrNull { command == "open ${it.first}" || command == "launch ${it.first}" || command == "start ${it.first}" }
    if (target != null) {
        val launch = context.packageManager.getLaunchIntentForPackage(target.second)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            return "Opening ${target.third}."
        }
        return "${target.third} is not installed or Android did not expose its launch activity."
    }
    val appPrefix = listOf("open ", "launch ", "start ").firstOrNull { command.startsWith(it) }
    if (appPrefix != null) {
        val requested = command.removePrefix(appPrefix).trim()
        if (requested.isNotBlank()) {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val matches = runCatching { context.packageManager.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
            val app = matches.firstOrNull {
                it.loadLabel(context.packageManager).toString().lowercase(Locale.US) == requested
            } ?: matches.firstOrNull {
                it.loadLabel(context.packageManager).toString().lowercase(Locale.US).contains(requested)
            }
            if (app != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return "Opening ${app.loadLabel(context.packageManager)}."
                }
            }
        }
    }
    val typedText = when {
        command.startsWith("type ") -> raw.trim().substringAfter(" ", "")
        command.startsWith("enter text ") -> raw.trim().substringAfter("enter text ", "")
        else -> null
    }
    if (typedText != null) {
        if (typedText.isBlank()) return "Tell me what text to type, then select an editable field in the app."
        return if (MarcusAccessibilityService.typeTextIntoFocusedField(typedText)) {
            "Typed the text into the focused field. I have not pressed Send or submitted the form."
        } else {
            "I couldn't type there. Enable Marcus Control in Accessibility, tap an editable text field first, and make sure it isn't a password field."
        }
    }
    return when (command) {
        "what's on my screen", "whats on my screen", "read my screen", "describe my screen" -> {
            val visible = MarcusAccessibilityService.readVisibleScreen()
            if (visible.isBlank()) "I couldn't read visible screen text. Enable Marcus Control in Android Accessibility settings, then try again."
            else "Visible screen text (read only):\n$visible"
        }
        "open settings", "open phone settings", "launch settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening Android Settings."
        }
        "open wi-fi settings", "open wifi settings", "open wi fi settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening Wi-Fi settings."
        }
        "open bluetooth settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening Bluetooth settings."
        }
        "open display settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening display settings."
        }
        "open sound settings" -> {
            context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opening sound settings."
        }
        "go home", "press home", "show home screen" -> {
            if (MarcusAccessibilityService.perform(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)) "Going to the Home screen."
            else "Phone navigation is not enabled yet. Open Android Settings > Accessibility > Installed apps > Marcus Control, then enable it."
        }
        "go back", "press back" -> {
            if (MarcusAccessibilityService.perform(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) "Going back."
            else "Phone navigation is not enabled yet. Enable Marcus Control in Android Settings > Accessibility > Installed apps."
        }
        "show recent apps", "open recent apps" -> {
            if (MarcusAccessibilityService.perform(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) "Showing recent apps."
            else "Phone navigation is not enabled yet. Enable Marcus Control in Android Settings > Accessibility > Installed apps."
        }
        "show notifications", "open notifications" -> {
            if (MarcusAccessibilityService.perform(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) "Opening notifications."
            else "Enable Marcus Control in Android Settings > Accessibility > Installed apps first."
        }
        "open quick settings", "show quick settings" -> {
            if (MarcusAccessibilityService.perform(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) "Opening Quick Settings."
            else "Enable Marcus Control in Android Settings > Accessibility > Installed apps first."
        }
        "open camera", "launch camera" -> {
            val camera = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (camera.resolveActivity(context.packageManager) != null) {
                context.startActivity(camera)
                "Opening Camera."
            } else "No camera app is available."
        }
        "open phone", "open dialer", "open calls" -> launchDeviceIntent(context, Intent(Intent.ACTION_DIAL), "Phone dialer")
        "open messages", "open sms" -> launchDeviceIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "Messages")
        "open maps", "open navigation" -> launchDeviceIntent(context, Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")), "Maps")
        "open clock", "open alarm" -> launchDeviceIntent(context, Intent("android.intent.action.SHOW_ALARMS"), "Clock")
        "open calendar" -> launchDeviceIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), "Calendar")
        else -> null
    }
}


private fun launchDeviceIntent(context: android.content.Context, intent: Intent, label: String): String {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (intent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(intent) }
            .fold({ "Opening $label." }, { "$label could not be opened on this device." })
    } else "$label is not available on this device."
}


@Composable
private fun MarcusRobotFace(palette: MarcusPalette, listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "marcus_eye_glow")
    val glow = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
        label = "eye_glow"
    )
    Box(
        Modifier.size(190.dp).background(
            Brush.radialGradient(listOf(palette.primary.copy(alpha = 0.42f), palette.surface, palette.background)),
            RoundedCornerShape(100.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.width(12.dp).height(10.dp).background(palette.primary.copy(alpha = glow.value), RoundedCornerShape(5.dp)))
            Box(
                Modifier.padding(top = 5.dp).width(126.dp).height(108.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF34495B), Color(0xFF111C28))), RoundedCornerShape(30.dp))
                    .padding(horizontal = 17.dp, vertical = 25.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(2) {
                        Box(Modifier.weight(1f).height(17.dp).background(palette.accent.copy(alpha = glow.value), RoundedCornerShape(10.dp)))
                    }
                }
            }
            Box(Modifier.padding(top = 7.dp).width(46.dp).height(5.dp).background(palette.primary.copy(alpha = if (listening) 1f else 0.65f), RoundedCornerShape(4.dp)))
        }
    }
}
