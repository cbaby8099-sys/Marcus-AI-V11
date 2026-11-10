package com.marcusai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Obsidian = Color(0xFF080B12)
private val ElectricBlue = Color(0xFF168BFF)
private val Panel = Color(0xFF111827)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ElectricBlue,
                    background = Obsidian,
                    surface = Panel,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                MarcusHome()
            }
        }
    }
}

@Composable
private fun MarcusHome() {
    var message by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Obsidian
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                "MARCUS AI",
                color = ElectricBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(34.dp))

            Box(
                modifier = Modifier
                    .background(
                        ElectricBlue.copy(alpha = 0.13f),
                        CircleShape
                    )
                    .padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(ElectricBlue, CircleShape)
                        .padding(30.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                "Marcus is ready",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "Your personal AI assistant",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Panel,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        "Hello, Master.",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Marcus AI V11 is ready. Next, we'll connect your existing V10 brain and memory.",
                        color = Color(0xFFB8C2D1),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Marcus...") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        // Chat connection comes next.
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Send")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
