
package com.marcusai.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val Obsidian = Color(0xFF080B12)
private val ElectricBlue = Color(0xFF168BFF)
private val Panel = Color(0xFF111827)
private val White = Color(0xFFF4F7FF)
private val Muted = Color(0xFF8995A8)

private data class ChatMessage(
    val role: String,
    val content: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            "marcus_private",
            Context.MODE_PRIVATE
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ElectricBlue,
                    background = Obsidian,
                    surface = Panel,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                MarcusScreen(prefs)
            }
        }
    }
}

@Composable
private fun MarcusScreen(
    prefs: android.content.SharedPreferences
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var userId by remember {
        mutableStateOf(
            prefs.getString("marcus_user_id", "") ?: ""
        )
    }

    var idInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var busy by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(true) }

    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }

    val listState = rememberLazyListState()

    val tts = remember {
        TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                // Voice engine ready
            }
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val saved = prefs.getString(
                "chat_history",
                "[]"
            ) ?: "[]"

            val array = JSONArray(saved)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                messages.add(
                    ChatMessage(
                        item.optString("role"),
                        item.optString("content")
                    )
                )
            }
        } catch (_: Exception) {
            messages.clear()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.lastIndex
            )
        }
    }

    fun saveHistory() {
        val array = JSONArray()

        messages.takeLast(40).forEach {
            array.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
            )
        }

        prefs.edit()
            .putString("chat_history", array.toString())
            .apply()
    }

    fun sendMessage() {
        val text = messageInput.trim()

        if (
            text.isEmpty() ||
            busy ||
            userId.isBlank()
        ) return

        messageInput = ""

        messages.add(
            ChatMessage("user", text)
        )

        saveHistory()

        busy = true
        status = "Marcus is thinking..."

        scope.launch {
            try {
                val history = messages
                    .takeLast(12)
                    .map {
                        it.role to it.content
                    }

                val reply = withContext(Dispatchers.IO) {
                    sendToWorker(
                        userId,
                        text,
                        history
                    )
                }

                messages.add(
                    ChatMessage("assistant", reply)
                )

                saveHistory()
                status = "Marcus is ready"

                if (voiceMode) {
                    tts.language = Locale.getDefault()

                    tts.speak(
                        reply,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "marcus_reply"
                    )
                }

            } catch (_: Exception) {
                messages.add(
                    ChatMessage(
                        "assistant",
                        "I couldn't connect. Check your internet and try again."
                    )
                )

                saveHistory()
                status = "Connection problem"

            } finally {
                busy = false
            }
        }
    }

    fun loadMemory() {
        if (busy || userId.isBlank()) return

        busy = true
        status = "Loading V10 memory..."

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    requestWorker(
                        JSONObject()
                            .put("action", "get_memory")
                            .put("userId", userId)
                    )
                }

                val memory = result.opt("memory")

                val display = when {
                    memory == null ->
                        result.optString(
                            "reply",
                            result.toString()
                        )

                    memory is JSONObject ->
                        memory.toString(2)

                    memory is JSONArray ->
                        memory.toString(2)

                    else -> memory.toString()
                }

                messages.add(
                    ChatMessage(
                        "assistant",
                        "V10 MEMORY\n$display"
                    )
                )

                saveHistory()
                status = "Memory loaded"
                voiceMode = false

            } catch (_: Exception) {
                status = "Memory could not load"

            } finally {
                busy = false
            }
        }
    }

    val speechLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode ==
                android.app.Activity.RESULT_OK
            ) {
                val spoken = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spoken.isNullOrBlank()) {
                    messageInput = spoken
                    status = "Voice captured — ready to send"
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

                try {
                    speechLauncher.launch(intent)
                } catch (_: Exception) {
                    status = "Speech service unavailable"
                }

            } else {
                status = "Microphone permission is required"
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "MARCUS",
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "PERSONAL AI ASSISTANT",
                        color = ElectricBlue,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }

                Text(
                    "V11",
                    color = ElectricBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            Panel,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        )
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                status,
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            if (userId.isBlank()) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Connect your V10 account",
                        color = White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    BasicTextField(
                        value = idInput,
                        onValueChange = {
                            idInput = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Panel,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp),
                        textStyle = TextStyle(
                            color = White,
                            fontSize = 14.sp
                        ),
                        decorationBox = { inner ->
                            if (idInput.isEmpty()) {
                                Text(
                                    "Paste your V10 account ID",
                                    color = Muted
                                )
                            }
                            inner()
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val id = idInput.trim()

                            if (id.isNotEmpty()) {
                                prefs.edit()
                                    .putString(
                                        "marcus_user_id",
                                        id
                                    )
                                    .apply()

                                userId = id
                                status = "V10 account connected"
                            }
                        },
                        enabled = idInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue
                        )
                    ) {
                        Text("Connect Marcus")
                    }
                }

            } else {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Panel,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(5.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {
                    ModeButton(
                        label = "VOICE",
                        selected = voiceMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        voiceMode = true
                    }

                    ModeButton(
                        label = "CHAT",
                        selected = !voiceMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        voiceMode = false
                    }
                }

                if (voiceMode) {

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {
                        Text(
                            "YOUR ASSISTANT",
                            color = Muted,
                            fontSize = 11.sp,
                            letterSpacing = 3.sp
                        )

                        Spacer(Modifier.height(28.dp))

                        Box(
                            modifier = Modifier
                                .size(205.dp)
                                .background(
                                    ElectricBlue.copy(alpha = 0.08f),
                                    CircleShape
                                )
                                .border(
                                    2.dp,
                                    ElectricBlue.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .padding(16.dp)
                                .background(
                                    ElectricBlue.copy(alpha = 0.18f),
                                    CircleShape
                                )
                                .clickable(enabled = !busy) {
                                    permissionLauncher.launch(
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (busy) "..." else "M",
                                color = White,
                                fontSize = 76.sp,
                                fontWeight = FontWeight.Light
                            )
                        }

                        Spacer(Modifier.height(26.dp))

                        Text(
                            if (busy)
                                "Marcus is thinking"
                            else
                                "Talk to Marcus",
                            color = White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Tap the blue orb to speak",
                            color = Muted,
                            fontSize = 13.sp
                        )

                        Spacer(Modifier.height(22.dp))

                        if (messages.isNotEmpty()) {
                            Text(
                                messages.last().content,
                                color = White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Panel,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(14.dp)
                            )
                        }

                        if (messageInput.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))

                            Text(
                                messageInput,
                                color = White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Panel,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp)
                            )

                            Button(
                                onClick = {
                                    sendMessage()
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricBlue
                                )
                            ) {
                                Text("Send to Marcus")
                            }
                        }
                    }

                } else {

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                      
