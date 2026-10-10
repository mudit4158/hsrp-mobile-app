package com.hsrp.dealer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsrp.dealer.App
import com.hsrp.dealer.BuildConfig
import com.hsrp.dealer.data.PendingAction
import com.hsrp.dealer.data.rupees
import com.hsrp.dealer.data.unwrap
import com.hsrp.dealer.sync.SyncQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueueScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by App.db.pending().all().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("en", "IN")) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { SyncQueue.kick(context) }) { Text("Sync now") }
            OutlinedButton(onClick = { scope.launch { App.db.pending().clearDone() } }) { Text("Clear sent") }
        }
        Text(
            "Inward and fitment are saved here first. They are sent with the same request key on every try, so a retry never records twice.",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (items.isEmpty()) Text("Nothing recorded yet.", modifier = Modifier.padding(16.dp), color = Color(0xFF64748B))
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { a ->
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        StatusChip(if (a.status == PendingAction.STATUS_DONE) "DONE" else a.status)
                    }
                    Text("${fmt.format(Date(a.createdAt))} · ${a.attempts} attempt(s)", fontSize = 12.sp, color = Color(0xFF64748B))
                    a.lastError?.let { Text(it, fontSize = 13.sp, color = if (a.status == PendingAction.STATUS_FAILED) Color(0xFFB91C1C) else Color(0xFFB45309)) }
                    if (a.status == PendingAction.STATUS_FAILED) {
                        TextButton(onClick = { scope.launch { deletePhotos(listOf(a.photoFront, a.photoRear, a.photoChassis)); App.db.pending().delete(a.id) } }) { Text("Remove") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun WalletScreen() {
    val (state, reload) = rememberLoad { App.api().wallet().unwrap() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            LoadView(state, reload) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(w.summary.name) {
                        KeyValue("Available", rupees(w.summary.availablePaise))
                        KeyValue("Balance", rupees(w.summary.balancePaise))
                        KeyValue("Held for orders", rupees(w.summary.heldPaise))
                        KeyValue("Credit limit", rupees(w.summary.creditLimitPaise))
                    }
                    SectionCard("Recent entries") {
                        w.statement.items.filter { it.account == "WALLET" }.forEach { e ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${e.createdAt.take(10)} ${e.kind ?: ""}", fontSize = 13.sp)
                                Text(rupees(e.amountPaise), color = if (e.amountPaise < 0) Color(0xFFB91C1C) else Color(0xFF15803D))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val user by App.settings.userFlow.collectAsState(initial = null)
    LaunchedEffect(Unit) {
        url = App.settings.baseUrl()
        device = App.settings.deviceId()
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            SectionCard("Server") {
                OutlinedTextField(url, { url = it.trim() }, label = { Text("API address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Emulator: http://10.0.2.2:3000 (the default). Phone on the same Wi-Fi as the computer: http://<computer's IP address>:3000.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { App.settings.setBaseUrl(url); result = "Saved" } }) { Text("Save") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            result = withContext(Dispatchers.IO) {
                                try {
                                    val c = URL("${url.trimEnd('/')}/api/v1/health").openConnection() as HttpURLConnection
                                    c.connectTimeout = 5000
                                    c.readTimeout = 5000
                                    if (c.responseCode == 200) "Connected: the server answered" else "Server answered HTTP ${c.responseCode}"
                                } catch (e: Exception) {
                                    "Cannot connect: ${e.message}"
                                }
                            }
                        }
                    }) { Text("Test connection") }
                }
                result?.let { InfoBanner(it, ok = it.startsWith("Connected") || it == "Saved") }
            }
            SectionCard("This device") {
                KeyValue("Signed in as", user)
                KeyValue("Device id", device)
                KeyValue("App version", "${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}")
                Text("The device is registered at sign-in. An administrator can deactivate it on the web app.", fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
    }
}
