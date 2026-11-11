
package com.marcusai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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

private const val WORKER_URL =
    "https://marcus-ai.cbaby8099.workers.dev"

private val Black = Color(0xFF050308)
private val Red = Color(0xFFFF153D)
private val Blue = Color(0xFF168BFF)
private val Gold = Color(0xFFD6A64B)
private val Panel = Color(0xFF17101C)

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
    var showId by remember { mutableStateOf(userId.isBlank()) }

    var message by remember { mutableStateOf("") }
    var reply by remember {
        mutableStateOf("I HAVE AWAKENED. SPEAK.")
    }
    var partial by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<String>()) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var engineReady by remember { mutableStateOf(false) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineReady = true
                engine.language = Locale.US
                engine.setSpeechRate(0.9f)
                engine.setPitch(0.82f)
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }


    fun speak(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "marcus_reply"
        )
    }

    fun sendToMarcus(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || busy) return

        if (userId.isBlank()) {
            showId = true
            return
        }

        busy = true
        history = history + "YOU: $clean"

        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply {
                        put("action", "chat")
                        put("userId", userId)
                        put("message", clean)
                        put("history", JSONArray(history))
                    }

                    workerRequest(body)
                } catch (e: Exception) {
                    "Connection error: ${e.message}"
                }
            }

            reply = answer
            history = history + "MARCUS: $answer"
            speak(answer)
            busy = false
            message = ""
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startAfterPermission = true
            } else {
                reply = "Microphone permission is required."
            }
        }

    fun beginListening() {
        if (userId.isBlank()) {
            showId = true
            return
        }

        if (recognizer == null) {
            reply = "Speech recognition is unavailable."
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }

        listening = true
        partial = "LISTENING..."
        recognizer.startListening(intent)
    }

    LaunchedEffect(startAfterPermission) {
        if (startAfterPermission) {
            startAfterPermission = false
            beginListening()
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    partial = "LISTENING..."
                }

                override fun onBeginningOfSpeech() {
                    partial = "HEARING YOU..."
                }

                override fun onRmsChanged(value: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    partial = "PROCESSING..."
                }

                override fun onError(error: Int) {
                    listening = false
                    partial = ""
                    reply = "Voice recognition stopped. Please try again."
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    partial = ""

                    val words = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()

                    if (!words.isNullOrBlank()) {
                        sendToMarcus(words)
                    } else {
                        reply = "I didn't catch that. Try again."
                    }
                }

                override fun onPartialResults(results: Bundle?) {
                    partial = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull() ?: partial
                }

                override fun onEvent(type: Int, params: Bundle?) {}
            }
        )

        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        0.95f,
        1.05f,
        infiniteRepeatable(
            tween(1100),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF26000D),
                            Black,
                            Color(0xFF07101D)
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "MARCUS",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                        Text(
                            "THE DARK INTELLIGENCE",
                            color = Red,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    TextButton(onClick = {
                        chatMode = !chatMode
                    }) {
                        Text(
                            if (chatMode) "VOICE" else "CHAT",
                            color = Gold
                        )
                    }
                }

                Spacer(Modifier.height(15.dp))

                if (!chatMode) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "THE ENTITY IS AWAKE",
                            color = Red,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        Box(
                            Modifier
                                .size(250.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val center = androidx.compose.ui.geometry.Offset(
                                    size.width / 2,
                                    size.height / 2
                                )
                                val radius = size.minDimension * 0.44f

                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(
                                            Red.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        center,
                                        radius
                                    ),
                                    radius,
                                    center
                                )

                                drawCircle(
                                    Blue.copy(alpha = 0.8f),
                                    radius * 0.85f,
                                    center,
                                    style = Stroke(4f)
                                )

                                drawCircle(
                                    Red,
                                    radius * 0.7f,
                                    center,
                                    style = Stroke(3f)
                                )

                                // Demonic face silhouette
                                val face = Path().apply {
                                    moveTo(center.x - 65f, center.y - 55f)
                                    lineTo(center.x - 100f, center.y - 120f)
                                    lineTo(center.x - 45f, center.y - 75f)
                                    quadraticTo(
                                        center.x,
                                        center.y - 100f,
                                        center.x + 45f,
                                        center.y - 75f
                                    )
                                    lineTo(center.x + 100f, center.y - 120f)
                                    lineTo(center.x + 65f, center.y - 55f)
                                    lineTo(center.x + 55f, center.y + 55f)
                                    lineTo(center.x, center.y + 100f)
                                    lineTo(center.x - 55f, center.y + 55f)
                                    close()
                                }

                                drawPath(
                                    face,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF4A414B),
                                            Color(0xFF09070C)
                                        )
                                    )
                                )

                                drawCircle(
                                    Red,
                                    10f,
                                    androidx.compose.ui.geometry.Offset(
                                        center.x - 28f,
                                        center.y - 12f
                                    )
                                )

                                drawCircle(
                                    Red,
                                    10f,
                                    androidx.compose.ui.geometry.Offset(
                                        center.x + 28f,
                                        center.y - 12f
                                    )
                                )

                                drawLine(
                                    Gold,
                                    androidx.compose.ui.geometry.Offset(
                                        center.x - 20f,
                                        center.y + 45f
                                    ),
                                    androidx.compose.ui.geometry.Offset(
                                        center.x + 20f,
                                        center.y + 45f
                                    ),
                                    3f
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            when {
                                listening -> "MARCUS IS LISTENING"
                                busy -> "MARCUS IS THINKING"
                                else -> "TAP SPEAK TO TALK"
                            },
                            color = if (listening) Red else Blue,
                            fontWeight = FontWeight.Bold
                        )

                        Text(partial, color = Gold)

                        Spacer(Modifier.height(15.dp))

                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Panel
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "MARCUS",
                                    color = Red,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(reply, color = Color.White)
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Button(
                            onClick = {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                if (granted) beginListening()
                                else permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            },
                            Modifier.fillMaxWidth().height(55.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF72001D)
                            )
                        ) {
                            Text(
                                if (listening) "LISTENING..." else "SPEAK TO MARCUS",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { tts?.stop() },
                            Modifier.fillMaxWidth()
                        ) {
                            Text("STOP VOICE", color = Gold)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { line ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Panel
                                )
                            ) {
                                Text(
                                    line,
                                    Modifier.padding(12.dp),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Speak to Marcus...")
                        }
                    )

                    Button(
                        onClick = { sendToMarcus(message) },
                        Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        Text("SEND MESSAGE")
                    }

                    Button(
                        onClick = {
                            val granted
