package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.RoomTransitionEngine
import com.vivescriptsolutions.roomready.domain.StaffRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTransitionEngineTest {
  @Test
  fun `housekeeper legal transitions match specification`() {
    assertEquals(
      setOf(RoomStatus.CLEANING, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.DIRTY, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
    assertEquals(
      setOf(RoomStatus.CLEANED, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.CLEANING, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
    // Soft gating (P5): both roles can act on cleaned rooms; the UI leads
    // each role to its own queue instead of hiding actions.
    assertEquals(
      setOf(RoomStatus.READY, RoomStatus.CLEANING, RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.CLEANED, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
    assertEquals(
      setOf(RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.READY, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
    assertEquals(
      setOf(RoomStatus.DIRTY),
      RoomTransitionEngine.legalActionsFor(RoomStatus.OUT_OF_SERVICE, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
  }

  @Test
  fun `supervisor legal transitions include inspection approval`() {
    assertEquals(
      setOf(RoomStatus.READY, RoomStatus.CLEANING, RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.CLEANED, StaffRole.SUPERVISOR)
        .map { it.toStatus }
        .toSet(),
    )
  }

  @Test
  fun `full matrix every illegal transition rejected for every role`() {
    // The complete legal set, quoted from the transition table. All 75
    // (from x to x role) combos are checked: housekeepers and supervisors
    // share the table (soft gating), SYSTEM can do nothing.
    val legal: Map<RoomStatus, Set<RoomStatus>> =
      mapOf(
        RoomStatus.DIRTY to setOf(RoomStatus.CLEANING, RoomStatus.OUT_OF_SERVICE),
        RoomStatus.CLEANING to setOf(RoomStatus.CLEANED, RoomStatus.OUT_OF_SERVICE),
        RoomStatus.CLEANED to
          setOf(RoomStatus.READY, RoomStatus.CLEANING, RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
        RoomStatus.READY to setOf(RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
        RoomStatus.OUT_OF_SERVICE to setOf(RoomStatus.DIRTY),
      )
    var checked = 0
    var illegal = 0
    RoomStatus.entries.forEach { from ->
      RoomStatus.entries.forEach { to ->
        StaffRole.entries.forEach { role ->
          val expected = role != StaffRole.SYSTEM && to in legal.getValue(from)
          assertEquals(
            "from=$from to=$to role=$role",
            expected,
            RoomTransitionEngine.isLegalTransition(from, to, role),
          )
          checked++
          if (!expected) illegal++
        }
      }
    }
    assertEquals(75, checked)
    // 11 legal x 2 roles = 22 legal; the other 53 are rejected.
    assertEquals(53, illegal)
  }

  @Test
  fun `listed actions match legality for every status and role`() {
    RoomStatus.entries.forEach { from ->
      StaffRole.entries.forEach { role ->
        val listed =
          RoomTransitionEngine.legalActionsFor(from, role).map { it.toStatus }.toSet()
        val legal =
          RoomStatus.entries.filter { to ->
            RoomTransitionEngine.isLegalTransition(from, to, role)
          }.toSet()
        assertEquals("from=$from role=$role", legal, listed)
      }
    }
  }

  @Test
  fun `illegal shortcuts are rejected`() {
    assertFalse(
      RoomTransitionEngine.isLegalTransition(
        RoomStatus.DIRTY,
        RoomStatus.READY,
        StaffRole.SUPERVISOR,
      )
    )
    assertFalse(
      RoomTransitionEngine.isLegalTransition(
        RoomStatus.CLEANING,
        RoomStatus.READY,
        StaffRole.SUPERVISOR,
      )
    )
    assertFalse(
      RoomTransitionEngine.isLegalTransition(
        RoomStatus.OUT_OF_SERVICE,
        RoomStatus.READY,
        StaffRole.SUPERVISOR,
      )
    )
    assertFalse(
      RoomTransitionEngine.isLegalTransition(
        RoomStatus.READY,
        RoomStatus.CLEANING,
        StaffRole.HOUSEKEEPER,
      )
    )
  }
}
