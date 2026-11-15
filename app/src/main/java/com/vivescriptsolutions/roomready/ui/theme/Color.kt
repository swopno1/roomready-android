package com.vivescriptsolutions.roomready.ui.theme

import androidx.compose.ui.graphics.Color
import com.vivescriptsolutions.roomready.domain.RoomStatus
import com.vivescriptsolutions.roomready.domain.TicketPriority

// Warm, calm boutique hotel palette (strictly flat, no gradients)
val WarmAlabaster = Color(0xFFFBF8F3)
val WarmIvory = Color(0xFFFFFFFF)
val WarmSandstone = Color(0xFFF3EDE2)
val WarmOatmeal = Color(0xFFEAE1D3)
val WarmBorder = Color(0xFFDDD4C6)

val BotanicalSage = Color(0xFF2F5241)
val BotanicalSageContainer = Color(0xFFDCE8E0)
val OnBotanicalSageContainer = Color(0xFF162E22)

val WarmTeak = Color(0xFF7A5C43)
val WarmTeakContainer = Color(0xFFEFE3D6)
val OnWarmTeakContainer = Color(0xFF332214)

val EspressoInk = Color(0xFF221F1B)
val WarmSlateInk = Color(0xFF635B50)

// Room status colors (Dirty=red-brown, Cleaning=amber, Cleaned=blue, Ready=green, Out of Service=grey)
val StatusDirtyBg = Color(0xFF8C3B2B)
val StatusDirtyTint = Color(0xFFF6E5E0)
val StatusDirtyTintInk = Color(0xFF6E2B1E)

val StatusCleaningBg = Color(0xFFA65E0A)
val StatusCleaningTint = Color(0xFFFDF1DB)
val StatusCleaningTintInk = Color(0xFF7A4404)

val StatusCleanedBg = Color(0xFF24639E)
val StatusCleanedTint = Color(0xFFE2EEF8)
val StatusCleanedTintInk = Color(0xFF184774)

val StatusReadyBg = Color(0xFF276E40)
val StatusReadyTint = Color(0xFFE2F1E6)
val StatusReadyTintInk = Color(0xFF194E2C)

val StatusOutOfServiceBg = Color(0xFF635E58)
val StatusOutOfServiceTint = Color(0xFFECE9E4)
val StatusOutOfServiceTintInk = Color(0xFF47433E)

data class StatusColorSpec(
  val chipBackground: Color,
  val chipContent: Color,
  val softTintBackground: Color,
  val softTintContent: Color,
)

fun RoomStatus.colorSpec(): StatusColorSpec =
  when (this) {
    RoomStatus.DIRTY ->
      StatusColorSpec(
        chipBackground = StatusDirtyBg,
        chipContent = Color.White,
        softTintBackground = StatusDirtyTint,
        softTintContent = StatusDirtyTintInk,
      )
    RoomStatus.CLEANING ->
      StatusColorSpec(
        chipBackground = StatusCleaningBg,
        chipContent = Color.White,
        softTintBackground = StatusCleaningTint,
        softTintContent = StatusCleaningTintInk,
      )
    RoomStatus.CLEANED ->
      StatusColorSpec(
        chipBackground = StatusCleanedBg,
        chipContent = Color.White,
        softTintBackground = StatusCleanedTint,
        softTintContent = StatusCleanedTintInk,
      )
    RoomStatus.READY ->
      StatusColorSpec(
        chipBackground = StatusReadyBg,
        chipContent = Color.White,
        softTintBackground = StatusReadyTint,
        softTintContent = StatusReadyTintInk,
      )
    RoomStatus.OUT_OF_SERVICE ->
      StatusColorSpec(
        chipBackground = StatusOutOfServiceBg,
        chipContent = Color.White,
        softTintBackground = StatusOutOfServiceTint,
        softTintContent = StatusOutOfServiceTintInk,
      )
  }

fun TicketPriority.badgeColors(): Pair<Color, Color> =
  when (this) {
    TicketPriority.LOW -> StatusOutOfServiceTint to StatusOutOfServiceTintInk
    TicketPriority.NORMAL -> StatusCleanedTint to StatusCleanedTintInk
    TicketPriority.URGENT -> StatusDirtyTint to StatusDirtyTintInk
  }
