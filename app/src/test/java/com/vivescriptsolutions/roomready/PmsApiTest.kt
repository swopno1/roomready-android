package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.data.sync.ApiOutcome
import com.vivescriptsolutions.roomready.data.sync.PmsApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PmsApiTest {
  private fun apiWithBase(baseUrl: String): PmsApi =
    PmsApi(
      baseUrlProvider = { baseUrl },
      keyProvider = { "rk.k1.secret" },
      client = OkHttpClient.Builder().build(),
    )

  @Test
  fun `unparseable stored url returns malformed on get instead of throwing`() {
    val outcome = runBlocking { apiWithBase("https://demo 13.example").bootstrap() }
    assertTrue(outcome is ApiOutcome.Malformed)
  }

  @Test
  fun `unparseable stored url returns malformed on post instead of throwing`() {
    val outcome =
      runBlocking {
        apiWithBase("https://demo 13.example").postStatus(
          unitId = "u1",
          expected = "DIRTY",
          action = "MARK_CLEAN",
          staffName = "Rahim",
          note = null,
        )
      }
    assertTrue(outcome is ApiOutcome.Malformed)
  }

  @Test
  fun `unparseable stored url returns malformed on patch instead of throwing`() {
    val outcome =
      runBlocking {
        apiWithBase("https://demo 13.example").patchTicket(id = "t1", status = "DONE", staffName = "Karim")
      }
    assertTrue(outcome is ApiOutcome.Malformed)
  }
}
