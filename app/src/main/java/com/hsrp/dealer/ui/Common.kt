package com.hsrp.dealer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.hsrp.dealer.data.ApiException
import java.io.IOException

val Brand = Color(0xFF1E3A8A)
val BrandBright = Color(0xFF2563EB)

@Composable
fun HsrpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = BrandBright, secondary = Brand, primaryContainer = Color(0xFFDBEAFE), surface = Color.White, background = Color(0xFFF4F6FA)),
        content = content,
    )
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

fun errorText(e: Throwable): String = when (e) {
    is ApiException -> if (e.status == 401) "Your session has ended. Sign in again." else e.message ?: "Request failed"
    is IOException -> "Cannot reach the server. Check the server address in Settings."
    else -> e.message ?: e.toString()
}

/** Loads data when the keys change; returns the state and a reload function. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Pair<Load<T>, () -> Unit> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(keys = arrayOf(*keys, tick)) {
        state = try {
            Load.Ok(block())
        } catch (e: Exception) {
            Load.Err(errorText(e))
        }
    }
    return state to { tick++ }
}

@Composable
fun <T> LoadView(state: Load<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Err -> Column(Modifier.padding(16.dp)) {
            ErrorBanner(state.message)
            TextButton(onClick = retry) { Text("Try again") }
        }
        is Load.Ok -> content(state.value)
    }
}

@Composable
fun ErrorBanner(text: String) {
    Text(
        text,
        color = Color(0xFF7F1D1D),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFFEE2E2), RoundedCornerShape(8.dp)).padding(12.dp),
    )
}

@Composable
fun InfoBanner(text: String, ok: Boolean = false) {
    Text(
        text,
        color = if (ok) Color(0xFF14532D) else Color(0xFF1E3A8A),
        modifier = Modifier.fillMaxWidth().background(if (ok) Color(0xFFDCFCE7) else Color(0xFFDBEAFE), RoundedCornerShape(8.dp)).padding(12.dp),
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            content()
        }
    }
}

private val STATUS_COLORS = mapOf(
    "COMPLETED" to (Color(0xFFDCFCE7) to Color(0xFF15803D)),
    "REJECTED" to (Color(0xFFFEE2E2) to Color(0xFFB91C1C)),
    "CANCELLED" to (Color(0xFFF1F5F9) to Color(0xFF475569)),
    "REJECTION_REVIEW" to (Color(0xFFFEF3C7) to Color(0xFFB45309)),
    "AWAITING_APPROVAL" to (Color(0xFFFEF3C7) to Color(0xFFB45309)),
    "AWAITING_PAYMENT" to (Color(0xFFFEF3C7) to Color(0xFFB45309)),
    "DISPATCHED" to (Color(0xFFCCFBF1) to Color(0xFF0F766E)),
    "INWARDED" to (Color(0xFFCCFBF1) to Color(0xFF0F766E)),
    "FITTED" to (Color(0xFFCCFBF1) to Color(0xFF0F766E)),
    "PENDING" to (Color(0xFFFEF3C7) to Color(0xFFB45309)),
    "SYNCING" to (Color(0xFFDBEAFE) to Color(0xFF1D4ED8)),
    "DONE" to (Color(0xFFDCFCE7) to Color(0xFF15803D)),
    "FAILED" to (Color(0xFFFEE2E2) to Color(0xFFB91C1C)),
)

fun statusLabel(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun StatusChip(status: String) {
    val (bg, fg) = STATUS_COLORS[status] ?: (Color(0xFFDBEAFE) to Color(0xFF1D4ED8))
    Text(statusLabel(status), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun PlateText(regnNo: String) {
    Text(
        regnNo,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun KeyValue(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF64748B), fontSize = 13.sp)
        Text(value ?: "–", fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
