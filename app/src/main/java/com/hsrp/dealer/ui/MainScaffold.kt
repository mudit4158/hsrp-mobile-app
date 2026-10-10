package com.hsrp.dealer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hsrp.dealer.App

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("orders", "Orders", Icons.AutoMirrored.Filled.List),
    Tab("inward", "Inward", Icons.Filled.ShoppingCart),
    Tab("fitment", "Fitment", Icons.Filled.Build),
    Tab("queue", "Sync", Icons.Filled.Refresh),
)

private val TITLES = mapOf(
    "home" to "HSRP Dealer",
    "orders" to "Orders",
    "inward" to "Challan inward",
    "fitment" to "Fitment",
    "queue" to "Offline queue",
    "neworder" to "New order",
    "order/{id}" to "Order",
    "challan/{id}" to "Inward challan",
    "record" to "Record fitment",
    "wallet" to "Wallet",
    "settings" to "Settings",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val pending by App.db.pending().pendingCount().collectAsState(initial = 0)
    val isTab = TABS.any { it.route == route }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[route] ?: "HSRP Dealer") },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White),
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { go(nav, t.route) },
                        icon = {
                            if (t.route == "queue" && pending > 0) BadgedBox(badge = { Badge { Text("$pending") } }) { Icon(t.icon, t.label) } else Icon(t.icon, t.label)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(nav) }
            composable("orders") { OrdersScreen(nav) }
            composable("neworder") { NewOrderScreen(nav) }
            composable("order/{id}") { OrderDetailScreen(it.arguments?.getString("id") ?: "") }
            composable("inward") { ChallansScreen(nav) }
            composable("challan/{id}") { ChallanScreen(nav, it.arguments?.getString("id") ?: "") }
            composable("fitment") { FitmentSearchScreen(nav) }
            composable("record") { FitmentRecordScreen(nav) }
            composable("queue") { QueueScreen() }
            composable("wallet") { WalletScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

fun go(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
