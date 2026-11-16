package com.vivescriptsolutions.roomready.ui.screens

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vivescriptsolutions.roomready.R
import com.vivescriptsolutions.roomready.ui.about.AboutLinks
import com.vivescriptsolutions.roomready.ui.components.CalmSectionCard
import com.vivescriptsolutions.roomready.ui.components.SectionHeading

/**
 * About/legal card for the Me screen. Each row fires a plain
 * ACTION_VIEW intent to the system browser (no WebView, no new
 * permission) and carries no user data.
 */
@Composable
fun AboutCard(modifier: Modifier = Modifier) {
  CalmSectionCard(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionHeading(text = stringResource(R.string.about_title))
      AboutLinkRow(
        labelRes = R.string.about_website,
        testTag = "about_website_link",
        url = AboutLinks.WEBSITE,
      )
      AboutLinkRow(
        labelRes = R.string.about_privacy,
        testTag = "about_privacy_link",
        url = AboutLinks.PRIVACY_POLICY,
      )
      AboutLinkRow(
        labelRes = R.string.about_terms,
        testTag = "about_terms_link",
        url = AboutLinks.TERMS_OF_SERVICE,
      )
    }
  }
}

@Composable
private fun AboutLinkRow(
  @StringRes labelRes: Int,
  testTag: String,
  url: String,
) {
  val context = LocalContext.current
  TextButton(
    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(testTag),
  ) {
    Text(
      text = stringResource(labelRes),
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}
