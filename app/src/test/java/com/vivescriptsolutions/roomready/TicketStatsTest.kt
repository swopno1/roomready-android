package com.vivescriptsolutions.roomready

import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.data.repository.filterTicketsByStatus
import com.vivescriptsolutions.roomready.data.repository.urgentOpenByRoom
import com.vivescriptsolutions.roomready.data.repository.urgentOpenCount
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TicketStatsTest {
  private fun ticket(
    id: Long,
    roomId: Long,
    priority: TicketPriority,
    status: TicketStatus,
  ): TicketEntity =
    TicketEntity(
      id = id,
      roomId = roomId,
      roomName = "R$roomId",
      title = "T$id",
      priority = priority,
      status = status,
      createdBy = "Amina Rahman",
      createdAt = 1_700_000_000_000L,
    )

  private val sample =
    listOf(
      ticket(1, 10, TicketPriority.URGENT, TicketStatus.OPEN),
      ticket(2, 10, TicketPriority.NORMAL, TicketStatus.OPEN),
      ticket(3, 11, TicketPriority.URGENT, TicketStatus.OPEN),
      ticket(4, 11, TicketPriority.URGENT, TicketStatus.DONE),
      ticket(5, 12, TicketPriority.LOW, TicketStatus.DONE),
    )

  @Test
  fun `urgent open count ignores done and non-urgent`() {
    assertEquals(2, urgentOpenCount(sample))
    assertEquals(0, urgentOpenCount(emptyList()))
  }

  @Test
  fun `urgent open by room groups correctly`() {
    assertEquals(mapOf(10L to 1, 11L to 1), urgentOpenByRoom(sample))
    assertEquals(emptyMap<Long, Int>(), urgentOpenByRoom(emptyList()))
  }

  @Test
  fun `filter by status`() {
    assertEquals(listOf(1L, 2L, 3L), filterTicketsByStatus(sample, TicketStatus.OPEN).map { it.id })
    assertEquals(listOf(4L, 5L), filterTicketsByStatus(sample, TicketStatus.DONE).map { it.id })
    assertEquals(5, filterTicketsByStatus(sample, null).size)
  }
}
