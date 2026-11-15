package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.domain.ChecklistRunKind
import com.vivescriptsolutions.roomready.domain.ChecklistStepKey
import com.vivescriptsolutions.roomready.domain.ChecklistStepState
import com.vivescriptsolutions.roomready.domain.ChecklistTemplates
import com.vivescriptsolutions.roomready.domain.StayoverStepKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTemplatesTest {
  @Test
  fun `full template has 8 steps in canonical order`() {
    assertEquals(
      listOf(
        "bathroom",
        "beds_linen",
        "dusting",
        "floors",
        "amenities_restock",
        "fridge_minibar",
        "windows_curtains",
        "final_look",
      ),
      ChecklistStepKey.standardTemplateKeys,
    )
    assertEquals(ChecklistTemplates.FULL_STEP_COUNT, ChecklistStepKey.standardTemplateKeys.size)
  }

  @Test
  fun `full template builds 8 indexed states with exact titles`() {
    val steps = ChecklistTemplates.buildSteps(ChecklistStepKey.standardTemplateKeys)
    assertEquals(
      listOf(
        "Bathroom",
        "Beds & linen",
        "Dusting",
        "Floors",
        "Amenities restock",
        "Fridge/minibar",
        "Windows & curtains",
        "Final look",
      ),
      steps.map { it.title },
    )
    assertEquals((0..7).toList(), steps.map { it.stepIndex })
    assertTrue(steps.none { it.isDone })
    assertTrue(steps.all { it.note.isEmpty() })
  }

  @Test
  fun `stayover template has exactly the 4 light steps`() {
    assertEquals(
      listOf("stayover_bathroom_quick", "stayover_beds_made", "stayover_towels", "stayover_bins"),
      StayoverStepKey.templateKeys,
    )
    val steps = ChecklistTemplates.buildSteps(StayoverStepKey.templateKeys)
    assertEquals(
      listOf("Bathroom quick", "Beds made", "Towels", "Bins"),
      steps.map { it.title },
    )
    assertEquals(ChecklistTemplates.STAYOVER_STEP_COUNT, steps.size)
  }

  @Test
  fun `every template key resolves to a label resource`() {
    (ChecklistStepKey.standardTemplateKeys + StayoverStepKey.templateKeys).forEach { key ->
      assertNotNull("missing label for $key", ChecklistTemplates.labelResForKey(key))
    }
  }

  @Test
  fun `template selection follows run kind and falls back for empty room type`() {
    assertEquals(
      ChecklistStepKey.standardTemplateKeys,
      ChecklistTemplates.templateKeysFor(ChecklistRunKind.FULL, ChecklistStepKey.standardTemplateKeys),
    )
    assertEquals(
      ChecklistStepKey.standardTemplateKeys,
      ChecklistTemplates.templateKeysFor(ChecklistRunKind.FULL, emptyList()),
    )
    assertEquals(
      StayoverStepKey.templateKeys,
      ChecklistTemplates.templateKeysFor(ChecklistRunKind.STAYOVER, ChecklistStepKey.standardTemplateKeys),
    )
  }

  private fun states(done: Int, total: Int): List<ChecklistStepState> =
    List(total) { index ->
      ChecklistStepState(stepIndex = index, key = "k$index", title = "t$index", isDone = index < done)
    }

  @Test
  fun `finish allowed only at full completion`() {
    assertTrue(ChecklistTemplates.canFinish(states(done = 8, total = 8)))
    assertFalse(ChecklistTemplates.canFinish(states(done = 7, total = 8)))
    assertTrue(ChecklistTemplates.canFinish(states(done = 4, total = 4)))
    assertFalse(ChecklistTemplates.canFinish(states(done = 3, total = 4)))
    assertFalse(ChecklistTemplates.canFinish(emptyList()))
  }

  @Test
  fun `elapsed formatting`() {
    assertEquals("0:00", ChecklistTemplates.formatElapsed(0L))
    assertEquals("0:05", ChecklistTemplates.formatElapsed(5_000L))
    assertEquals("1:01", ChecklistTemplates.formatElapsed(61_000L))
    assertEquals("12:34", ChecklistTemplates.formatElapsed(754_000L))
    assertEquals("1:01:01", ChecklistTemplates.formatElapsed(3_661_000L))
  }
}
