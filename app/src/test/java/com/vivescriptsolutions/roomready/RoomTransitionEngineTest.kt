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
