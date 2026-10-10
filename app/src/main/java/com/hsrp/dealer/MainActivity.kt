package com.hsrp.dealer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hsrp.dealer.ui.HsrpTheme
import com.hsrp.dealer.ui.LoginScreen
import com.hsrp.dealer.ui.MainScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HsrpTheme {
                val token by App.settings.token.collectAsState(initial = "")
                when (token) {
                    "" -> Unit // still reading settings
                    null -> LoginScreen()
                    else -> MainScaffold()
                }
            }
        }
    }
}
