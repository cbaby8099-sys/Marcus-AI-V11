
package com.marcusai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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

private val BackgroundColor = Color(0xFF080B12)
private val BlueColor = Color(0xFF168BFF)
private val PanelColor = Color(0xFF111827)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MarcusApp()
        }
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

    var message by remember { mutableStateOf("") }
    var reply by remember {
        mutableStateOf("Marcus is ready. Tap the blue orb to speak.")
    }
    var busy by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<String>()) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    val speechLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK {
                val spoken = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spoken.isNullOrBlank()) {
                    message = spoken
                    scope.launch {
                        busy = true
                        val answer = sendToWorker(
                            userId,
                            spoken,
                            messages
                        )
                        reply = answer
                        messages = messages + "You: $spoken" +
                            "Marcus: $answer"
                        tts?.speak(
                            answer,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "marcus_reply"
                        )
                        busy = false
                    }
                }
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
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
                        RecognizerIntent.EXTRA_PROMPT,
                        "Speak to Marcus"
                    )
                }
                speechLauncher.launch(intent)
            }
        }

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
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
            }
            speechLauncher.launch(intent)
        } else {
            permissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MARCUS AI",
                        color = BlueColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { chatMode = !chatMode }
                    ) {
                        Text(
                            if (chatMode) "VOICE" else "CHAT",
                            color = BlueColor
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (!chatMode) {
                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(
                                BlueColor.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { startListening() },
                            modifier = Modifier.size(145.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueColor
                            )
                        ) {
                            Text(
                                "M",
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    Text(
                        if (busy) "Marcus is thinking..."
                        else "Tap to speak with Marcus",
                        color = BlueColor,
                        fontSize = 16.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PanelColor
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            reply,
                            modifier = Modifier.padding(20.dp),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { item ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = PanelColor
                                )
                            ) {
                                Text(
                                    item,
                                    modifier = Modifier.padding(14.dp),
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
                            Text("Message Marcus...")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BlueColor,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { startListening() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PanelColor
                            )
                        ) {
                            Text("Speak", color = BlueColor)
                        }

                        Button(
                            onClick = {
                                val sent = message.trim()
                                if (sent.isNotEmpty() && !busy) {
                                    scope.launch {
                                        busy = true
                                        messages = messages +
                                            "You: $sent"
                                        message = ""

                                        val answer = sendToWorker(
                                            userId,
                                            sent,
                                            messages
                                        )

                                        messages = messages +
                                            "Marcus: $answer"
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
                            Text("Send")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (!busy) {
                            scope.launch {
                                busy = true
                                reply = requestWorker(
                                    userId,
                                    "get_memory"
                                )
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("MEMORY", color = BlueColor)
                }
            }
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
            history.forEach { historyArray.put(it) }

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
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.doOutput = true
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

        val response = stream.bufferedReader().use {
            it.readText()
        }

        val json = JSONObject(response)
        json.optString(
            "reply",
            json.optString(
                "response",
                response
            )
        )
    } finally {
        connection.disconnect()
    }
}
