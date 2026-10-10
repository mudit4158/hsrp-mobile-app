package com.hsrp.dealer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hsrp.dealer.App
import com.hsrp.dealer.data.LoginRequest
import com.hsrp.dealer.data.unwrap
import com.hsrp.dealer.sync.SyncQueue
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { server = App.settings.baseUrl() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("HSRP Dealer", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Brand)
        Text("Place orders, inward challans and record fitment. Works offline: inward and fitment sync when the connection returns.", color = Color(0xFF64748B))
        error?.let { ErrorBanner(it) }
        OutlinedTextField(username, { username = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        App.settings.setBaseUrl(server)
                        val res = App.api().login(LoginRequest(username, password, App.settings.deviceId(), App.settings.deviceName())).unwrap()
                        if (!res.user.roles.contains("DEALER")) throw IllegalStateException("This app is for dealer users.")
                        App.settings.setSession(res.token, res.user.displayName)
                        SyncQueue.kick(context)
                    } catch (e: Exception) {
                        error = errorText(e)
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(if (busy) "Signing in…" else "Sign in") }
        TextButton(onClick = { showServer = !showServer }) { Text("Server: $server") }
        if (showServer) {
            OutlinedTextField(server, { server = it.trim() }, label = { Text("API address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                "Emulator: http://10.0.2.2:3000. Phone on the same Wi-Fi: http://<your computer's IP>:3000 (see the README).",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
            )
        }
        Text("Demo dealer users: dealer.wb01, dealer.od02 (password: SEED_PASSWORD from the server's .env).", fontSize = 12.sp, color = Color(0xFF64748B))
    }
}
