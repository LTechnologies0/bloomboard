package dev.ltechnologies.bloomboard.ui.touchpad

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.hid.MouseButton
import kotlin.math.abs

@Composable
fun TouchpadScreen(app: BloomboardApp, modifier: Modifier = Modifier) {
    val settings by app.preferences.settings.collectAsState()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.touchpad_hint), style = MaterialTheme.typography.bodyMedium)
        Text(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(settings.sensitivity) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        var moved = 0f
                        var fingers = 1
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            fingers = maxOf(fingers, pressed.size)
                            val now = pressed.map { it.position }.let { points ->
                                androidx.compose.ui.geometry.Offset(
                                    points.map { it.x }.average().toFloat(),
                                    points.map { it.y }.average().toFloat(),
                                )
                            }
                            val delta = now - last
                            last = now
                            moved += abs(delta.x) + abs(delta.y)
                            val gain = settings.sensitivity * 1.6f
                            if (pressed.size >= 2) {
                                val wheel = (-delta.y / 28f).toInt()
                                if (wheel != 0) app.hid.mouse(0, 0, wheel)
                            } else if (abs(delta.x) + abs(delta.y) > 0.4f) {
                                app.hid.mouse((delta.x * gain).toInt(), (delta.y * gain).toInt(), 0)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (moved < 12f) {
                            app.hid.click(if (fingers >= 2) MouseButton.RIGHT else MouseButton.LEFT)
                        }
                    }
                },
            text = "",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { app.hid.click(MouseButton.LEFT) }, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.touchpad_left))
            }
            Button(onClick = { app.hid.click(MouseButton.RIGHT) }, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.touchpad_right))
            }
        }
    }
}
