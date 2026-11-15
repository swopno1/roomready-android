package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.data.sync.PairingQrParser
import com.vivescriptsolutions.roomready.data.sync.PairingQrPayload
import com.vivescriptsolutions.roomready.data.sync.PairingQrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingQrParserTest {
  @Test
  fun `valid payload parses https url and rk key`() {
    val result =
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      )
    assertEquals(
      PairingQrResult.Valid(
        PairingQrPayload(
          baseUrl = "https://pms.example.com",
          apiKey = "rk.k1.Ab3def99",
        )
      ),
      result,
    )
  }

  @Test
  fun `wrong version returns UpdateRequired`() {
    assertEquals(
      PairingQrResult.UpdateRequired,
      PairingQrParser.parse(
        "roomready://connect?v=2&url=https%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      ),
    )
    assertEquals(
      PairingQrResult.UpdateRequired,
      PairingQrParser.parse(
        "roomready://connect?v=0&url=https%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      ),
    )
  }

  @Test
  fun `http or non-https url is rejected`() {
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse(
        "roomready://connect?v=1&url=http%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      ),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse(
        "roomready://connect?v=1&url=ftp%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      ),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2F&key=rk.k1.Ab3def99"
      ),
    )
  }

  @Test
  fun `missing or short or malformed key is rejected`() {
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com"),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com&key="),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com&key=rk.k1.ab"),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com&key=rk.k1"),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com&key=pk.k1.Ab3def99"
      ),
    )
  }

  @Test
  fun `plain https url is rejected`() {
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("https://pms.example.com/admin"),
    )
  }

  @Test
  fun `random text and other schemes are rejected`() {
    assertEquals(PairingQrResult.InvalidCode, PairingQrParser.parse("just some random text"))
    assertEquals(PairingQrResult.InvalidCode, PairingQrParser.parse(""))
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse("otherapp://connect?v=1&url=https%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"),
    )
    assertEquals(
      PairingQrResult.InvalidCode,
      PairingQrParser.parse(
        "roomready://connect.evil.com?v=1&url=https%3A%2F%2Fpms.example.com&key=rk.k1.Ab3def99"
      ),
    )
  }

  @Test
  fun `extra unknown parameters are ignored`() {
    val result =
      PairingQrParser.parse(
        "roomready://connect?v=1&property=Bayview&url=https%3A%2F%2Fpms.example.com&flag&key=rk.k1.Ab3def99&ts=1731680000"
      )
    assertEquals(
      PairingQrResult.Valid(
        PairingQrPayload(
          baseUrl = "https://pms.example.com",
          apiKey = "rk.k1.Ab3def99",
        )
      ),
      result,
    )
  }

  @Test
  fun `url-encoded values with port path and plus-preserving secret decode correctly`() {
    val result =
      PairingQrParser.parse(
        "roomready://connect?v=1&url=https%3A%2F%2Fpms.example.com%3A8443%2Fresort%2F&key=rk.k2.Sec%2Bret123"
      )
    assertTrue(result is PairingQrResult.Valid)
    val payload = (result as PairingQrResult.Valid).payload
    assertEquals("https://pms.example.com:8443/resort", payload.baseUrl)
    assertEquals("rk.k2.Sec+ret123", payload.apiKey)
    assertFalse(payload.toString().contains("rk.k2.Sec+ret123"))
  }
}
