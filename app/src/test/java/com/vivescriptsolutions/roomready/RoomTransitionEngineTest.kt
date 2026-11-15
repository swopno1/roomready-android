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
      setOf(RoomStatus.INSPECTED, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.CLEANING, StaffRole.HOUSEKEEPER)
        .map { it.toStatus }
        .toSet(),
    )
    assertTrue(
      RoomTransitionEngine.legalActionsFor(RoomStatus.INSPECTED, StaffRole.HOUSEKEEPER).isEmpty()
    )
    assertEquals(
      setOf(RoomStatus.DIRTY),
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
      setOf(RoomStatus.READY, RoomStatus.DIRTY, RoomStatus.OUT_OF_SERVICE),
      RoomTransitionEngine.legalActionsFor(RoomStatus.INSPECTED, StaffRole.SUPERVISOR)
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
        RoomStatus.INSPECTED,
        RoomStatus.READY,
        StaffRole.HOUSEKEEPER,
      )
    )
  }
}
