package com.vivescriptsolutions.roomready

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * P8 language gate: Bengali must cover every English string — zero
 * missing keys, zero blanks, matching format placeholders — and every
 * user-facing sentence must actually be translated (identical text is
 * allowed only for the explicit allowlist below).
 */
class LanguageCompletenessTest {
  private val enStrings = loadStrings("values")
  private val bnStrings = loadStrings("values-bn")

  @Test
  fun `bn covers every en key`() {
    val missing = enStrings.keys - bnStrings.keys
    assertTrue("Missing in values-bn: $missing", missing.isEmpty())
    val extra = bnStrings.keys - enStrings.keys
    assertTrue("Extra in values-bn (stale?): $extra", extra.isEmpty())
  }

  @Test
  fun `no blank strings in either language`() {
    val blankEn = enStrings.filterValues { it.isBlank() }.keys
    val blankBn = bnStrings.filterValues { it.isBlank() }.keys
    assertTrue("Blank en strings: $blankEn", blankEn.isEmpty())
    assertTrue("Blank bn strings: $blankBn", blankBn.isEmpty())
  }

  @Test
  fun `format placeholders match between en and bn`() {
    val mismatched =
      enStrings.keys.intersect(bnStrings.keys).filter { key ->
        placeholders(enStrings.getValue(key)) != placeholders(bnStrings.getValue(key))
      }
    assertTrue("Placeholder mismatch in: $mismatched", mismatched.isEmpty())
  }

  @Test
  fun `bn text is actually translated outside the allowlist`() {
    val identical =
      enStrings.keys.intersect(bnStrings.keys).filter { key ->
        enStrings.getValue(key) == bnStrings.getValue(key) && key !in IDENTICAL_ALLOWLIST
      }
    assertTrue("Untranslated bn strings: $identical", identical.isEmpty())
  }

  private fun loadStrings(qualifier: String): Map<String, String> {
    val file =
      listOf(
        File("src/main/res/$qualifier/strings.xml"),
        File("app/src/main/res/$qualifier/strings.xml"),
      ).firstOrNull { it.isFile }
        ?: error("Cannot find $qualifier/strings.xml from ${System.getProperty("user.dir")}")
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val out = mutableMapOf<String, String>()
    val nodes = doc.documentElement.getElementsByTagName("string")
    for (i in 0 until nodes.length) {
      val el = nodes.item(i) as Element
      out[el.getAttribute("name")] = el.textContent.trim()
    }
    assertTrue("No strings found in $file", out.isNotEmpty())
    return out
  }

  private fun placeholders(text: String): List<String> =
    PLACEHOLDER_REGEX.findAll(text).map { it.value }.sorted().toList()

  companion object {
    private val PLACEHOLDER_REGEX = Regex("""%(\d+\$)?[sdf]""")

    /**
     * Strings deliberately identical in both languages: the brand name,
     * punctuation-only formats, the demo URL, the key hint (a key
     * prefix, not a word), and language names (always shown in their
     * own script so users can recognize them). Everything else must be
     * translated.
     */
    private val IDENTICAL_ALLOWLIST =
      setOf(
        "app_name",
        "filter_count_format",
        "floor_unknown",
        "room_meta_format",
        "status_log_transition_format",
        "status_log_by_format",
        "staff_role_header_format",
        "ticket_meta_format",
        "dashboard_log_room_format",
        "notif_urgent_text",
        "connection_url_hint",
        "connection_key_hint",
        "language_english",
        "language_bangla",
      )
  }
}
