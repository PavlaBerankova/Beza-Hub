package com.berpod.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.berpod.app.ui.navigation.BerpodApp
import com.berpod.app.ui.theme.BerpodTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as BerpodApplication).container

        setContent {
            BerpodTheme {
                BerpodApp(container = container)
            }
        }
    }
}
