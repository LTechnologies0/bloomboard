package dev.ltechnologies.bloomboard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.BuildConfig
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.hid.LayoutId

@Composable
fun SettingsScreen(app: BloomboardApp, modifier: Modifier = Modifier) {
    val settings by app.preferences.settings.collectAsState()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.settings_layout),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Text(
            stringResource(R.string.settings_layout_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LayoutId.entries.forEach { layout ->
            ListItem(
                headlineContent = { Text(layout.title) },
                leadingContent = {
                    RadioButton(selected = settings.layout == layout, onClick = null)
                },
                modifier = Modifier.selectable(
                    selected = settings.layout == layout,
                    role = Role.RadioButton,
                    onClick = { app.preferences.update { it.copy(layout = layout) } },
                ),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_haptic)) },
            trailingContent = {
                Switch(checked = settings.haptic, onCheckedChange = { checked ->
                    app.preferences.update { it.copy(haptic = checked) }
                })
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_keep_screen)) },
            trailingContent = {
                Switch(checked = settings.keepScreenOn, onCheckedChange = { checked ->
                    app.preferences.update { it.copy(keepScreenOn = checked) }
                })
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_unicode)) },
            supportingContent = { Text(stringResource(R.string.settings_unicode_help)) },
            trailingContent = {
                Switch(checked = settings.linuxUnicode, onCheckedChange = { checked ->
                    app.preferences.update { it.copy(linuxUnicode = checked) }
                })
            },
        )
        Text(
            stringResource(R.string.settings_sensitivity),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Slider(
            value = settings.sensitivity,
            onValueChange = { value -> app.preferences.update { it.copy(sensitivity = value) } },
            valueRange = 0.4f..2.8f,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
