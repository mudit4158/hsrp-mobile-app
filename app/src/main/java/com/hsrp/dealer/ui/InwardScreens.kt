package com.hsrp.dealer.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hsrp.dealer.App
import com.hsrp.dealer.data.InwardLine
import com.hsrp.dealer.data.InwardRequest
import com.hsrp.dealer.data.PendingAction
import com.hsrp.dealer.data.unwrap
import com.hsrp.dealer.sync.SyncQueue
import kotlinx.coroutines.launch

@Composable
fun ChallansScreen(nav: NavHostController) {
    val (state, reload) = rememberLoad { App.api().challans("CREATED").unwrap() }
    val queued by App.db.pending().all().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Challans in transit to you", modifier = Modifier.weight(1f), color = Color(0xFF64748B))
            Button(onClick = reload) { Text("Refresh") }
        }
        LoadView(state, reload) { challans ->
            if (challans.isEmpty()) Text("Nothing to inward.", modifier = Modifier.padding(16.dp), color = Color(0xFF64748B))
            LazyColumn(Modifier.fillMaxSize()) {
                items(challans, key = { it.id }) { c ->
                    val q = queued.firstOrNull { it.type == PendingAction.TYPE_INWARD && it.refId == c.id && it.status != PendingAction.STATUS_FAILED }
                    Column(Modifier.fillMaxWidth().clickable(enabled = q == null) { nav.navigate("challan/${c.id}") }.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.challanNo, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            if (q != null) StatusChip(q.status) else Text("${c.plates ?: 0} plates", color = Color(0xFF64748B))
                        }
                        Text("Dispatched ${c.createdAt.take(10)}", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ChallanScreen(nav: NavHostController, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (state, reload) = rememberLoad(id) { App.api().challan(id).unwrap() }
    val decisions = remember { mutableStateMapOf<String, String>() }
    val reasons = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            LoadView(state, reload) { c ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.challanNo, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Check each plate. Reject a damaged or wrong plate with a reason; the station reviews it.", color = Color(0xFF64748B))
                    error?.let { ErrorBanner(it) }
                    (c.lines ?: emptyList()).forEach { l ->
                        SectionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                PlateText(l.regnNo ?: "")
                                Text(l.position, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Laser code ${l.laserCode} · ${l.orderNo ?: ""}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = decisions[l.id] == "ACCEPT", onClick = { decisions[l.id] = "ACCEPT" }, label = { Text("Accept") })
                                FilterChip(selected = decisions[l.id] == "REJECT", onClick = { decisions[l.id] = "REJECT" }, label = { Text("Reject") })
                            }
                            if (decisions[l.id] == "REJECT") {
                                OutlinedTextField(reasons[l.id] ?: "", { reasons[l.id] = it }, label = { Text("Reason (mandatory)") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    val lines = c.lines ?: emptyList()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { lines.forEach { decisions[it.id] = "ACCEPT" } }) { Text("Accept all") }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = lines.isNotEmpty() && lines.all { decisions[it.id] != null },
                        onClick = {
                            val missing = lines.filter { decisions[it.id] == "REJECT" && (reasons[it.id] ?: "").isBlank() }
                            if (missing.isNotEmpty()) {
                                error = "Give a reason for every rejected plate."
                                return@Button
                            }
                            scope.launch {
                                val req = InwardRequest(lines.map { InwardLine(it.id, decisions[it.id]!!, if (decisions[it.id] == "REJECT") reasons[it.id] else null) })
                                val rejected = lines.count { decisions[it.id] == "REJECT" }
                                SyncQueue.enqueueInward(context, c.id, "Inward ${c.challanNo}: ${lines.size - rejected} accepted, $rejected rejected", req)
                                nav.popBackStack()
                                go(nav, "queue")
                            }
                        },
                    ) { Text("Submit inward") }
                    Text("Saved on the phone first; sent now if online, otherwise when the connection returns.", fontSize = 12.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
}
