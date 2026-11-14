package com.minigames.gridscape

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.minigames.core.rendering.LocalMiniGamesColors
import com.minigames.core.rendering.MiniGamesTheme
import com.minigames.core.storage.MiniGamesStorage
import com.minigames.gridscape.ui.GridscapeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val storage = remember { MiniGamesStorage.create(applicationContext) }
            MiniGamesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalMiniGamesColors.current.background,
                ) {
                    GridscapeApp(storage = storage)
                }
            }
        }
    }
}
