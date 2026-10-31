package dev.ltechnologies.bloomboard.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.ui.keyboard.KeyboardScreen
import dev.ltechnologies.bloomboard.ui.remote.TvRemoteScreen
import dev.ltechnologies.bloomboard.ui.settings.SettingsScreen
import dev.ltechnologies.bloomboard.ui.touchpad.TouchpadScreen

@Composable
fun BloomboardRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as BloomboardApp
    var page by rememberSaveable { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
    val pages = listOf(
        stringResource(R.string.nav_keyboard) to "⌨",
        stringResource(R.string.nav_touchpad) to "◻",
        stringResource(R.string.nav_tv) to "▣",
        stringResource(R.string.nav_settings) to "⚙",
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                pages.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = page == index,
                        onClick = { page = index },
                        icon = { Text(icon, fontSize = 18.sp) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (page) {
            0 -> KeyboardScreen(app, modifier)
            1 -> TouchpadScreen(app, modifier)
            2 -> TvRemoteScreen(app, modifier)
            else -> SettingsScreen(app, modifier)
        }
    }
}

fun discoverableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
    120,
)

fun enableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
