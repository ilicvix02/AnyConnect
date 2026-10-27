package com.viktor.anyconnect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viktor.anyconnect.agent.AgentService
import com.viktor.anyconnect.agent.AgentState
import com.viktor.anyconnect.data.Prefs
import com.viktor.anyconnect.ui.theme.AnyConnectTheme
import com.viktor.anyconnect.ui.theme.Muted
import com.viktor.anyconnect.ui.theme.RoyalCyan
import com.viktor.anyconnect.ui.theme.RoyalCyanBright
import com.viktor.anyconnect.ui.theme.Surface2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnyConnectTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen(activity = this)
                }
            }
        }
    }
}

@Composable
private fun AppScreen(activity: Activity) {
    val ctx = activity
    val prefs = remember { Prefs(ctx) }

    val running by AgentState.running.collectAsState()
    val connected by AgentState.connected.collectAsState()
    val log by AgentState.log.collectAsState()

    var task by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(prefs.apiKey.isNotBlank()) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(RoyalCyan, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "AnyConnect",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = RoyalCyanBright)
                Spacer(Modifier.width(6.dp))
                Text("Settings", color = RoyalCyanBright)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Give it a task and put the phone down. The agent opens the right app and taps, types and scrolls until it's done.",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(16.dp))

        // Status chips
        Row {
            StatusChip(
                label = if (connected) "Control ON" else "Control OFF",
                ok = connected
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(
                label = if (hasKey) "Key set" else "No key",
                ok = hasKey
            )
        }

        if (!connected) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Turn on control",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AnyConnect needs the Accessibility permission to act in other apps. Find AnyConnect in the list and switch it on.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalCyan, contentColor = Color.Black)
                    ) { Text("Open accessibility settings") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = task,
            onValueChange = { task = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("What should the agent do?") },
            placeholder = { Text("e.g. Open YouTube and play lofi music") }
        )

        Spacer(Modifier.height(12.dp))

        if (!running) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val svc = AgentService.instance
                    if (svc == null) {
                        AgentState.log("⚠ Turn on control first (accessibility).")
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else if (task.isBlank()) {
                        AgentState.log("⚠ Type a task first.")
                    } else {
                        svc.startTask(task.trim())
                        activity.moveTaskToBack(true)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Run task", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = { AgentService.instance?.stop() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Activity", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1014)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(14.dp)
            ) {
                if (log.isEmpty()) {
                    Text("No activity yet.", color = Muted, fontSize = 13.sp)
                } else {
                    log.forEach { line ->
                        Text(
                            line,
                            color = if (line.startsWith("✖") || line.startsWith("⚠")) MaterialTheme.colorScheme.error else Color(0xFFBfe9f2),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            prefs = prefs,
            onDismiss = { showSettings = false },
            onSaved = {
                hasKey = prefs.apiKey.isNotBlank()
                showSettings = false
            }
        )
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    val c = if (ok) RoyalCyan else Muted
    Box(
        modifier = Modifier
            .background(Color(0xFF0E151B), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(c, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsDialog(prefs: Prefs, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var key by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                prefs.apiKey = key.trim()
                prefs.model = model.trim().ifBlank { "gemini-2.5-flash" }
                onSaved()
            }) { Text("Save", color = RoyalCyanBright) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        },
        title = { Text("Settings") },
        text = {
            Column {
                Text("Gemini API key", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("AIza…") }
                )
                Spacer(Modifier.height(12.dp))
                Text("Model", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("gemini-2.5-flash") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your key is stored only on this phone.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    )
}
