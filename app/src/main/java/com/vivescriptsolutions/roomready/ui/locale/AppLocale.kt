package com.vivescriptsolutions.roomready.ui.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.vivescriptsolutions.roomready.domain.AppLanguage
import java.util.Locale

/** True when the UI is currently rendered in Bengali. */
val LocalBanglaDigits = staticCompositionLocalOf { false }

/**
 * Wraps [this] so resources resolve in [language]. SYSTEM returns the
 * context untouched (the device locale decides).
 */
fun Context.wrapWithLanguage(language: AppLanguage): Context {
  val locale =
    when (language) {
      AppLanguage.ENGLISH -> Locale.ENGLISH
      AppLanguage.BANGLA -> Locale("bn")
      AppLanguage.SYSTEM -> return this
    }
  val config = Configuration(resources.configuration)
  config.setLocale(locale)
  return createConfigurationContext(config)
}

/** Whether UI text (and digits) should render in Bengali. */
fun isBanglaUi(language: AppLanguage): Boolean =
  when (language) {
    AppLanguage.BANGLA -> true
    AppLanguage.ENGLISH -> false
    AppLanguage.SYSTEM -> Locale.getDefault().language == "bn"
  }

/** Maps ASCII 0–9 to Bengali ০–৯; every other character passes through. */
fun withBanglaDigits(text: String): String {
  if (text.isEmpty()) return text
  val out = StringBuilder(text.length)
  for (char in text) {
    if (char in '0'..'9') {
      out.append(('০'.code + (char - '0')).toChar())
    } else {
      out.append(char)
    }
  }
  return out.toString()
}

/**
 * Renders [text] with Bengali digits when the UI is Bengali, unchanged
 * otherwise. Apply to counts, timers, and room numbers at the call site.
 */
@Composable
fun localizedDigits(text: String): String =
  if (LocalBanglaDigits.current) withBanglaDigits(text) else text

/**
 * Digit-aware string resource: resolves [res] and maps digits when the
 * UI is Bengali. Use for every user-visible string that can carry a
 * count, a timer, or a room number.
 */
@Composable
fun digitString(
  @StringRes res: Int,
  vararg args: Any,
): String = localizedDigits(stringResource(res, *args))

fun Context.findActivity(): Activity? {
  var current: Context? = this
  while (current is ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}
