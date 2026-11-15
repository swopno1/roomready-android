package com.vivescriptsolutions.roomready.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.vivescriptsolutions.roomready.MainActivity
import com.vivescriptsolutions.roomready.R

/**
 * Local notifications only: inspection returns, urgent tickets, and
 * stuck-clean reminders. Posting checks POST_NOTIFICATIONS first, so a
 * denied permission is silently off — never an error, never a nag.
 */
class RoomReadyNotifier(
  private val appContext: Context,
  private val canPostCheck: (() -> Boolean)? = null,
) {
  private val manager: NotificationManager
    get() = appContext.getSystemService()!!

  fun ensureChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ATTENTION,
        appContext.getString(R.string.notif_channel_attention),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply { description = appContext.getString(R.string.notif_channel_attention_desc) }
    )
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_REMINDERS,
        appContext.getString(R.string.notif_channel_reminders),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply { description = appContext.getString(R.string.notif_channel_reminders_desc) }
    )
  }

  fun canPost(): Boolean =
    canPostCheck?.invoke()
      ?: (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
          appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
        )

  fun notify(event: NotificationEvent) {
    if (!canPost()) return
    val content = notificationContent(appContext, event)
    val deepLink =
      when (event) {
        is NotificationEvent.RoomReturned -> RoomDeepLink.Room(event.roomId)
        is NotificationEvent.UrgentTicket -> RoomDeepLink.Ticket(event.ticketId)
        is NotificationEvent.StuckCleaning -> RoomDeepLink.Room(event.roomId)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        appContext,
        notificationId(event),
        deepLinkIntent(deepLink),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(appContext, content.channelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(content.title)
        .setContentText(content.text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    manager.notify(notificationId(event), notification)
  }

  fun deepLinkIntent(event: NotificationEvent): Intent =
    deepLinkIntent(
      when (event) {
        is NotificationEvent.RoomReturned -> RoomDeepLink.Room(event.roomId)
        is NotificationEvent.UrgentTicket -> RoomDeepLink.Ticket(event.ticketId)
        is NotificationEvent.StuckCleaning -> RoomDeepLink.Room(event.roomId)
      }
    )

  private fun deepLinkIntent(link: RoomDeepLink): Intent =
    Intent(appContext, MainActivity::class.java).apply {
      action = DEEP_LINK_ACTION
      when (link) {
        is RoomDeepLink.Room -> {
          putExtra(DEEP_LINK_KIND, DEEP_LINK_KIND_ROOM)
          putExtra(DEEP_LINK_REF_ID, link.roomId)
        }
        is RoomDeepLink.Ticket -> {
          putExtra(DEEP_LINK_KIND, DEEP_LINK_KIND_TICKET)
          putExtra(DEEP_LINK_REF_ID, link.ticketId)
        }
      }
    }

  private fun notificationId(event: NotificationEvent): Int {
    val type =
      when (event) {
        is NotificationEvent.RoomReturned -> 1
        is NotificationEvent.UrgentTicket -> 2
        is NotificationEvent.StuckCleaning -> 3
      }
    val ref =
      when (event) {
        is NotificationEvent.RoomReturned -> event.roomId
        is NotificationEvent.UrgentTicket -> event.ticketId
        is NotificationEvent.StuckCleaning -> event.roomId
      }
    return type * 1_000_000 + (ref % 1_000_000).toInt()
  }

  companion object {
    const val CHANNEL_ATTENTION = "roomready_attention"
    const val CHANNEL_REMINDERS = "roomready_reminders"
  }
}
