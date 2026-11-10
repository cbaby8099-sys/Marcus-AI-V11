
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

private val BackgroundColor = Color(0xFF07050A)
private val RedColor = Color(0xFFB00020)
private val BlueColor = Color(0xFF168BFF)
private val PanelColor = Color(0xFF17121D)
private val GoldColor = Color(0xFFD5A64A)

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

    var idInput by remember { mutableStateOf("") }
    var showIdDialog by remember { mutableStateOf(userId.isBlank()) }
    var idError by remember { mutableStateOf(false) }

    var message by remember { mutableStateOf("") }
    var reply by remember {
        mutableStateOf("The entity is awake. Awaiting your command.")
    }
    var busy by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale.UK
                    setSpeechRate(0.92f)
                    setPitch(0.92f)
                }
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
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spoken.isNullOrBlank() && userId.isNotBlank()) {
                    message = spoken

                    scope.launch {
                        busy = true
                        val answer = sendToWorker(
                            userId,
                            spoken,
                            messages
                        )

                        reply = answer
                        messages = messages + listOf(
                            "You: $spoken",
                            "Marcus: $answer"
                        )

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

    fun launchSpeech() {
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

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) launchSpeech()
        }

    fun startListening() {
        if (!userId.matches(Regex("^[a-zA-Z0-9_-]{16,80}$"))) {
            showIdDialog = true
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchSpeech()
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Text(
                            "THE DARK INTELLIGENCE",
                            color = RedColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "VERSION 12",
                            color = GoldColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            TextButton(
                                onClick = {
                                    idInput = ""
                                    showIdDialog = true
                                }
                            ) {
                                Text(
                                    "LINK ID",
                                    color = RedColor,
                                    fontSize = 10.sp
                                )
                            }

                            TextButton(
                                onClick = {
                                    chatMode = !chatMode
                                }
                            ) {
                                Text(
                                    if (chatMode) "VOICE" else "CHAT",
                                    color = BlueColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (!chatMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "◈  THE ENTITY IS AWAKE  ◈",
                            color = RedColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(22.dp))

                        Box(
                            modifier = Modifier
                                .size(174.dp)
                                .background(
                                    RedColor.copy(alpha = 0.10f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .background(
                                        BlueColor.copy(alpha = 0.12f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { startListening() },
                                    modifier = Modifier.size(120.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF190D18)
                                    )
                                ) {
                                    Text(
                                        "◉\nM",
                                        color = Color(0xFFFF334F),
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 35.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            if (busy) "MARCUS IS THINKING..."
                            else "TAP THE CORE TO SPEAK",
                            color = BlueColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = PanelColor
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "MARCUS RESPONSE",
                                    color = GoldColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    reply,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = PanelColor
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    item,
                                    modifier = Modifier.padding(13.dp),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (messages.isEmpty()) {
                            item {
                                Text(
                                    "Your conversation with Marcus will appear here.",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(12.dp)
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
                            unfocusedBorderColor = RedColor,
                            cursorColor = BlueColor
                        )
                    )

                    Spacer(Modifier.height(8.dp))

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
                            Text("SPEAK", color = BlueColor)
                        }

                        Button(
                            onClick = {
                                val sent = message.trim()

                                if (userId.isBlank()) {
                                    showIdDialog = true
                                } else if (
                                    sent.isNotEmpty() && !busy
                                ) {
                                    scope.launch {
                                        busy = true
                                        val oldHistory = messages
                                        messages = oldHistory + "You: $sent"
                                        message = ""

                                        val answer = sendToWorker(
                                            userId,
                                            sent,
                                            oldHistory
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
                                reply = requestWorker(
                                    userId,
                                    "get_memory"
                                )
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GoldColor
                    )
                ) {
                    Text("MEMORY")
                }
            }
        }

        if (showIdDialog) {
            AlertDialog(
                onDismissRequest = {
                    showIdDialog = false
                },
                title = {
                    Text(
                        "CONNECT MARCUS",
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            "Paste your existing V10 user ID. It stays on this device.",
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
                            label = {
                                Text("V10 User ID")
                            },
                            isError = idError
                        )

                        if (idError) {
                            Text(
                                "That ID format is not valid. Check the copied ID.",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                      
