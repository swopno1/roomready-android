package com.vivescriptsolutions.roomready.ui.about

/**
 * Static brand/legal links for the Me screen's About card. These are
 * the only first-party URLs in shipped source: tapping one fires a
 * plain ACTION_VIEW intent to the system browser (no WebView, no new
 * permission) and carries no user data — the PMS sync path never
 * touches them.
 */
object AboutLinks {
  const val WEBSITE = "https://www.vivereply.com/roomready"
  const val PRIVACY_POLICY = "https://www.vivereply.com/roomready/privacy"
  const val TERMS_OF_SERVICE = "https://www.vivereply.com/roomready/terms"
}
