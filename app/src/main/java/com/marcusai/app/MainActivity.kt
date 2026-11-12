package com.marcusai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
private val Ink = Color(0xFF080B14)
private val Panel = Color(0xFF121A2B)
private val Blue = Color(0xFF58B8FF)
private val Violet = Color(0xFF9A82FF)
private val Muted = Color(0xFF9AA8BE)
private val White = Color(0xFFF3F7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarcusApp() }
    }
}

@Composable
fun MarcusApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("marcus_user_id", 0) }

    // New installs receive a private local app identity automatically.
    // Users can optionally link an existing V10 identity to recover its cloud memory.
    var userId by remember {
        mutableStateOf(
            prefs.getString("userId", null)
                ?: UUID.randomUUID().toString().replace("-", "")
        )
    }
    LaunchedEffect(userId) { prefs.edit().putString("userId", userId).apply() }

    var message by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("Marcus is online. Start a conversation whenever you're ready.") }
    var busy by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var continuousMode by remember { mutableStateOf(false) }
    var micStatus by remember { mutableStateOf("VOICE SYSTEM READY") }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestContinuous by rememberUpdatedState(continuousMode)
    val latestBusy by rememberUpdatedState(busy)
    val latestUserId by rememberUpdatedState(userId)
    val latestMessages by rememberUpdatedState(messages)
    val latestTts by rememberUpdatedState(tts)

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    var startRecognizer by remember { mutableStateOf<(() -> Unit)?>(null) }
    val recognitionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                engine.language = Locale.UK
                engine.setSpeechRate(1.15f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { micStatus = "MARCUS IS SPEAKING" }
                    }
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (latestContinuous && !latestBusy) {
                                micStatus = "LISTENING FOR YOU"
                                mainHandler.postDelayed({ startRecognizer?.invoke() }, 350L)
                            } else if (!latestContinuous) {
                                micStatus = "VOICE SYSTEM READY"
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (latestContinuous && !latestBusy) {
                                mainHandler.postDelayed({ startRecognizer?.invoke() }, 700L)
                            }
                        }
                    }
                })
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    startRecognizer = {
        if (latestContinuous && !latestBusy) {
            if (speechRecognizer == null) {
                micStatus = "SPEECH RECOGNITION UNAVAILABLE"
                continuousMode = false
            } else {
                try {
                    speechRecognizer.startListening(recognitionIntent)
                    micStatus = "LISTENING FOR YOU"
                } catch (_: Exception) {
                    micStatus = "RESTARTING VOICE"
                    mainHandler.postDelayed({ startRecognizer?.invoke() }, 900L)
                }
            }
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                mainHandler.post { if (latestContinuous) micStatus = "LISTENING FOR YOU" }
            }
            override fun onBeginningOfSpeech() {
                mainHandler.post { if (latestContinuous) micStatus = "HEARING YOU" }
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                mainHandler.post { if (latestContinuous) micStatus = "PROCESSING YOUR MESSAGE" }
            }
            override fun onError(error: Int) {
                mainHandler.post {
                    if (latestContinuous && !latestBusy) {
                        micStatus = "RECONNECTING VOICE"
                        speechRecognizer?.cancel()
                        val delay = if (
                            error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) 450L else 1100L
                        mainHandler.postDelayed({ startRecognizer?.invoke() }, delay)
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                mainHandler.post {
                    if (!spoken.isNullOrBlank() && latestContinuous && !latestBusy) {
                        busy = true
                        message = spoken
                        micStatus = "MARCUS IS THINKING"
                        scope.launch {
                            val answer = sendToWorker(latestUserId, spoken, latestMessages)
                            reply = answer
                            messages = (latestMessages + listOf("You: $spoken", "Marcus: $answer")).takeLast(30)
                            busy = false
                            val engine = latestTts
                            if (engine != null) {
                                engine.speak(answer, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                            } else if (latestContinuous) {
                                mainHandler.postDelayed({ startRecognizer?.invoke() }, 500L)
                            }
                        }
                    } else if (latestContinuous && !latestBusy) {
                        mainHandler.postDelayed({ startRecognizer?.invoke() }, 450L)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            continuousMode = true
            micStatus = "STARTING VOICE"
            mainHandler.postDelayed({ startRecognizer?.invoke() }, 300L)
        } else {
            continuousMode = false
            micStatus = "MICROPHONE PERMISSION DENIED"
        }
    }

    fun stopListening() {
        continuousMode = false
        speechRecognizer?.cancel()
        tts?.stop()
        micStatus = "VOICE PAUSED"
    }

    fun startListening() {
        if (speechRecognizer == null) {
            micStatus = "SPEECH RECOGNITION UNAVAILABLE"
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            continuousMode = true
            micStatus = "STARTING VOICE"
            mainHandler.postDelayed({ startRecognizer?.invoke() }, 120L)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun submitMessage() {
        val sent = message.trim()
        if (sent.isNotEmpty() && !busy) {
            busy = true
            messages = (messages + "You: $sent").takeLast(30)
            message = ""
            scope.launch {
                val answer = sendToWorker(userId, sent, messages)
                reply = answer
                messages = (messages + "Marcus: $answer").takeLast(30)
                busy = false
                tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            }
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                continuousMode = false
                speechRecognizer?.cancel()
                tts?.stop()
                micStatus = "PAUSED — RETURN TO RESUME"
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val pulse = rememberInfiniteTransition(label = "marcus-pulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core-scale"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            secondary = Violet,
            background = Ink,
            surface = Panel,
            onPrimary = Ink,
            onBackground = White,
            onSurface = White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF111A30), Ink, Color(0xFF090D18))
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MARCUS", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("PERSONAL AI  •  V1000", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    }
                    RobotAvatar(modifier = Modifier.size(58.dp))
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF17243A), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(7.dp).background(if (busy) Violet else Blue, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (busy) "THINKING" else micStatus,
                        color = White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                if (!chatMode) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("YOUR AI CO-PILOT", color = Muted, fontSize = 11.sp, letterSpacing = 2.2.sp)
                        Spacer(Modifier.height(28.dp))
                        Box(
                            modifier = Modifier.size(226.dp)
                                .background(Blue.copy(alpha = 0.055f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(190.dp)
                                    .background(Violet.copy(alpha = 0.09f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(154.dp)
                                        .graphicsLayer {
                                            val scale = if (continuousMode) pulse.value else 1f
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .background(
                                            Brush.linearGradient(listOf(Blue, Violet, Color(0xFF263F75))),
                                            CircleShape
                                        )
                                        .padding(3.dp)
                                        .background(Color(0xFF10192B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = { if (continuousMode) stopListening() else startListening() },
                                        modifier = Modifier.size(122.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (continuousMode) Color(0xFF542B54) else Color(0xFF183B62)
                                        )
                                    ) {
                                        Text(
                                            if (continuousMode) "STOP" else "TALK",
                                            color = White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        Text(
                            if (continuousMode) "MARCUS IS LISTENING" else "TAP TALK TO START",
                            color = Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.96f))
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("LATEST RESPONSE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(reply, color = White, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { chatMode = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("OPEN CHAT", color = Blue, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    reply = requestWorker(userId, "get_memory")
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !busy
                        ) { Text("MEMORY", color = Violet, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        items(messages) { item ->
                            val isUser = item.startsWith("You:")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) Color(0xFF173454) else Panel
                                )
                            ) {
                                Text(item, modifier = Modifier.padding(14.dp), color = White, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message Marcus...", color = Muted) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = Muted
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (continuousMode) stopListening() else startListening() },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (continuousMode) "STOP VOICE" else "VOICE", color = Violet) }
                        Button(
                            onClick = { submitMessage() },
                            modifier = Modifier.weight(1f),
                            enabled = message.isNotBlank() && !busy
                        ) { Text("SEND") }
                    }
                    TextButton(onClick = { chatMode = false }) { Text("BACK TO VOICE", color = Blue) }
                }
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SET UP PHONE CONTROL  •  ACCESSIBILITY", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("VOICE PAUSES WHEN APP LEAVES FOREGROUND", color = Muted, fontSize = 8.sp, letterSpacing = 0.7.sp)
            }
        }
    }
}

@Composable
private fun RobotAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(Color(0xFF8EDBFF), Color(0xFF647DCE), Color(0xFF272F55))),
                RoundedCornerShape(22.dp)
            )
            .padding(3.dp)
            .background(Color(0xFF111A2B), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(35.dp)
                    .height(3.dp)
                    .background(Blue, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .width(44.dp)
                    .height(31.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFDAE9F5), Color(0xFF758DAA))),
                        RoundedCornerShape(11.dp)
                    )
                    .padding(horizontal = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(Color(0xFF10233C), CircleShape))
                Box(Modifier.size(7.dp).background(Color(0xFF10233C), CircleShape))
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(17.dp)
                    .height(3.dp)
                    .background(Violet, RoundedCornerShape(4.dp))
            )
        }
    }
}

suspend fun sendToWorker(userId: String, message: String, history: List<String>): String {
    return withContext(Dispatchers.IO) {
        try {
            val historyArray = JSONArray()
            history.takeLast(12).forEach { historyArray.put(it) }
            val data = JSONObject().apply {
                put("action", "chat")
                put("userId", userId)
                put("message", message)
                put("history", historyArray)
            }
            postToWorker(data)
        } catch (e: Exception) {
            "Connection error: ${e.message ?: "Please try again."}"
        }
    }
}

suspend fun requestWorker(userId: String, action: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val data = JSONObject().apply {
                put("action", action)
                put("userId", userId)
            }
            postToWorker(data)
        } catch (e: Exception) {
            "Memory connection error: ${e.message ?: "Please try again."}"
        }
    }
}

private fun postToWorker(data: JSONObject): String {
    val connection = URL(WORKER_URL).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 6000
        connection.readTimeout = 12000
        connection.doOutput = true
        connection.useCaches = false
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (response.isBlank()) return "Marcus received an empty response. Please try again."
        val json = JSONObject(response)
        when {
            json.has("error") -> "Connection error: " + json.optString("error")
            json.has("reply") -> json.optString("reply")
            json.has("response") -> json.optString("response")
            else -> response
        }
    } finally {
        connection.disconnect()
    }
}
