package com.hsrp.dealer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hsrp.dealer.App
import com.hsrp.dealer.data.rupees
import com.hsrp.dealer.data.unwrap
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val (state, reload) = rememberLoad { App.api().home().unwrap() }
    val pending by App.db.pending().pendingCount().collectAsState(initial = 0)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            if (pending > 0) InfoBanner("$pending record(s) waiting to sync. They are sent automatically when online.")
            LoadView(state, reload) { h ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(h.dealer.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${h.dealer.code} · ${h.dealer.city}", color = Color(0xFF64748B))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountTile("To inward", h.counts.toInward, Modifier.weight(1f)) { go(nav, "inward") }
                        CountTile("To fit", h.counts.toFit, Modifier.weight(1f)) { go(nav, "fitment") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountTile("In production", h.counts.inProduction, Modifier.weight(1f)) { go(nav, "orders") }
                        CountTile("Waiting for VAHAN", h.counts.awaitingVahan, Modifier.weight(1f)) { go(nav, "orders") }
                    }
                    h.wallet?.let { w ->
                        SectionCard("Wallet", Modifier.clickable { nav.navigate("wallet") }) {
                            KeyValue("Available", rupees(w.availablePaise))
                            KeyValue("Held for orders", rupees(w.heldPaise))
                            KeyValue("Credit limit", rupees(w.creditLimitPaise))
                        }
                    }
                    SectionCard("Today's appointments") {
                        if (h.appointments.isEmpty()) Text("No customer appointments today.", color = Color(0xFF64748B))
                        h.appointments.forEach { a ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${a.slotStart}  ${a.regnNo ?: ""}")
                                a.orderStatus?.let { StatusChip(it) }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("neworder") }) { Text("New order") }
                OutlinedButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                OutlinedButton(onClick = { scope.launch { App.settings.setSession(null, null) } }) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun CountTile(label: String, value: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color(0xFF64748B), fontSize = 13.sp)
            Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (value > 0) Brand else Color(0xFF94A3B8))
        }
    }
}
