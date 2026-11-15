package com.vivescriptsolutions.roomready

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vivescriptsolutions.roomready.data.notifications.enqueueStuckReminders
import com.vivescriptsolutions.roomready.data.notifications.parseDeepLink
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
    // Idempotent (KEEP): safe across config-change re-creation. Lives here
    // rather than Application.onCreate so unit tests never need WorkManager.
    enqueueStuckReminders(this)
    forwardDeepLink(intent)
    setContent {
      RoomReadyTheme {
        RoomReadyApp(
          repository = appContainer.housekeepingRepository,
          connectivityMonitor = appContainer.connectivityMonitor,
          syncRepository = appContainer.syncRepository,
          deepLinkChannel = appContainer.deepLinkChannel,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    forwardDeepLink(intent)
  }

  private fun forwardDeepLink(intent: Intent?) {
    if (intent == null) return
    parseDeepLink(intent)?.let { link ->
      (application as RoomReadyApplication).container.deepLinkChannel.trySend(link)
    }
  }
}
