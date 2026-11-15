package com.vivescriptsolutions.roomready

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vivescriptsolutions.roomready.data.preferences.UserPreferencesRepository
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.ui.locale.isBanglaUi
import com.vivescriptsolutions.roomready.ui.locale.withBanglaDigits
import com.vivescriptsolutions.roomready.ui.locale.wrapWithLanguage
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P8 locale gate: per-app language wrapping, Bangla-UI detection,
 * Bengali digit mapping, the language preference round-trip, and the
 * fixed PMS contract-step display mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLocaleTest {
  @Test
  fun `wrap english sets the en locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val wrapped = context.wrapWithLanguage(AppLanguage.ENGLISH)
    assertEquals("en", wrapped.resources.configuration.locales[0].language)
  }

  @Test
  fun `wrap bangla sets the bn locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val wrapped = context.wrapWithLanguage(AppLanguage.BANGLA)
    assertEquals("bn", wrapped.resources.configuration.locales[0].language)
  }

  @Test
  fun `wrap system leaves the context untouched`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(context, context.wrapWithLanguage(AppLanguage.SYSTEM))
  }

  @Test
  fun `bangla choice always means bangla UI`() {
    assertTrue(isBanglaUi(AppLanguage.BANGLA))
  }

  @Test
  fun `english choice never means bangla UI`() {
    assertFalse(isBanglaUi(AppLanguage.ENGLISH))
  }

  @Test
  fun `system choice follows the device locale`() {
    val previous = Locale.getDefault()
    try {
      Locale.setDefault(Locale("bn"))
      assertTrue(isBanglaUi(AppLanguage.SYSTEM))
      Locale.setDefault(Locale.ENGLISH)
      assertFalse(isBanglaUi(AppLanguage.SYSTEM))
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun `digit mapping converts every ascii digit`() {
    assertEquals("০১২৩৪৫৬৭৮৯", withBanglaDigits("0123456789"))
  }

  @Test
  fun `digit mapping leaves other characters alone`() {
    assertEquals("Room ১০১ · ৪:৩০ PM", withBanglaDigits("Room 101 · 4:30 PM"))
    assertEquals("", withBanglaDigits(""))
  }

  @Test
  fun `language preference round-trips`() {
    runBlocking {
      val prefs =
        UserPreferencesRepository(ApplicationProvider.getApplicationContext())
      prefs.setAppLanguage(AppLanguage.BANGLA)
      assertEquals(AppLanguage.BANGLA, prefs.appLanguage())
      prefs.setAppLanguage(AppLanguage.ENGLISH)
      assertEquals(AppLanguage.ENGLISH, prefs.appLanguage())
      prefs.setAppLanguage(AppLanguage.SYSTEM)
      assertEquals(AppLanguage.SYSTEM, prefs.appLanguage())
    }
  }

  @Test
  fun `fixed PMS contract steps resolve to display strings`() {
    val expected =
      mapOf(
        "Change bed linen" to R.string.step_pms_bed_linen,
        "Fresh towels & toiletries" to R.string.step_pms_towels,
        "Vacuum & mop floors" to R.string.step_pms_floors,
        "Bathroom deep clean" to R.string.step_pms_bathroom,
        "Check minibar & restock" to R.string.step_pms_minibar,
        "AC, lights & sockets check" to R.string.step_pms_ac_power,
        "Balcony / veranda tidy" to R.string.step_pms_balcony,
        "Final walkthrough" to R.string.step_pms_walkthrough,
      )
    expected.forEach { (sentence, res) ->
      assertEquals(sentence, res, ChecklistTemplates.labelResForKey(sentence))
    }
  }

  @Test
  fun `unknown step sentences still fall back to raw text`() {
    assertEquals(null, ChecklistTemplates.labelResForKey("Some future PMS step"))
  }
}
