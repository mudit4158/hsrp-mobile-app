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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hsrp.dealer.App
import com.hsrp.dealer.data.CreateOrderRequest
import com.hsrp.dealer.data.OrderDto
import com.hsrp.dealer.data.rupees
import com.hsrp.dealer.data.unwrap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OrdersScreen(nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val (state, reload) = rememberLoad(search) { App.api().orders(search.ifBlank { null }, null).unwrap().items }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(q, { q = it.uppercase() }, label = { Text("Order or registration") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { search = q }) { Text("Find") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.navigate("neworder") }) { Text("New order") }
            Button(onClick = reload) { Text("Refresh") }
        }
        LoadView(state, reload) { orders ->
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(orders, key = { it.id }) { o -> OrderRow(o) { nav.navigate("order/${o.id}") } }
            }
        }
    }
}

@Composable
private fun OrderRow(o: OrderDto, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PlateText(o.regnNo)
            StatusChip(o.status)
        }
        Text("${o.orderNo} · ${o.orderType.lowercase()} · ${rupees(o.amountPaise)}", fontSize = 13.sp, color = Color(0xFF64748B))
    }
    HorizontalDivider()
}

@Composable
fun OrderDetailScreen(id: String) {
    var tick by remember { mutableStateOf(0) }
    val (state, reload) = rememberLoad(id, tick) { App.api().order(id).unwrap() }
    // Validation runs on the server; refresh while it is in progress.
    LaunchedEffect(state) {
        val s = (state as? Load.Ok)?.value?.status
        if (s == "RECEIVED" || s == "VALIDATED") {
            delay(2500)
            tick++
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            LoadView(state, reload) { o ->
                SectionCard(o.orderNo) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlateText(o.regnNo)
                        StatusChip(o.status)
                    }
                    if (o.status == "RECEIVED") InfoBanner("Checking with VAHAN…")
                    o.rejectionReason?.let { ErrorBanner(it) }
                    o.remarks?.let { InfoBanner(it) }
                    KeyValue("Type", "${o.orderType.lowercase()} · ${o.plateSet.lowercase()}")
                    KeyValue("Vehicle", listOfNotNull(o.maker, o.vehicleClass, o.fuel).joinToString(" · ").ifBlank { null })
                    KeyValue("Chassis", o.chassisNo)
                    KeyValue("Engine", o.engineNo)
                    KeyValue("Amount", rupees(o.amountPaise))
                    KeyValue("Owner", o.ownerName)
                    KeyValue("Mobile", o.mobile)
                    KeyValue("Placed", o.createdAt.take(16).replace('T', ' '))
                }
            }
        }
    }
}

@Composable
fun NewOrderScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var regn by remember { mutableStateOf("") }
    var chassis by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var oem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // One idempotency key per form, so a double tap or a retry after a timeout cannot create two orders.
    val key = remember { UUID.randomUUID().toString() }
    val (oems, _) = rememberLoad { App.api().oems().unwrap() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            Text("The order is checked with VAHAN, priced, and paid from your wallet.", color = Color(0xFF64748B))
            error?.let { ErrorBanner(it) }
            val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            OutlinedTextField(regn, { regn = it.uppercase().replace(" ", "") }, label = { Text("Registration number") }, singleLine = true, keyboardOptions = caps, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chassis, { chassis = it.uppercase().trim() }, label = { Text("Chassis number") }, singleLine = true, keyboardOptions = caps, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(engine, { engine = it.uppercase().trim() }, label = { Text("Engine number") }, singleLine = true, keyboardOptions = caps, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(owner, { owner = it }, label = { Text("Owner name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mobile, { mobile = it.filter(Char::isDigit).take(10) }, label = { Text("Owner mobile (required in Odisha)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Text("OEM (optional)", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = oem == null, onClick = { oem = null }, label = { Text("None") })
                (oems as? Load.Ok)?.value?.forEach { o -> FilterChip(selected = oem == o.code, onClick = { oem = o.code }, label = { Text(o.code) }) }
            }
            Button(
                enabled = !busy && regn.length >= 6 && chassis.length >= 5 && engine.length >= 5,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val o = App.api().createOrder(key, CreateOrderRequest(regn, chassis, engine, oemCode = oem, ownerName = owner.ifBlank { null }, mobile = mobile.ifBlank { null })).unwrap()
                            nav.popBackStack()
                            nav.navigate("order/${o.id}")
                        } catch (e: Exception) {
                            error = errorText(e)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Placing…" else "Place order") }
        }
    }
}
