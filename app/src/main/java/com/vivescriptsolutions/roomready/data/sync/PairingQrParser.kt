package com.vivescriptsolutions.roomready.data.sync

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.util.Locale

data class PairingQrPayload(
  val baseUrl: String,
  val apiKey: String,
) {
  override fun toString(): String = "PairingQrPayload(baseUrl=$baseUrl, apiKey=REDACTED)"
}

sealed interface PairingQrResult {
  data class Valid(val payload: PairingQrPayload) : PairingQrResult

  data object UpdateRequired : PairingQrResult

  data object InvalidCode : PairingQrResult
}

/**
 * Parses a RoomReady PMS pairing QR payload:
 * `roomready://connect?v=1&url=<url-encoded https base URL>&key=<rk.<id>.<secret>>`
 *
 * Unknown query parameters are ignored. Any version other than `1` returns
 * [PairingQrResult.UpdateRequired]; malformed payloads, non-HTTPS URLs, or
 * invalid keys return [PairingQrResult.InvalidCode]. Neither raw payloads
 * nor keys are ever logged.
 */
object PairingQrParser {
  private const val PREFIX = "roomready://connect?"
  // Secret alphabet is base64url (PMS: randomBytes(32)) plus tolerated
  // legacy chars; the trailing "-" MUST stay (base64url includes it).
  private val KEY_REGEX = Regex("""^rk\.[A-Za-z0-9_-]{1,64}\.[A-Za-z0-9_~+/-]{6,256}$""")

  fun parse(raw: String): PairingQrResult {
    val trimmed = raw.trim()
    if (!trimmed.startsWith(PREFIX)) return PairingQrResult.InvalidCode

    val rawQuery = trimmed.substringAfter(PREFIX).substringBefore('#')
    if (rawQuery.isBlank()) return PairingQrResult.InvalidCode

    val params = LinkedHashMap<String, String>()
    for (segment in rawQuery.split('&')) {
      if (segment.isEmpty()) continue
      val eq = segment.indexOf('=')
      if (eq <= 0) continue
      val name = percentDecode(segment.substring(0, eq)) ?: return PairingQrResult.InvalidCode
      val value = percentDecode(segment.substring(eq + 1))
      if (value == null) {
        if (name == "v" || name == "url" || name == "key") return PairingQrResult.InvalidCode
        continue
      }
      if (!params.containsKey(name)) {
        params[name] = value
      }
    }

    val versionRaw = params["v"]?.trim() ?: return PairingQrResult.InvalidCode
    if (versionRaw.isEmpty()) return PairingQrResult.InvalidCode
    if (versionRaw != "1") {
      return if (versionRaw.toIntOrNull() != null) {
        PairingQrResult.UpdateRequired
      } else {
        PairingQrResult.InvalidCode
      }
    }

    val rawUrl = params["url"]?.trim() ?: return PairingQrResult.InvalidCode
    val normalizedUrl = validatePmsBaseUrl(rawUrl) ?: return PairingQrResult.InvalidCode

    val rawKey = params["key"]?.trim() ?: return PairingQrResult.InvalidCode
    if (!KEY_REGEX.matches(rawKey)) return PairingQrResult.InvalidCode

    return PairingQrResult.Valid(
      PairingQrPayload(
        baseUrl = normalizedUrl,
        apiKey = rawKey,
      )
    )
  }

  private fun percentDecode(input: String): String? {
    if ('%' !in input) return input
    val out = ByteArrayOutputStream(input.length)
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      if (ch == '%') {
        if (i + 2 >= input.length) return null
        val hi = input[i + 1].digitToIntOrNull(16) ?: return null
        val lo = input[i + 2].digitToIntOrNull(16) ?: return null
        out.write((hi shl 4) or lo)
        i += 3
      } else {
        val bytes = ch.toString().toByteArray(StandardCharsets.UTF_8)
        out.write(bytes)
        i += 1
      }
    }
    return out.toString(StandardCharsets.UTF_8.name())
  }
}

/**
 * Shared PMS origin check for the QR and typed-entry paths: https only,
 * with a host, no credentials or fragment. Returns the normalized origin
 * (trailing slash trimmed) or null. Anything it rejects would either
 * fail or throw inside the HTTP client, so callers treat null as
 * "not a usable PMS address".
 */
internal fun validatePmsBaseUrl(rawUrl: String): String? {
  if (rawUrl.isEmpty() || rawUrl.any { it.isWhitespace() || it.isISOControl() }) return null
  val uri =
    try {
      URI(rawUrl)
    } catch (_: URISyntaxException) {
      return null
    }
  if (!uri.scheme.equals("https", ignoreCase = true)) return null
  if (uri.host.isNullOrBlank()) return null
  if (uri.userInfo != null) return null
  if (uri.fragment != null) return null
  val afterScheme = rawUrl.substringAfter("://")
  return ("https://$afterScheme").trimEnd('/')
}
