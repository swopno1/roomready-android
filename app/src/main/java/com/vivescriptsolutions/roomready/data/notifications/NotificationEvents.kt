package com.vivescriptsolutions.roomready.data.notifications

import android.content.Context
import android.content.Intent
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.data.local.ChecklistRunEntity

/** Local-only notification triggers. No FCM, no server push. */
sealed interface NotificationEvent {
  data class RoomReturned(
    val roomId: Long,
    val roomName: String,
    val reason: String,
  ) : NotificationEvent

  data class UrgentTicket(
    val ticketId: Long,
    val roomId: Long,
    val roomName: String,
    val title: String,
  ) : NotificationEvent

  data class StuckCleaning(
    val roomId: Long,
    val roomName: String,
    val elapsedMinutes: Long,
  ) : NotificationEvent
}

/** Where a notification tap lands. */
sealed interface RoomDeepLink {
  data class Room(val roomId: Long) : RoomDeepLink

  data class Ticket(val ticketId: Long) : RoomDeepLink
}

const val DEEP_LINK_ACTION = "com.vivescriptsolutions.roomready.OPEN_DEEP_LINK"
const val DEEP_LINK_KIND = "roomready.deep_link_kind"
const val DEEP_LINK_REF_ID = "roomready.deep_link_ref_id"
const val DEEP_LINK_KIND_ROOM = "room"
const val DEEP_LINK_KIND_TICKET = "ticket"

/** Pure intent parser so tests can prove tap targets without launching. */
fun parseDeepLink(intent: Intent): RoomDeepLink? {
  val refId = intent.getLongExtra(DEEP_LINK_REF_ID, -1L).takeIf { it > 0 } ?: return null
  return when (intent.getStringExtra(DEEP_LINK_KIND)) {
    DEEP_LINK_KIND_ROOM -> RoomDeepLink.Room(refId)
    DEEP_LINK_KIND_TICKET -> RoomDeepLink.Ticket(refId)
    else -> null
  }
}

data class NotificationContent(
  val channelId: String,
  val title: String,
  val text: String,
)

/** Exact user-visible copy per event (tested). */
fun notificationContent(context: Context, event: NotificationEvent): NotificationContent =
  when (event) {
    is NotificationEvent.RoomReturned ->
      NotificationContent(
        channelId = RoomReadyNotifier.CHANNEL_ATTENTION,
        title = context.getString(R.string.notif_returned_title, event.roomName),
        text = context.getString(R.string.notif_returned_text, event.roomName, event.reason),
      )
    is NotificationEvent.UrgentTicket ->
      NotificationContent(
        channelId = RoomReadyNotifier.CHANNEL_ATTENTION,
        title = context.getString(R.string.notif_urgent_title, event.roomName),
        text = context.getString(R.string.notif_urgent_text, event.title),
      )
    is NotificationEvent.StuckCleaning ->
      NotificationContent(
        channelId = RoomReadyNotifier.CHANNEL_REMINDERS,
        title = context.getString(R.string.notif_stuck_title, event.roomName),
        text = context.getString(R.string.notif_stuck_text),
      )
  }

const val STUCK_AFTER_MINUTES = 60L

/**
 * Pure stuck-episode check: unfinished runs older than 60 minutes that
 * have not been notified yet. One reminder per episode.
 */
fun findStuckRunIds(
  runs: List<ChecklistRunEntity>,
  nowMillis: Long,
  alreadyNotified: Set<Long>,
): List<Long> =
  runs
    .filter { run ->
      run.finishedAt == null &&
        nowMillis - run.startedAt > STUCK_AFTER_MINUTES * 60_000L &&
        run.id !in alreadyNotified
    }
    .map { it.id }
