package com.vivescriptsolutions.roomready

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.preferences.UserSession
import com.vivescriptsolutions.roomready.ui.about.AboutLinks
import com.vivescriptsolutions.roomready.ui.screens.MeScreen
import com.vivescriptsolutions.roomready.ui.theme.RoomReadyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * About/legal surface: the three canonical ViveReply URLs are exact,
 * the Me screen exposes them as tappable rows, and tapping fires a
 * plain ACTION_VIEW browser intent (no WebView, no in-app fetch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AboutLinksTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `about urls are the exact canonical vivereply tree`() {
    assertEquals("https://www.vivereply.com/roomready", AboutLinks.WEBSITE)
    assertEquals("https://www.vivereply.com/roomready/privacy", AboutLinks.PRIVACY_POLICY)
    assertEquals("https://www.vivereply.com/roomready/terms", AboutLinks.TERMS_OF_SERVICE)
  }

  @Test
  fun `me screen shows all three about links`() {
    composeTestRule.setContent {
      RoomReadyTheme {
        MeScreen(
          session = UserSession(),
          onSwitchRole = {},
          onSwitchStaffName = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("about_website_link").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("about_privacy_link").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("about_terms_link").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `tapping website fires an action_view browser intent`() {
    tapAboutLink("about_website_link")
    assertStartedBrowserIntent(AboutLinks.WEBSITE)
  }

  @Test
  fun `tapping privacy fires an action_view browser intent`() {
    tapAboutLink("about_privacy_link")
    assertStartedBrowserIntent(AboutLinks.PRIVACY_POLICY)
  }

  private fun tapAboutLink(testTag: String) {
    composeTestRule.setContent {
      RoomReadyTheme {
        MeScreen(
          session = UserSession(),
          onSwitchRole = {},
          onSwitchStaffName = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(testTag).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  private fun assertStartedBrowserIntent(expectedUrl: String) {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val started = Shadows.shadowOf(app).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, started.action)
    assertEquals(expectedUrl, started.data.toString())
  }
}
