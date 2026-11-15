package com.vivescriptsolutions.roomready.data.repository

import com.vivescriptsolutions.roomready.data.local.TicketEntity
import com.vivescriptsolutions.roomready.domain.TicketPriority
import com.vivescriptsolutions.roomready.domain.TicketStatus

fun urgentOpenCount(tickets: List<TicketEntity>): Int =
  tickets.count { it.status == TicketStatus.OPEN && it.priority == TicketPriority.URGENT }

fun urgentOpenByRoom(tickets: List<TicketEntity>): Map<Long, Int> =
  tickets
    .filter { it.status == TicketStatus.OPEN && it.priority == TicketPriority.URGENT }
    .groupingBy { it.roomId }
    .eachCount()

fun filterTicketsByStatus(
  tickets: List<TicketEntity>,
  status: TicketStatus?,
): List<TicketEntity> =
  if (status == null) tickets else tickets.filter { it.status == status }
