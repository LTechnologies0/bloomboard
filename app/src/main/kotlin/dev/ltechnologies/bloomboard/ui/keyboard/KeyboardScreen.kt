package dev.ltechnologies.bloomboard.ui.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.hid.HidUsage
import dev.ltechnologies.bloomboard.hid.KeyAction
import dev.ltechnologies.bloomboard.hid.glyphText
import dev.ltechnologies.bloomboard.hid.keyboardRows
import dev.ltechnologies.bloomboard.session.SessionService
import dev.ltechnologies.bloomboard.tv.TvStatus
import dev.ltechnologies.bloomboard.ui.discoverableIntent
import dev.ltechnologies.bloomboard.ui.emoji.emojiCategories
import dev.ltechnologies.bloomboard.ui.enableBluetoothIntent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyboardScreen(app: BloomboardApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings by app.preferences.settings.collectAsState()
    val hid by app.hid.state.collectAsState()
    val tv by app.tv.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var emojiOpen by remember { mutableStateOf(false) }
    var emojiPage by remember { mutableIntStateOf(0) }
    val discoverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val copied = stringResource(R.string.emoji_copied)
    val sent = stringResource(R.string.emoji_sent_tv)
    val typed = stringResource(R.string.emoji_unicode)
    val rows = remember { keyboardRows() }
    val shiftOn = hid.shiftHeld || hid.oneShotShift

    fun haptic() {
        if (settings.haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun syncSession() = SessionService.start(context).also {
        if (!app.preferences.settings.value.hidEnabled && !app.preferences.settings.value.tvActive) {
            context.stopService(android.content.Intent(context, SessionService::class.java))
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = when {
                    !hid.bluetoothOn && settings.hidEnabled -> stringResource(R.string.status_bluetooth_off)
                    hid.connectedName != null -> stringResource(R.string.status_connected, hid.connectedName ?: "")
                    settings.hidEnabled -> stringResource(R.string.status_ready)
                    else -> stringResource(R.string.status_disconnected)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            text = stringResource(R.string.layout_hint, settings.layout.title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                if (!hid.bluetoothOn) {
                    enableLauncher.launch(enableBluetoothIntent())
                    return@TextButton
                }
                app.preferences.update { it.copy(hidEnabled = !settings.hidEnabled) }
                syncSession()
            }) {
                Text(stringResource(if (settings.hidEnabled) R.string.action_disable_keyboard else R.string.action_enable_keyboard))
            }
            TextButton(onClick = { discoverLauncher.launch(discoverableIntent()) }, enabled = settings.hidEnabled) {
                Text(stringResource(R.string.action_make_discoverable))
            }
        }
        if (settings.hidEnabled && hid.bonded.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hid.bonded.forEach { device ->
                    AssistChip(
                        onClick = { app.hid.connectBonded(device.address) },
                        label = { Text(device.name) },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        val usage = (key.action as? KeyAction.Usage)?.code
                        val base = usage?.let { glyphText(settings.layout, it, shifted = false) }.orEmpty()
                        val letter = base.length == 1 && base[0].isLetter()
                        val showShift = if (letter && hid.caps) !shiftOn else shiftOn
                        val label = when (val action = key.action) {
                            is KeyAction.Usage -> glyphText(settings.layout, action.code, showShift).ifEmpty { " " }
                            KeyAction.Backspace -> "⌫"
                            KeyAction.Enter -> "⏎"
                            KeyAction.Tab -> "⇥"
                            KeyAction.Escape -> "Esc"
                            KeyAction.Shift -> "⇧"
                            KeyAction.Caps -> "⇪"
                            KeyAction.Space -> ""
                            KeyAction.Emoji -> "☺"
                            is KeyAction.Latch -> action.label
                            is KeyAction.Arrow -> action.label
                        }
                        val active = when (val action = key.action) {
                            KeyAction.Shift -> shiftOn
                            KeyAction.Caps -> hid.caps
                            is KeyAction.Latch -> hid.latched and action.bit != 0
                            else -> false
                        }
                        KeyCap(
                            label = label,
                            active = active,
                            popup = if (key.action is KeyAction.Usage || key.action is KeyAction.Space) label.ifEmpty { " " } else null,
                            modifier = Modifier.weight(key.weight),
                            onDown = {
                                haptic()
                                when (val action = key.action) {
                                    is KeyAction.Usage -> app.hid.keyDown(action.code)
                                    KeyAction.Backspace -> app.hid.keyDown(HidUsage.BACKSPACE)
                                    KeyAction.Enter -> app.hid.keyDown(HidUsage.ENTER)
                                    KeyAction.Tab -> app.hid.keyDown(HidUsage.TAB)
                                    KeyAction.Escape -> app.hid.keyDown(HidUsage.ESCAPE)
                                    KeyAction.Space -> app.hid.keyDown(HidUsage.SPACE)
                                    KeyAction.Shift -> app.hid.shiftDown()
                                    is KeyAction.Arrow -> app.hid.keyDown(action.code)
                                    else -> Unit
                                }
                            },
                            onUp = {
                                when (val action = key.action) {
                                    is KeyAction.Usage -> app.hid.keyUp(action.code)
                                    KeyAction.Backspace -> app.hid.keyUp(HidUsage.BACKSPACE)
                                    KeyAction.Enter -> app.hid.keyUp(HidUsage.ENTER)
                                    KeyAction.Tab -> app.hid.keyUp(HidUsage.TAB)
                                    KeyAction.Escape -> app.hid.keyUp(HidUsage.ESCAPE)
                                    KeyAction.Space -> app.hid.keyUp(HidUsage.SPACE)
                                    KeyAction.Shift -> app.hid.shiftUp()
                                    is KeyAction.Arrow -> app.hid.keyUp(action.code)
                                    KeyAction.Emoji -> emojiOpen = true
                                    KeyAction.Caps -> app.hid.toggleCaps()
                                    is KeyAction.Latch -> app.hid.toggleLatch(action.bit)
                                }
                            },
                            onLong = {
                                if (key.action == KeyAction.Shift) app.hid.toggleCaps()
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    if (emojiOpen) {
        ModalBottomSheet(onDismissRequest = { emojiOpen = false }) {
            Text(
                stringResource(R.string.emoji_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emojiCategories.forEachIndexed { index, category ->
                    FilterChip(
                        selected = emojiPage == index,
                        onClick = { emojiPage = index },
                        label = { Text(category.title) },
                    )
                }
            }
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                emojiCategories[emojiPage].symbols.forEach { symbol ->
                    AssistChip(
                        onClick = {
                            when {
                                tv.status == TvStatus.Connected -> {
                                    app.tv.sendText(symbol)
                                    scope.launch { snackbar.showSnackbar(sent) }
                                }
                                settings.linuxUnicode && hid.connectedName != null -> {
                                    app.hid.typeUnicode(symbol, settings.layout)
                                    scope.launch { snackbar.showSnackbar(typed) }
                                }
                                else -> {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("emoji", symbol))
                                    scope.launch { snackbar.showSnackbar(copied) }
                                }
                            }
                            emojiOpen = false
                        },
                        label = { Text(symbol) },
                    )
                }
            }
        }
    }
}
