package com.marcusai.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val SettingsGold = Color(0xFF9BCBFF)
private val SettingsPanel = Color(0xFF121E30)

@Composable
fun MarcusSettingsScreen(userId: String, onUserIdChange: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("marcus_config_v15", Context.MODE_PRIVATE) }

    var assistantName by remember { mutableStateOf(prefs.getString("assistant_name", "Marcus") ?: "Marcus") }
    var personality by remember { mutableStateOf(prefs.getString("personality", "Calm and strategic") ?: "Calm and strategic") }
    var responseLength by remember { mutableStateOf(prefs.getString("response_length", "Balanced") ?: "Balanced") }
    var language by remember { mutableStateOf(prefs.getString("language", "English (US)") ?: "English (US)") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var autoSpeak by remember { mutableStateOf(prefs.getBoolean("auto_speak", false)) }
    var speechRate by remember { mutableFloatStateOf(prefs.getFloat("speech_rate", 0.90f)) }
    var memoryEnabled by remember { mutableStateOf(prefs.getBoolean("memory_enabled", true)) }
    var saveHistory by remember { mutableStateOf(prefs.getBoolean("save_history", true)) }
    var confirmActions by remember { mutableStateOf(prefs.getBoolean("confirm_actions", true)) }
    var showReasoningSummary by remember { mutableStateOf(prefs.getBoolean("reasoning_summary", true)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var compactMode by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    var tradingEducation by remember { mutableStateOf(prefs.getBoolean("trading_education", true)) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "Midnight Blue") ?: "Midnight Blue") }
    var saved by remember { mutableStateOf(false) }
    var idDraft by remember(userId) { mutableStateOf(userId) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("MARCUS CONFIGURATION", color = SettingsGold, style = MaterialTheme.typography.titleLarge)
        Text("Personalise the assistant, voice, privacy and interface.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)

        SettingsSection("MARCUS IDENTITY") {
            Text("Your automatically generated Marcus ID", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(idDraft, { idDraft = it }, Modifier.fillMaxWidth(), label = { Text("Marcus User ID") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Marcus ID", userId))
                    saved = true
                }, modifier = Modifier.weight(1f)) { Text("COPY CURRENT ID") }
                OutlinedButton(onClick = {
                    val value = idDraft.trim()
                    if (value.length >= 8) { onUserIdChange(value); saved = true }
                }, modifier = Modifier.weight(1f)) { Text("RESTORE / SAVE ID") }
            }
            OutlinedButton(onClick = {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, userId)
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share Marcus ID"))
            }, modifier = Modifier.fillMaxWidth()) { Text("SHARE MARCUS ID") }
            Text("Keep this ID safe. It identifies your Marcus account on the connected backend. A manually entered ID must already exist there to restore its cloud history.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        SettingsSection("IDENTITY & PERSONALITY") {
            OutlinedTextField(assistantName, { assistantName = it }, Modifier.fillMaxWidth(), label = { Text("Assistant name") }, singleLine = true)
            SettingsChoice("Personality", personality, listOf("Calm and strategic", "Direct and concise", "Friendly and encouraging", "Formal and analytical", "Professional and adaptive")) { personality = it }
            SettingsChoice("Response detail", responseLength, listOf("Concise", "Balanced", "Detailed")) { responseLength = it }
            SettingsChoice("Language", language, listOf("English (US)", "English (UK)", "English (South Africa)")) { language = it }
        }

        SettingsSection("VOICE & SPEECH") {
            SettingsToggle("Voice features enabled", voiceEnabled) { voiceEnabled = it }
            SettingsToggle("Read replies aloud automatically", autoSpeak) { autoSpeak = it }
            Text("Speech rate: ${"%.2f".format(speechRate)}×", color = Color.White)
            Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.70f..1.20f, enabled = voiceEnabled)
            Text("Marcus uses one in-app microphone for both Chat and Voice. Android supplies speech recognition; online recognition is allowed when an offline speech pack is unavailable. If listening fails, check microphone permission and the phone's speech-recognition provider.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + context.packageName))
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth()) { Text("OPEN MARCUS APP PERMISSIONS") }
        }

        SettingsSection("MEMORY & PRIVACY") {
            SettingsToggle("Personal memory", memoryEnabled) { memoryEnabled = it }
            SettingsToggle("Save chat history on this device", saveHistory) { saveHistory = it }
            SettingsToggle("Ask before sensitive actions", confirmActions) { confirmActions = it }
            Text("Memory and history settings are local preferences. They do not create cloud storage or guarantee server-side deletion.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        SettingsSection("RESPONSE BEHAVIOUR") {
            SettingsToggle("Show short answer summaries", showReasoningSummary) { showReasoningSummary = it }
            SettingsToggle("Notifications", notifications) { notifications = it }
            SettingsToggle("Compact interface", compactMode) { compactMode = it }
            SettingsToggle("FX learning tools", tradingEducation) { tradingEducation = it }
            Text("FX tools are for chart learning and demo practice. Live trades and financial actions must remain under your control.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        SettingsSection("APPEARANCE") {
            Text("Choose a futuristic interface identity. Changes apply when you return to the dashboard.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            SettingsChoice("Theme", theme, listOf("Midnight Blue", "Arctic Silver", "Neon Cyber")) { theme = it }
        }

        SettingsSection("PHONE CONTROL") {
            Text("Enable Marcus navigation", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text("To let Marcus perform your spoken Home, Back and Recent Apps commands, Android requires you to enable its clearly disclosed accessibility service. Marcus does not read screen contents or tap arbitrary controls.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.fillMaxWidth()) { Text("OPEN ACCESSIBILITY SETTINGS") }
            Text("Supported commands include opening installed apps and Android settings, opening Dialer, Contacts and Messages, going Home or Back, and showing Recent Apps, Notifications and Quick Settings. Some actions require Marcus Control to be enabled in Android Accessibility settings.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        SettingsSection("APP & SECURITY") {
            Text("Marcus V18 • Futuristic AI edition", color = Color.White)
            Text("Never store API keys, passwords, broker credentials or recovery codes in chat history. Phone-control features require Android permissions and explicit confirmation.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                prefs.edit()
                    .putString("assistant_name", assistantName.trim().ifBlank { "Marcus" })
                    .putString("personality", personality)
                    .putString("response_length", responseLength)
                    .putString("language", language)
                    .putBoolean("voice_enabled", voiceEnabled)
                    .putBoolean("auto_speak", autoSpeak)
                    .putFloat("speech_rate", speechRate)
                    .putBoolean("memory_enabled", memoryEnabled)
                    .putBoolean("save_history", saveHistory)
                    .putBoolean("confirm_actions", confirmActions)
                    .putBoolean("reasoning_summary", showReasoningSummary)
                    .putBoolean("notifications", notifications)
                    .putBoolean("compact_mode", compactMode)
                    .putBoolean("trading_education", tradingEducation)
                    .putString("theme", theme)
                    .apply()
                saved = true
            },
            Modifier.fillMaxWidth()
        ) { Text(if (saved) "SETTINGS SAVED ✓" else "SAVE CONFIGURATION") }
        OutlinedButton(onClick = onClose, Modifier.fillMaxWidth()) { Text("RETURN TO MARCUS") }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SettingsPanel)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = SettingsGold, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsChoice(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
                Text(selected, modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}
