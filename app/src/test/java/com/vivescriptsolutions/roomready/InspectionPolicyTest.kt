package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.domain.ReturnReason
import com.vivescriptsolutions.roomready.domain.failedStepKeysForReason
import com.vivescriptsolutions.roomready.domain.formatReturnReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionPolicyTest {
  @Test
  fun `bathroom reason reopens bathroom steps on both templates`() {
    assertEquals(
      setOf("bathroom", "stayover_bathroom_quick"),
      failedStepKeysForReason(ReturnReason.BATHROOM),
    )
  }

  @Test
  fun `beds reason reopens bed steps on both templates`() {
    assertEquals(
      setOf("beds_linen", "stayover_beds_made"),
      failedStepKeysForReason(ReturnReason.BEDS),
    )
  }

  @Test
  fun `dusting reason reopens dusting only`() {
    assertEquals(setOf("dusting"), failedStepKeysForReason(ReturnReason.DUSTING))
  }

  @Test
  fun `amenities reason reopens restock steps`() {
    assertEquals(
      setOf("amenities_restock", "stayover_towels", "stayover_bins"),
      failedStepKeysForReason(ReturnReason.AMENITIES),
    )
  }

  @Test
  fun `other reason reopens nothing`() {
    assertTrue(failedStepKeysForReason(ReturnReason.OTHER).isEmpty())
  }

  @Test
  fun `reason display combines chip label with optional note`() {
    assertEquals("Bathroom", formatReturnReason("Bathroom", ""))
    assertEquals("Bathroom", formatReturnReason("Bathroom", "   "))
    assertEquals("Beds: pillowcases missing", formatReturnReason("Beds", "  pillowcases missing  "))
  }
}
