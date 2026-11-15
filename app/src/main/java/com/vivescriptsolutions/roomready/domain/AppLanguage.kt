package com.vivescriptsolutions.roomready.domain

/**
 * Per-app language choice. SYSTEM follows the device locale; ENGLISH
 * and BANGLA pin the app to that language.
 */
enum class AppLanguage {
  SYSTEM,
  ENGLISH,
  BANGLA,
}
