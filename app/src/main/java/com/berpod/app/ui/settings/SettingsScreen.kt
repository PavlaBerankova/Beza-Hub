package com.berpod.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berpod.app.R
import com.berpod.app.ui.dot.DotTitle
import com.berpod.app.ui.dot.SectionSeparator
import com.berpod.app.ui.theme.DotTheme
import com.berpod.app.ui.theme.DotType
import com.berpod.app.ui.theme.LocalThemeController

/** The Nastavení tab: appearance now, more later; each group has its own separator. */
@Composable
fun SettingsScreen() {
    val c = DotTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DotTitle(stringResource(R.string.nav_settings))

        SettingsGroup(stringResource(R.string.settings_appearance)) {
            val theme = LocalThemeController.current
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dark_mode),
                hint = stringResource(R.string.settings_dark_mode_hint),
                checked = theme.isDark,
                onCheckedChange = { theme.toggle() }
            )
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_app_name), style = DotType.title, color = c.text)
                Text(stringResource(R.string.settings_app_tagline), style = DotType.body, color = c.textMuted)
            }
            if (version.isNotEmpty()) {
                SettingsValueRow(stringResource(R.string.settings_version), version)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionSeparator(title)
        content()
    }
}

@Composable
private fun SettingsSwitchRow(title: String, hint: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = DotTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = DotType.rowTitle.copy(fontWeight = FontWeight.W700), color = c.text)
            if (hint != null) Text(hint, style = DotType.body.copy(fontSize = 13.sp), color = c.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textMuted,
                uncheckedTrackColor = c.background,
                uncheckedBorderColor = c.outline
            )
        )
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    val c = DotTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DotType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
        Text(value, style = DotType.rowTitle, color = c.textMuted)
    }
}
