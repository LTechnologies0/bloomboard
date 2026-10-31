package dev.ltechnologies.bloomboard.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import dev.ltechnologies.bloomboard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class BondedDevice(val address: String, val name: String)

data class HidState(
    val permissionGranted: Boolean = false,
    val bluetoothOn: Boolean = false,
    val registered: Boolean = false,
    val connectedName: String? = null,
    val bonded: List<BondedDevice> = emptyList(),
    val caps: Boolean = false,
    val shiftHeld: Boolean = false,
    val oneShotShift: Boolean = false,
    val latched: Int = 0,
    val failure: String? = null,
)

class HidController(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(HidState())
    val state: StateFlow<HidState> = _state.asStateFlow()

    private val lock = Any()
    private var profile: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val pressed = LinkedHashSet<Int>()
    private var shiftHeld = false
    private var oneShotShift = false
    private var shiftUsed = false
    private var latched = 0
    private var caps = false
    private var buttons = 0
    private val started = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshBonded()
        }
    }

    fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (!hasConnectPermission()) {
            started.set(false)
            _state.update { it.copy(permissionGranted = false, failure = context.getString(R.string.error_permission)) }
            return
        }
        _state.update { it.copy(permissionGranted = true, failure = null) }
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            fail(context.getString(R.string.error_bluetooth))
            return
        }
        _state.update { it.copy(bluetoothOn = adapter.isEnabled) }
        refreshBonded()
        val connected = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                val hid = proxy as BluetoothHidDevice
                profile = hid
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    context.getString(R.string.app_name),
                    "Keyboard and touchpad",
                    "Bloomboard",
                    BluetoothHidDevice.SUBCLASS1_COMBO,
                    HidReports.DESCRIPTOR,
                )
                val qos = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                    800,
                    9,
                    0,
                    11250,
                    BluetoothHidDeviceAppQosSettings.MAX,
                )
                val ok = hid.registerApp(sdp, qos, qos, executor, callback)
                if (!ok) fail(context.getString(R.string.error_bluetooth))
            }

            override fun onServiceDisconnected(profileId: Int) {
                profile = null
                host = null
                _state.update { it.copy(registered = false, connectedName = null) }
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!connected) fail(context.getString(R.string.error_bluetooth))
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { context.unregisterReceiver(receiver) }
        val hid = profile
        profile = null
        host = null
        synchronized(lock) {
            pressed.clear()
            shiftHeld = false
            oneShotShift = false
            latched = 0
        }
        runCatching { hid?.unregisterApp() }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (hid != null && adapter != null) {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        }
        _state.update {
            it.copy(registered = false, connectedName = null, shiftHeld = false, oneShotShift = false, latched = 0)
        }
    }

    fun connectBonded(address: String) {
        if (!hasConnectPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = adapter.bondedDevices?.firstOrNull { it.address == address } ?: return
        profile?.connect(device)
    }

    fun keyDown(usage: Int) {
        executor.execute {
            synchronized(lock) {
                if (shiftHeld || oneShotShift) shiftUsed = true
                pressed.add(usage)
                sendLocked()
            }
        }
    }

    fun keyUp(usage: Int) {
        executor.execute {
            synchronized(lock) {
                pressed.remove(usage)
                if (pressed.isEmpty() && !shiftHeld) oneShotShift = false
                sendLocked()
                publishModifiers()
            }
        }
    }

    fun shiftDown() {
        executor.execute {
            synchronized(lock) {
                shiftHeld = true
                shiftUsed = false
                sendLocked()
                publishModifiers()
            }
        }
    }

    fun shiftUp() {
        executor.execute {
            synchronized(lock) {
                shiftHeld = false
                if (!shiftUsed) oneShotShift = !oneShotShift
                if (pressed.isEmpty()) {
                    // A tap toggles the one-shot highlight. A chord releases it with the key.
                } else {
                    oneShotShift = false
                }
                sendLocked()
                publishModifiers()
            }
        }
    }

    fun toggleCaps() {
        executor.execute {
            synchronized(lock) {
                shiftUsed = true
                oneShotShift = false
                caps = !caps
                publishModifiers()
                tapLocked(HidUsage.CAPS_LOCK, 0)
            }
        }
    }

    fun toggleLatch(bit: Int) {
        executor.execute {
            synchronized(lock) {
                latched = latched xor bit
                sendLocked()
                publishModifiers()
            }
        }
    }

    fun mouse(dx: Int, dy: Int, wheel: Int, buttons: Int = this.buttons) {
        executor.execute {
            synchronized(lock) {
                this.buttons = buttons
                sendMouseLocked(dx, dy, wheel)
            }
        }
    }

    fun click(button: Int) {
        executor.execute {
            synchronized(lock) {
                buttons = button
                sendMouseLocked(0, 0, 0)
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                buttons = 0
                sendMouseLocked(0, 0, 0)
            }
        }
    }

    fun typeUnicode(text: String, layout: LayoutId) {
        executor.execute {
            text.codePoints().forEach { codePoint ->
                val u = usageForLabel(layout, "u") ?: return@forEach
                val mods = HidMod.LEFT_CTRL or HidMod.LEFT_SHIFT or (if (u.shift) HidMod.LEFT_SHIFT else 0)
                synchronized(lock) { tapLocked(u.usage, mods) }
                codePoint.toString(16).forEach { digit ->
                    val resolved = usageForLabel(layout, digit.toString()) ?: return@forEach
                    val extra = if (resolved.shift) HidMod.LEFT_SHIFT else 0
                    synchronized(lock) { tapLocked(resolved.usage, extra) }
                }
                synchronized(lock) { tapLocked(HidUsage.SPACE, 0) }
            }
        }
    }

    private fun tapLocked(usage: Int, extraMods: Int) {
        sendRaw(extraMods, listOf(usage))
        try {
            Thread.sleep(18)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        sendLocked()
    }

    private fun sendLocked() {
        sendRaw(currentMods(), pressed.toList())
    }

    private fun currentMods(): Int {
        var mods = latched
        if (shiftHeld || oneShotShift) mods = mods or HidMod.LEFT_SHIFT
        return mods
    }

    private fun sendRaw(modifiers: Int, keys: List<Int>) {
        val device = host ?: return
        val hid = profile ?: return
        hid.sendReport(device, HidReports.KEYBOARD_ID, HidReports.keyboardReport(modifiers, keys))
    }

    private fun sendMouseLocked(dx: Int, dy: Int, wheel: Int) {
        val device = host ?: return
        val hid = profile ?: return
        var remainingX = dx
        var remainingY = dy
        var remainingWheel = wheel
        do {
            val stepX = remainingX.coerceIn(-127, 127)
            val stepY = remainingY.coerceIn(-127, 127)
            val stepWheel = remainingWheel.coerceIn(-127, 127)
            hid.sendReport(
                device,
                HidReports.MOUSE_ID,
                HidReports.mouseReport(buttons, stepX, stepY, stepWheel),
            )
            remainingX -= stepX
            remainingY -= stepY
            remainingWheel -= stepWheel
        } while (remainingX != 0 || remainingY != 0 || remainingWheel != 0)
    }

    private fun publishModifiers() {
        _state.update {
            it.copy(caps = caps, shiftHeld = shiftHeld, oneShotShift = oneShotShift, latched = latched)
        }
    }

    private fun refreshBonded() {
        if (!hasConnectPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val devices = adapter?.bondedDevices.orEmpty().map { device ->
            BondedDevice(device.address, device.name ?: device.address)
        }.sortedBy { it.name }
        _state.update { it.copy(bluetoothOn = adapter?.isEnabled == true, bonded = devices, permissionGranted = true) }
    }

    private fun fail(message: String) {
        _state.update { it.copy(failure = message, registered = false) }
    }

    private fun applyLeds(data: ByteArray) {
        if (data.isEmpty()) return
        caps = data[0].toInt() and 0x02 != 0
        publishModifiers()
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            _state.update { it.copy(registered = registered) }
            if (registered) refreshBonded()
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (device == null) return
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    synchronized(lock) {
                        pressed.clear()
                        sendLocked()
                    }
                    _state.update { it.copy(connectedName = device.name ?: device.address, failure = null) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host?.address == device.address) host = null
                    _state.update { it.copy(connectedName = null) }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            val hid = profile ?: return
            if (device == null) return
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt() == HidReports.KEYBOARD_ID) {
                val report = synchronized(lock) { HidReports.keyboardReport(currentMods(), pressed.toList()) }
                hid.replyReport(device, type, id, report)
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            if (id.toInt() == HidReports.KEYBOARD_ID && data != null) {
                synchronized(lock) { applyLeds(data) }
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            if (reportId.toInt() == HidReports.KEYBOARD_ID && data != null) {
                synchronized(lock) { applyLeds(data) }
            }
        }
    }
}
