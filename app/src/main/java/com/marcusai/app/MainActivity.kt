
package com.marcusai.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val WORKER_URL =
    "https://marcus-ai.cbaby8099.workers.dev"

private val Obsidian = Color(0xFF080B12)
private val ElectricBlue = Color(0xFF168BFF)
private val Panel = Color(0xFF111827)
private val White = Color(0xFFF4F7FF)

data class ChatMessage(
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
            MarcusScreen(prefs)
        }
    }
}

@Composable
fun MarcusScreen(prefs: android.content.SharedPreferences) {
    var userId by remember {
        mutableStateOf(prefs.getString("marcus_user_id", "") ?: "")
    }

    var idInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var busy by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf<ChatMessage>()
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        try {
            val saved = prefs.getString("chat_history", "[]") ?: "[]"
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
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun saveHistory() {
        val array = JSONArray()
        messages.takeLast(30).forEach {
            array.put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
            )
        }
        prefs.edit().putString("chat_history", array.toString()).apply()
    }

    fun sendMessage() {
        val text = messageInput.trim()
        if (text.isEmpty() || busy || userId.isBlank()) return

        messageInput = ""
        messages.add(ChatMessage("user", text))
        saveHistory()
        busy = true
        status = "Marcus is thinking..."

        scope.launch {
            try {
                val history = messages
                    .takeLast(12)
                    .map { it.role to it.content }

                val reply = withContext(Dispatchers.IO) {
                    sendToWorker(userId, text, history)
                }

                messages.add(ChatMessage("assistant", reply))
                saveHistory()
                status = "Connected"
            } catch (e: Exception) {
                messages.add(
                    ChatMessage(
                        "assistant",
                        "I couldn't connect right now. Please check your internet and try again. (${e.message ?: "Connection error"})"
                    )
                )
                saveHistory()
                status = "Connection error"
            } finally {
                busy = false
            }
        }
    }

    fun loadMemory() {
        if (busy || userId.isBlank()) return

        busy = true
        status = "Loading memory..."

        scope.launch {
            try {
                val memory = withContext(Dispatchers.IO) {
                    requestWorker(
                        JSONObject()
                            .put("action", "get_memory")
                            .put("userId", userId)
                    )
                }

                val result = memory.opt("memory")
                val display = when {
                    result == null -> memory.optString(
                        "reply",
                        memory.toString()
                    )
                    result is JSONObject -> result.toString(2)
                    else -> result.toString()
                }

                messages.add(
                    ChatMessage(
                        "assistant",
                        "V10 MEMORY:\n$display"
                    )
                )
                saveHistory()
                status = "Memory loaded"
            } catch (e: Exception) {
                status = "Memory error: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Obsidian)
                .padding(16.dp)
        ) {
            Text(
                "MARCUS AI",
                color = ElectricBlue,
                fontSize = 25.sp
            )

            Text(
                "Your personal assistant",
                color = White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                status,
                color = ElectricBlue,
                fontSize = 12.sp
            )

            if (userId.isBlank()) {
                Spacer(Modifier.height(24.dp))

                Text(
                    "Connect your V10 account",
                    color = White,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Paste your V10 account ID below. It will be saved privately on this phone.",
                    color = White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(14.dp))

                BasicTextField(
                    value = idInput,
                    onValueChange = { idInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = White,
                        fontSize = 14.sp
                    ),
                    decorationBox = { inner ->
                        if (idInput.isEmpty()) {
                            Text(
                                "Paste your V10 account ID",
                                color = White.copy(alpha = 0.4f)
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
                                .putString("marcus_user_id", id)
                                .apply()
                            userId = id
                            status = "Account saved"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue
                    )
                ) {
                    Text("Connect Marcus")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { loadMemory() },
                        enabled = !busy
                    ) {
                        Text("Load V10 Memory", color = ElectricBlue)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { item ->
                        val isUser = item.role == "user"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser)
                                Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .background(
                                        if (isUser) ElectricBlue else Panel,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    if (isUser) "You" else "Marcus",
                                    color = White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.content,
                                    color = White,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .background(Panel, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = White,
                            fontSize = 15.sp
                        ),
                        decorationBox = { inner ->
                            if (messageInput.isEmpty()) {
                                Text(
                                    "Message Marcus...",
                                    color = White.copy(alpha = 0.4f)
                                )
                            }
                            inner()
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { sendMessage() },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue
                        )
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

private suspend fun sendToWorker(
    userId: String,
    message: String,
    history: List<Pair<String, String>>
): String {
    val historyArray = JSONArray()

    history.forEach { (role, content) ->
        historyArray.put(
            JSONObject()
                .put("role", role)
                .put("content", content)
        )
    }

    val body = JSONObject()
        .put("action", "chat")
        .put("userId", userId)
        .put("message", message)
        .put("history", historyArray)

    val result = requestWorker(body)

    return result.optString(
        "reply",
        result.optString("response", "Marcus returned an empty reply.")
    )
}

private fun requestWorker(body: JSONObject): JSONObject {
    val connection = URL(WORKER_URL)
        .openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 90000
        connection.doOutput = true
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode

        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream?.bufferedReader()?.use {
            it.readText()
        } ?: ""

        if (code !in 200..299) {
            throw Exception("Server returned $code: $response")
        }

        return JSONObject(response)
    } finally {
        connection.disconnect()
    }
}
