package com.hsrp.dealer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hsrp.dealer.App
import com.hsrp.dealer.data.FitmentHit
import com.hsrp.dealer.data.unwrap
import com.hsrp.dealer.sync.FitmentPayload
import com.hsrp.dealer.sync.SyncQueue
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

/** The order picked on the search screen, handed to the record screen. */
object FitmentSelection {
    var hit: FitmentHit? = null
}

@Composable
fun FitmentSearchScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var regn by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<FitmentHit>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            Text("Search the vehicle by registration number to see its laser codes.", color = Color(0xFF64748B))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    regn,
                    { regn = it.uppercase().replace(" ", "") },
                    label = { Text("Registration number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = regn.length >= 6 && !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            hits = App.api().searchFitment(regn).unwrap()
                        } catch (e: Exception) {
                            error = errorText(e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Search") }
            }
            error?.let { ErrorBanner(it) }
            hits?.let { list ->
                if (list.isEmpty()) InfoBanner("No order of yours for this vehicle is ready for fitment.")
                list.forEach { h ->
                    SectionCard(h.order.orderNo) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlateText(h.order.regnNo)
                            StatusChip(h.order.status)
                        }
                        h.allocations.forEach { a -> KeyValue("${a.position.lowercase()} laser code", a.laserCode) }
                        if (h.canRecord) {
                            Button(onClick = {
                                FitmentSelection.hit = h
                                nav.navigate("record")
                            }, modifier = Modifier.fillMaxWidth()) { Text("Record fitment") }
                        } else {
                            Text(
                                when (h.order.status) {
                                    "DISPATCHED" -> "Inward the challan first."
                                    "FITTED" -> "Fitment recorded; waiting for VAHAN."
                                    "COMPLETED" -> "Fitment done and posted to VAHAN."
                                    else -> "Not ready for fitment."
                                },
                                color = Color(0xFF64748B),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FitmentRecordScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hit = FitmentSelection.hit
    if (hit == null) {
        ScreenColumn { ErrorBanner("Search the vehicle again.") }
        return
    }
    val photos = remember { mutableStateMapOf<String, String>() }
    var camera by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    val front = hit.allocations.firstOrNull { it.position == "FRONT" }
    val rear = hit.allocations.firstOrNull { it.position == "REAR" }
    val slots = buildList {
        if (front != null) add(Triple("front", "Front plate", hit.photoRules.front))
        if (rear != null) add(Triple("rear", "Rear plate", hit.photoRules.rear))
        add(Triple("chassis", "Chassis number", hit.photoRules.chassis))
    }
    val ready = confirmed && slots.filter { it.third }.all { photos[it.first] != null }

    camera?.let { kind ->
        CameraCapture(
            kind,
            slots.first { it.first == kind }.second,
            onCaptured = { f ->
                photos[kind] = f.path
                camera = null
            },
            onCancel = { camera = null },
            // Plate photos are read by OCR on the server (D45); the debug test photo draws this number.
            plateText = if (kind == "chassis") null else hit.order.regnNo,
        )
        return
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        ScreenColumn {
            SectionCard(hit.order.orderNo) {
                PlateText(hit.order.regnNo)
                KeyValue("Vehicle", listOfNotNull(hit.order.maker, hit.order.vehicleClass).joinToString(" · "))
                hit.allocations.forEach { a ->
                    Text("${a.position}: ${a.laserCode}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirmed, { confirmed = it })
                    Text("The plates on the vehicle carry these laser codes")
                }
            }
            slots.forEach { (kind, label, required) ->
                SectionCard("$label photo${if (required) " (required)" else " (optional)"}") {
                    photos[kind]?.let { path ->
                        val bmp = remember(path) { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = "$label photo", contentScale = ContentScale.Crop, modifier = Modifier.width(240.dp).height(150.dp))
                    }
                    OutlinedButton(onClick = { camera = kind }) { Text(if (photos[kind] == null) "Take photo" else "Retake") }
                }
            }
            Button(
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        SyncQueue.enqueueFitment(
                            context,
                            "Fitment ${hit.order.regnNo} (${hit.order.orderNo})",
                            FitmentPayload(hit.order.id, front?.laserCode, rear?.laserCode, Instant.now().toString()),
                            photos["front"],
                            photos["rear"],
                            photos["chassis"],
                        )
                        FitmentSelection.hit = null
                        nav.popBackStack()
                        go(nav, "queue")
                    }
                },
            ) { Text("Submit fitment") }
            Text("Saved on the phone first and sent now, or when the connection returns. The server reads the registration number on each plate photo (OCR) and refuses the fitment if it is not ${hit.order.regnNo}; VAHAN is updated by the server.", fontSize = 12.sp, color = Color(0xFF64748B))
        }
    }
}

fun deletePhotos(paths: List<String?>) = paths.filterNotNull().forEach { File(it).delete() }
