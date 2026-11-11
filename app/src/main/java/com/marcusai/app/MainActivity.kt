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
import java.util.UUID

private const val WORKER_URL = "https://marcus-ai.cbaby8099.workers.dev"
private val VoidBlack = Color(0xFF070509)
private val Crimson = Color(0xFFB0002A)
private val MutedGold = Color(0xFFD6A64B)
private val Panel = Color(0xFF19131D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarcusApp(startVoice = intent?.getBooleanExtra(MarcusFloatingService.EXTRA_START_VOICE, false) == true) }
    }
}

@Composable
private fun MarcusApp(startVoice: Boolean = false) {
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
    var chatMode by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    var recognizer by remember {
        mutableStateOf(
            if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
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
        val speech = recognizer
        if (speech == null) {
            listening = false
            answer = "Speech recognition is unavailable. Install or enable a speech recognition provider in Android settings."
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speech.cancel()
            listening = true
            speech.startListening(intent)
        } catch (e: Exception) {
            listening = false
            answer = "Could not start microphone: ${e.message ?: "recognition service error"}"
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

    LaunchedEffect(startVoice) {
        if (startVoice) {
            chatMode = false
            android.os.Handler(context.mainLooper).postDelayed({ requestMarcusMic() }, 450L)
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
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
                    recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
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
        onDispose { recognizer?.cancel(); recognizer?.destroy() }
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("MARCUS", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                        Text("ROBOTIC CORE  •  V19", color = palette.primary, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showAppManager = !showAppManager; showSettings = false }) { Text("APPS", color = palette.accent, fontSize = 12.sp) }
                        TextButton(onClick = { showSettings = !showSettings; showAppManager = false }) { Text("⚙", color = palette.accent, fontSize = 22.sp) }
                    }
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

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = chatMode, onClick = { chatMode = true }, label = { Text("CHAT") })
                    FilterChip(selected = !chatMode, onClick = { chatMode = false }, label = { Text("VOICE") })
                    Spacer(Modifier.weight(1f))
                    Text(if (busy) "THINKING…" else if (listening) "LISTENING…" else "ONLINE UI", color = palette.accent, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }

                if (!chatMode) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(palette.primary.copy(alpha = 0.55f), palette.surface, palette.background)), RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                            Text("M", color = Color.White, fontSize = 100.sp, fontWeight = FontWeight.Black)
                        }
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
                val connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 20000
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
    return when (command) {
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
        "open clock", "open alarm" -> launchDeviceIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CLOCK), "Clock")
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
