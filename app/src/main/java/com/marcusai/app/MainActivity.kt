
package com.marcusai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val WORKER_URL =
    "https://marcus-ai.cbaby8099.workers.dev"

private val BackgroundColor = Color(0xFF050509)
private val BlueColor = Color(0xFF168BFF)
private val RedColor = Color(0xFFB00020)
private val PanelColor = Color(0xFF15121B)

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

    val prefs = remember {
        context.getSharedPreferences("marcus_user_id", 0)
    }

    var userId by remember {
        mutableStateOf(prefs.getString("userId", "") ?: "")
    }

    var idInput by remember { mutableStateOf("") }
    var showIdDialog by remember { mutableStateOf(userId.isBlank()) }
    var idError by remember { mutableStateOf(false) }

    var message by remember { mutableStateOf("") }
    var reply by remember {
        mutableStateOf("Marcus is awake. The connection is waiting.")
    }
    var busy by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var continuousMode by remember { mutableStateOf(false) }
    var micStatus by remember { mutableStateOf("READY") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestContinuous by rememberUpdatedState(continuousMode)
    val latestBusy by rememberUpdatedState(busy)
    val latestUserId by rememberUpdatedState(userId)
    val latestMessages by rememberUpdatedState(messages)
    val latestTts by rememberUpdatedState(tts)
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    var startRecognizer by remember { mutableStateOf<(() -> Unit)?>(null) }
    val recognitionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                engine.language = Locale.UK
                engine.setSpeechRate(1.05f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { micStatus = "MARCUS IS SPEAKING..." }
                    }
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (latestContinuous && !latestBusy) {
                                micStatus = "LISTENING..."
                                mainHandler.postDelayed({ startRecognizer?.invoke() }, 400L)
                            } else if (!latestContinuous) micStatus = "READY"
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (latestContinuous && !latestBusy) mainHandler.postDelayed({ startRecognizer?.invoke() }, 800L)
                        }
                    }
                })
            }
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    startRecognizer = {
        if (latestContinuous && !latestBusy) {
            if (speechRecognizer == null) {
                micStatus = "SPEECH RECOGNITION UNAVAILABLE"
                continuousMode = false
            } else {
                try { speechRecognizer.startListening(recognitionIntent); micStatus = "LISTENING..." }
                catch (_: Exception) {
                    micStatus = "RESTARTING MICROPHONE..."
                    mainHandler.postDelayed({ startRecognizer?.invoke() }, 900L)
                }
            }
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { mainHandler.post { if (latestContinuous) micStatus = "LISTENING..." } }
            override fun onBeginningOfSpeech() { mainHandler.post { if (latestContinuous) micStatus = "HEARING YOU..." } }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { mainHandler.post { if (latestContinuous) micStatus = "PROCESSING..." } }
            override fun onError(error: Int) {
                mainHandler.post {
                    if (latestContinuous && !latestBusy) {
                        micStatus = "RECONNECTING MICROPHONE..."
                        speechRecognizer?.cancel()
                        val delay = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 500L else 1200L
                        mainHandler.postDelayed({ startRecognizer?.invoke() }, delay)
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                mainHandler.post {
                    if (!spoken.isNullOrBlank() && latestUserId.isNotBlank()) {
                        message = spoken
                        busy = true
                        micStatus = "MARCUS IS THINKING..."
                        scope.launch {
                            val answer = sendToWorker(latestUserId, spoken, latestMessages)
                            reply = answer
                            messages = latestMessages + listOf("You: $spoken", "Marcus: $answer")
                            busy = false
                            val engine = latestTts
                            if (engine != null) engine.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "marcus_reply")
                            else if (latestContinuous) mainHandler.postDelayed({ startRecognizer?.invoke() }, 600L)
                        }
                    } else if (latestContinuous) {
                        mainHandler.postDelayed({ startRecognizer?.invoke() }, 500L)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer?.cancel(); speechRecognizer?.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            continuousMode = true
            micStatus = "STARTING MICROPHONE..."
            mainHandler.postDelayed({ startRecognizer?.invoke() }, 300L)
        } else {
            continuousMode = false
            micStatus = "MICROPHONE PERMISSION DENIED"
        }
    }

    fun stopContinuousListening() {
        continuousMode = false
        speechRecognizer?.cancel()
        tts?.stop()
        micStatus = "MICROPHONE STOPPED"
    }

    fun startListening() {
        if (!userId.matches(Regex("^[a-zA-Z0-9_-]{16,80}$"))) { showIdDialog = true; return }
        if (speechRecognizer == null) { micStatus = "SPEECH RECOGNITION UNAVAILABLE"; return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            continuousMode = true
            micStatus = "STARTING MICROPHONE..."
            startRecognizer?.invoke()
        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                continuousMode = false
                speechRecognizer?.cancel()
                tts?.stop()
                micStatus = "PAUSED — TAP START TO RESUME"
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "MARCUS",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "POSSESSED AI",
                            color = RedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            idInput = ""
                            showIdDialog = true
                        }) {
                            Text("LINK ID", color = RedColor, fontSize = 11.sp)
                        }

                        TextButton(onClick = {
                            chatMode = !chatMode
                        }) {
                            Text(
                                if (chatMode) "VOICE" else "CHAT",
                                color = BlueColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (!chatMode) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "THE ENTITY IS AWAKE",
                            color = RedColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .size(158.dp)
                                .background(
                                    RedColor.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(138.dp)
                                    .background(
                                        BlueColor.copy(alpha = 0.20f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { if (continuousMode) stopContinuousListening() else startListening() },
                                    modifier = Modifier.size(112.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF170D19)
                                    )
                                ) {
                                    Text(
                                        if (continuousMode) "STOP\nM" else "START\nM",
                                        color = Color(0xFFFF334F),
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 34.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            if (busy) "MARCUS IS THINKING..."
                            else micStatus,
                            color = BlueColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = PanelColor
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                reply,
                                modifier = Modifier.padding(14.dp),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { item ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = PanelColor
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    item,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message Marcus...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BlueColor,
                            unfocusedBorderColor = RedColor
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (continuousMode) stopContinuousListening() else startListening() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PanelColor
                            )
                        ) {
                            Text(if (continuousMode) "STOP LISTENING" else "START LISTENING", color = BlueColor)
                        }

                        Button(
                            onClick = {
                                val sent = message.trim()
                                if (userId.isBlank()) {
                                    showIdDialog = true
                                } else if (sent.isNotEmpty() && !busy) {
                                    scope.launch {
                                        busy = true
                                        messages = messages + "You: $sent"
                                        message = ""

                                        val answer = sendToWorker(
                                            userId,
                                            sent,
                                            messages
                                        )

                                        messages = messages + "Marcus: $answer"
                                        reply = answer
                                        tts?.speak(
                                            answer,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "marcus_chat"
                                        )
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueColor
                            )
                        ) {
                            Text("SEND")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        if (userId.isBlank()) {
                            showIdDialog = true
                        } else if (!busy) {
                            scope.launch {
                                busy = true
                                reply = requestWorker(userId, "get_memory")
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BlueColor
                    )
                ) {
                    Text("MEMORY")
                }
            }
        }

        if (showIdDialog) {
            AlertDialog(
                onDismissRequest = { showIdDialog = false },
                title = {
                    Text("CONNECT MARCUS", color = Color.White)
                },
                text = {
                    Column {
                        Text(
                            "Paste your copied V10 ID here. It stays on this device.",
                            color = Color.LightGray
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = idInput,
                            onValueChange = {
                                idInput = it.trim()
                                idError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("V10 User ID") },
                            isError = idError
                        )
                        if (idError) {
                            Text(
                                "That ID format is not valid. Copy it again from ID Finder.",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val cleanId = idInput.trim()
                        if (cleanId.matches(Regex("^[a-zA-Z0-9_-]{16,80}$"))) {
                            userId = cleanId
                            prefs.edit().putString("userId", cleanId).apply()
                            showIdDialog = false
                            idError = false
                            reply = "Marcus is connected to your V10 identity."
                        } else {
                            idError = true
                        }
                    }) {
                        Text("SAVE ID", color = BlueColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showIdDialog = false
                    }) {
                        Text("LATER", color = RedColor)
                    }
                },
                containerColor = PanelColor
            )
        }
    }
}

suspend fun sendToWorker(
    userId: String,
    message: String,
    history: List<String>
): String {
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
            "Connection error: ${e.message}"
        }
    }
}

suspend fun requestWorker(
    userId: String,
    action: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val data = JSONObject().apply {
                put("action", action)
                put("userId", userId)
            }
            postToWorker(data)
        } catch (e: Exception) {
            
            "Memory connection error: ${e.message}"
        }
    }
}

private fun postToWorker(data: JSONObject): String {
    val connection = URL(WORKER_URL)
        .openConnection() as HttpURLConnection

    return try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.useCaches = false
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        connection.outputStream.use {
            it.write(data.toString().toByteArray(Charsets.UTF_8))
        }

        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)

        when {
            json.has("error") ->
                "Connection error: " + json.optString("error")
            json.has("reply") -> json.optString("reply")
            json.has("response") -> json.optString("response")
            else -> response
        }
    } finally {
        connection.disconnect()
    }
}