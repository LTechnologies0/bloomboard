package dev.ltechnologies.bloomboard.ui.remote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.session.SessionService
import dev.ltechnologies.bloomboard.tv.AtvDirection
import dev.ltechnologies.bloomboard.tv.AtvKey
import dev.ltechnologies.bloomboard.tv.TvEndpoint
import dev.ltechnologies.bloomboard.tv.TvStatus
import dev.ltechnologies.bloomboard.ui.keyboard.KeyCap

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvRemoteScreen(app: BloomboardApp, modifier: Modifier = Modifier) {
    val tv by app.tv.state.collectAsState()
    val settings by app.preferences.settings.collectAsState()
    var host by remember(settings.tvHost) { mutableStateOf(settings.tvHost) }
    var pin by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun guarded(action: () -> Unit) {
        val publicHost = runCatching { TvEndpoint.isPublicAddress(TvEndpoint.normalize(host)) }.getOrDefault(false)
        if (publicHost) warn = action else action()
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (tv.status) {
                TvStatus.Idle -> stringResource(R.string.tv_status_idle)
                TvStatus.Connecting -> stringResource(R.string.tv_status_connecting)
                TvStatus.Pairing -> stringResource(R.string.tv_status_pairing)
                TvStatus.NeedPin -> stringResource(R.string.tv_status_pin)
                TvStatus.Connected -> stringResource(R.string.tv_status_connected, if (tv.detail.isBlank()) "" else " · ${tv.detail}")
                TvStatus.Error -> tv.detail.ifBlank { stringResource(R.string.tv_status_error) }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (tv.volume.isNotBlank()) Text(tv.volume, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.tv_host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                guarded {
                    app.tv.pair(host)
                }
            }) { Text(stringResource(R.string.tv_pair)) }
            Button(onClick = {
                guarded {
                    app.preferences.update { it.copy(tvHost = host.trim(), tvActive = true) }
                    SessionService.start(context)
                    app.tv.connect(host)
                }
            }) { Text(stringResource(R.string.tv_connect)) }
            OutlinedButton(onClick = {
                app.tv.disconnect()
                if (!app.preferences.settings.value.hidEnabled) {
                    context.stopService(android.content.Intent(context, SessionService::class.java))
                }
            }) { Text(stringResource(R.string.tv_disconnect)) }
        }
        TextButton(onClick = { if (tv.discovering) app.tv.stopDiscovery() else app.tv.discover() }) {
            Text(stringResource(if (tv.discovering) R.string.tv_discovering else R.string.tv_discover))
        }
        if (tv.discovered.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tv.discovered.forEach { found ->
                    OutlinedButton(onClick = { host = found }) { Text(found) }
                }
            }
        }
        if (tv.status == TvStatus.NeedPin) {
            Text(stringResource(R.string.tv_pin_help))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(6).uppercase() },
                label = { Text(stringResource(R.string.tv_pin)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { app.tv.submitPin(pin); pin = "" }, enabled = pin.length == 6) {
                Text(stringResource(R.string.tv_confirm))
            }
        }
        RemotePad(onKey = { code, direction -> app.tv.key(code, direction) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { app.tv.launch("com.google.android.youtube.tv") }) {
                Text(stringResource(R.string.remote_youtube))
            }
            OutlinedButton(onClick = { app.tv.launch("com.netflix.ninja") }) {
                Text(stringResource(R.string.remote_netflix))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.tv_text_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = { app.tv.sendText(text); text = "" }) { Text(stringResource(R.string.tv_send)) }
        }
    }

    warn?.let { action ->
        AlertDialog(
            onDismissRequest = { warn = null },
            text = { Text(stringResource(R.string.tv_public_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    warn = null
                    action()
                }) { Text(stringResource(R.string.tv_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { warn = null }) { Text(stringResource(R.string.tv_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemotePad(onKey: (Int, Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteButton(stringResource(R.string.remote_power), AtvKey.POWER, onKey)
            RemoteButton(stringResource(R.string.remote_home), AtvKey.HOME, onKey)
            RemoteButton(stringResource(R.string.remote_back), AtvKey.BACK, onKey)
            RemoteButton(stringResource(R.string.remote_menu), AtvKey.MENU, onKey)
        }
        RemoteButton("↑", AtvKey.DPAD_UP, onKey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton("←", AtvKey.DPAD_LEFT, onKey)
            RemoteButton(stringResource(R.string.remote_ok), AtvKey.DPAD_CENTER, onKey, round = true)
            RemoteButton("→", AtvKey.DPAD_RIGHT, onKey)
        }
        RemoteButton("↓", AtvKey.DPAD_DOWN, onKey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteButton("−", AtvKey.VOLUME_DOWN, onKey)
            RemoteButton(stringResource(R.string.remote_mute), AtvKey.VOLUME_MUTE, onKey)
            RemoteButton("+", AtvKey.VOLUME_UP, onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteButton(stringResource(R.string.remote_prev), AtvKey.MEDIA_PREVIOUS, onKey)
            RemoteButton(stringResource(R.string.remote_play), AtvKey.MEDIA_PLAY_PAUSE, onKey)
            RemoteButton(stringResource(R.string.remote_next), AtvKey.MEDIA_NEXT, onKey)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..9).forEach { digit ->
                RemoteButton(digit.toString(), AtvKey.N0 + digit, onKey, compact = true)
            }
            RemoteButton("0", AtvKey.N0, onKey, compact = true)
        }
    }
}

@Composable
private fun RemoteButton(
    label: String,
    keyCode: Int,
    onKey: (Int, Int) -> Unit,
    round: Boolean = false,
    compact: Boolean = false,
) {
    val side = when {
        round -> 84.dp
        compact -> 48.dp
        else -> 72.dp
    }
    KeyCap(
        label = label,
        active = round,
        popup = null,
        modifier = Modifier.size(side),
        onDown = {},
        onUp = { long ->
            if (!long) onKey(keyCode, AtvDirection.SHORT)
            else onKey(keyCode, AtvDirection.END_LONG)
        },
        onLong = { onKey(keyCode, AtvDirection.START_LONG) },
    )
}
