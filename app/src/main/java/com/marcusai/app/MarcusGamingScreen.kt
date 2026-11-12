package com.marcusai.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MarcusGamingScreen(palette: MarcusPalette, onClose: () -> Unit, onAskMarcus: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("marcus_gaming_v1", Context.MODE_PRIVATE) }
    var cash by remember { mutableStateOf(prefs.getString("cash", "") ?: "") }
    var buy by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("0") }
    var target by remember { mutableStateOf(prefs.getString("target", "120000000") ?: "120000000") }
    var result by remember { mutableStateOf("Enter your figures to calculate a possible trade.") }
    var priceNote by remember { mutableStateOf("") }
    var priceLog by remember { mutableStateOf(prefs.getString("price_log", "") ?: "") }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) {
            elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            delay(1000)
        }
    }
    val games = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching { context.packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }.filter { it.first != context.packageName }
            .sortedBy { it.second.lowercase(Locale.US) }
    }
    fun money(value: String): Long = value.replace(",", "").replace(" ", "").toLongOrNull() ?: 0L
    fun format(value: Long): String = String.format(Locale.US, "%,d", value)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("GAMING CO-PILOT", color = palette.primary, style = MaterialTheme.typography.titleLarge)
                Text("You play. Marcus assists. No overlay required.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SESSION TIMER", color = palette.primary, style = MaterialTheme.typography.titleSmall)
                Text(String.format(Locale.US, "%02d:%02d:%02d", elapsed / 3600000, (elapsed / 60000) % 60, (elapsed / 1000) % 60), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (!running) startedAt = System.currentTimeMillis() - elapsed; running = !running }, modifier = Modifier.weight(1f)) { Text(if (running) "PAUSE" else "START") }
                    OutlinedButton(onClick = { running = false; elapsed = 0L }, modifier = Modifier.weight(1f)) { Text("RESET") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GRAND MOBILE RP • MONEY PLANNER", color = palette.primary, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(cash, { cash = it; prefs.edit().putString("cash", it).apply() }, Modifier.fillMaxWidth(), label = { Text("Current cash") }, singleLine = true)
                OutlinedTextField(buy, { buy = it }, Modifier.fillMaxWidth(), label = { Text("Car purchase price") }, singleLine = true)
                OutlinedTextField(sell, { sell = it }, Modifier.fillMaxWidth(), label = { Text("Expected selling price") }, singleLine = true)
                OutlinedTextField(fees, { fees = it }, Modifier.fillMaxWidth(), label = { Text("Other costs (optional)") }, singleLine = true)
                OutlinedTextField(target, { target = it; prefs.edit().putString("target", it).apply() }, Modifier.fillMaxWidth(), label = { Text("Business savings target") }, singleLine = true)
                Button(onClick = {
                    val profit = money(sell) - money(buy) - money(fees)
                    val after = money(cash) + profit
                    val remaining = (money(target) - after).coerceAtLeast(0L)
                    result = "Estimated net result: ${if (profit >= 0) "+" else ""}${format(profit)}\nEstimated cash after sale: ${format(after)}\nRemaining to target: ${format(remaining)}\nBased only on your estimates; no sale or profit is guaranteed."
                }, Modifier.fillMaxWidth()) { Text("CALCULATE") }
                Text(result, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onAskMarcus("Help me review this Grand Mobile RP car trade plan. Current cash: $cash. Buy price: $buy. Expected sale: $sell. Other costs: $fees. Business target: $target. Assess risks and calculations; do not guarantee a profit.") }, Modifier.fillMaxWidth()) { Text("ASK MARCUS TO REVIEW PLAN") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OBSERVED CAR PRICE NOTES", color = palette.primary, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(priceNote, { priceNote = it }, Modifier.fillMaxWidth(), label = { Text("Car name and observed price") }, singleLine = true)
                Button(onClick = {
                    if (priceNote.isNotBlank()) {
                        priceLog = (listOf(priceNote.trim()) + priceLog.lines().filter { it.isNotBlank() }).take(30).joinToString("\n")
                        prefs.edit().putString("price_log", priceLog).apply()
                        priceNote = ""
                    }
                }, Modifier.fillMaxWidth()) { Text("SAVE PRICE NOTE") }
                if (priceLog.isNotBlank()) {
                    Text(priceLog, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { priceLog = ""; prefs.edit().remove("price_log").apply() }) { Text("CLEAR NOTES") }
                } else Text("Notes are stored locally. Marcus does not automatically inspect the game's market.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LAUNCHABLE APPS", color = palette.primary, style = MaterialTheme.typography.titleSmall)
                Text("Android provides apps with launcher activities. Choose one to open.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                games.forEach { (packageName, label) ->
                    OutlinedButton(onClick = {
                        context.packageManager.getLaunchIntentForPackage(packageName)?.let { launch -> runCatching { context.startActivity(launch) } }
                    }, Modifier.fillMaxWidth()) { Text("OPEN • $label") }
                }
            }
        }
    }
}

@Composable
fun MarcusMemoryScreen(palette: MarcusPalette, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("marcus_local_memory", Context.MODE_PRIVATE) }
    var draft by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(prefs.getString("notes", "").orEmpty().lines().filter { it.isNotBlank() }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MARCUS MEMORY", color = palette.primary, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        Text("Save useful notes and preferences on this phone. These notes are not automatically uploaded to the cloud.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("Add a note or preference") }, minLines = 2)
        Button(onClick = {
            if (draft.isNotBlank()) {
                notes = (notes + draft.trim()).takeLast(50)
                prefs.edit().putString("notes", notes.joinToString("\n")).apply()
                draft = ""
            }
        }, Modifier.fillMaxWidth()) { Text("SAVE TO DEVICE") }
        notes.forEachIndexed { index, note ->
            Card(colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(note, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        notes = notes.filterIndexed { i, _ -> i != index }
                        prefs.edit().putString("notes", notes.joinToString("\n")).apply()
                    }) { Text("DELETE") }
                }
            }
        }
        if (notes.isNotEmpty()) OutlinedButton(onClick = { notes = emptyList(); prefs.edit().remove("notes").apply() }, Modifier.fillMaxWidth()) { Text("CLEAR ALL NOTES") }
    }
}
