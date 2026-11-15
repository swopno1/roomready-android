package com.vivescriptsolutions.roomready

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vivescriptsolutions.roomready.ui.navigation.RoomReadyApp
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    val appContainer = (application as RoomReadyApplication).container
    setContent {
      RoomReadyTheme {
        RoomReadyApp(
          repository = appContainer.housekeepingRepository,
          connectivityMonitor = appContainer.connectivityMonitor,
        )
      }
    }
  }
}
