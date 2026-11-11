package com.marcusai.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class MarcusAppShortcut(val name: String, val packageName: String, val description: String)

@Composable
fun MarcusAppManagerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val shortcuts = listOf(
        MarcusAppShortcut("WhatsApp", "com.whatsapp", "Messages and calls"),
        MarcusAppShortcut("TikTok", "com.zhiliaoapp.musically", "Creator studio and videos"),
        MarcusAppShortcut("Chrome", "com.android.chrome", "Web browsing"),
        MarcusAppShortcut("YouTube", "com.google.android.youtube", "Video platform"),
        MarcusAppShortcut("Google Play Store", "com.android.vending", "Install and update apps"),
        MarcusAppShortcut("Settings", "com.android.settings", "Android device settings"),
        MarcusAppShortcut("MetaTrader 5", "net.metaquotes.metatrader5", "Demo chart learning")
    )
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MARCUS APP MANAGER", color = Color(0xFFD6A64B), style = MaterialTheme.typography.titleLarge)
        Text("Launch supported apps directly. Availability depends on what is installed on this phone.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shortcuts) { app ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF19131D))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(app.name, color = Color.White)
                            Text(app.description, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launch != null) context.startActivity(launch)
                            else {
                                val store = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                                context.startActivity(store)
                            }
                        }) { Text("OPEN") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onClose, Modifier.fillMaxWidth()) { Text("RETURN TO MARCUS") }
    }
}
