package dev.ltechnologies.bloomboard.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dev.ltechnologies.bloomboard.R
import dev.ltechnologies.bloomboard.data.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import kotlin.coroutines.cancellation.CancellationException

enum class TvStatus { Idle, Connecting, Pairing, NeedPin, Connected, Error }

data class TvState(
    val status: TvStatus = TvStatus.Idle,
    val host: String = "",
    val detail: String = "",
    val discovered: List<String> = emptyList(),
    val discovering: Boolean = false,
    val volume: String = "",
)

class TvRemoteController(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    private val identity = AtvIdentity()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(TvState(host = preferences.settings.value.tvHost))
    val state: StateFlow<TvState> = _state.asStateFlow()

    private var commandJob: Job? = null
    private var pairJob: Job? = null
    private var pinWait: CompletableDeferred<String>? = null
    private var output: OutputStream? = null
    private var commandSocket: SSLSocket? = null
    private val writeLock = Any()
    private var activeFeatures = AtvFeature.CLIENT
    private var imeCounter = 0
    private var fieldCounter = 0
    private var fieldCaret = 0
    private var nsdManager: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null

    fun pair(host: String) {
        val normalized = normalizeOrShow(host) ?: return
        preferences.update { it.copy(tvHost = normalized, tvActive = false) }
        stopCommand()
        pairJob?.cancel()
        val deferred = CompletableDeferred<String>()
        pinWait = deferred
        _state.update { it.copy(status = TvStatus.Pairing, host = normalized, detail = "", volume = "") }
        pairJob = scope.launch {
            try {
                pairSession(normalized, deferred)
            } catch (_: CancellationException) {
                _state.update { it.copy(status = TvStatus.Idle, detail = "") }
            } catch (_: BadPin) {
                preferences.update { it.copy(tvActive = false) }
                _state.update { it.copy(status = TvStatus.Error, detail = context.getString(R.string.error_pin)) }
            } catch (_: Exception) {
                preferences.update { it.copy(tvActive = false) }
                _state.update { it.copy(status = TvStatus.Error, detail = context.getString(R.string.error_tv_connect)) }
            } finally {
                if (pinWait === deferred) pinWait = null
            }
        }
    }

    fun submitPin(pin: String) {
        pinWait?.complete(pin.trim())
    }

    fun cancelPairing() {
        pairJob?.cancel()
        pinWait?.cancel()
        pinWait = null
    }

    fun connect(host: String, fromPairing: Boolean = false) {
        val normalized = normalizeOrShow(host) ?: return
        preferences.update { it.copy(tvHost = normalized, tvActive = true) }
        if (!fromPairing) pairJob?.cancel()
        startCommand(normalized)
    }

    fun disconnect() {
        preferences.update { it.copy(tvActive = false) }
        pairJob?.cancel()
        stopCommand()
        _state.update { it.copy(status = TvStatus.Idle, detail = "", volume = "") }
    }

    fun restore() {
        val settings = preferences.settings.value
        if (!settings.tvActive || settings.tvHost.isBlank()) return
        if (commandJob?.isActive == true) return
        startCommand(settings.tvHost)
    }

    fun key(keyCode: Int, direction: Int = AtvDirection.SHORT) {
        if (_state.value.status != TvStatus.Connected) return
        send(AtvMessages.keyInject(keyCode, direction))
    }

    fun launch(packageName: String) {
        if (!packageName.matches(PACKAGE)) return
        send(AtvMessages.appLink("market://launch?id=$packageName"))
    }

    fun sendText(text: String) {
        val clean = text.take(400)
        if (clean.isEmpty() || _state.value.status != TvStatus.Connected) return
        imeCounter += 1
        val start = fieldCaret
        send(AtvMessages.imeInsert(imeCounter, fieldCounter, start, start, clean))
        fieldCaret += clean.length
    }

    fun discover() {
        if (discovery != null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        nsdManager = manager
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        multicast = wifi?.createMulticastLock("bloomboard-tv")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _state.update { it.copy(discovering = true) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(manager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) {
                _state.update { it.copy(discovering = false) }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discovery = listener
        manager.discoverServices(AtvMessages.NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        scope.launch {
            delay(12_000)
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager
        val listener = discovery
        discovery = null
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        _state.update { it.copy(discovering = false) }
    }

    private fun startCommand(host: String) {
        stopCommand()
        _state.update { it.copy(status = TvStatus.Connecting, host = host, detail = "") }
        commandJob = scope.launch {
            var backoff = 500L
            while (isActive && preferences.settings.value.tvActive) {
                try {
                    commandSession(host)
                    backoff = 500L
                } catch (_: CancellationException) {
                    break
                } catch (_: NeedPairing) {
                    preferences.update { it.copy(tvActive = false) }
                    _state.update { it.copy(status = TvStatus.Error, detail = context.getString(R.string.tv_pin_help)) }
                    break
                } catch (_: Exception) {
                    if (!preferences.settings.value.tvActive) break
                    _state.update { it.copy(status = TvStatus.Error, detail = context.getString(R.string.error_tv_connect)) }
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(30_000)
                }
            }
        }
    }

    private fun stopCommand() {
        commandJob?.cancel()
        commandJob = null
        output = null
        runCatching { commandSocket?.close() }
        commandSocket = null
    }

    private fun commandSession(host: String) {
        val socket = open(host, AtvMessages.COMMAND_PORT, requirePin = true)
        commandSocket = socket
        output = socket.outputStream
        try {
            val input = socket.inputStream
            while (preferences.settings.value.tvActive) {
                when (val incoming = AtvMessages.parseRemote(readPrefixed(input))) {
                    is RemoteIncoming.Configure -> {
                        activeFeatures = AtvFeature.CLIENT and incoming.features
                        send(AtvMessages.configure(activeFeatures))
                        val label = listOf(incoming.vendor, incoming.model).filter { it.isNotBlank() }.joinToString(" ")
                        _state.update { it.copy(status = TvStatus.Connected, host = host, detail = label) }
                    }
                    RemoteIncoming.SetActive -> send(AtvMessages.setActive(activeFeatures))
                    is RemoteIncoming.Ping -> send(AtvMessages.pingResponse(incoming.value))
                    is RemoteIncoming.Started -> _state.update { it.copy(status = TvStatus.Connected, host = host) }
                    is RemoteIncoming.Volume -> {
                        val text = if (incoming.max > 0) "${incoming.level}/${incoming.max}" else ""
                        _state.update { it.copy(volume = text) }
                    }
                    is RemoteIncoming.TextField -> {
                        fieldCounter = incoming.counter
                        fieldCaret = incoming.end
                    }
                    RemoteIncoming.Other -> Unit
                }
            }
        } finally {
            output = null
            runCatching { socket.close() }
            if (commandSocket === socket) commandSocket = null
        }
    }

    private suspend fun pairSession(host: String, pinWait: CompletableDeferred<String>) {
        val socket = open(host, AtvMessages.PAIR_PORT, requirePin = false)
        try {
            val pairOut = socket.outputStream
            val input = socket.inputStream
            write(pairOut, AtvMessages.pairingRequest("Bloomboard"))
            while (true) {
                when (AtvMessages.parsePairing(readPrefixed(input)).kind) {
                    PairingKind.RequestAck -> write(pairOut, AtvMessages.pairingOptions())
                    PairingKind.Options -> write(pairOut, AtvMessages.pairingConfiguration())
                    PairingKind.ConfigurationAck -> break
                    PairingKind.Error -> throw BadPin()
                    else -> throw IllegalStateException("pairing")
                }
            }
            _state.update { it.copy(status = TvStatus.NeedPin, host = host) }
            val pin = withTimeout(170_000) { pinWait.await() }
            val server = socket.session.peerCertificates.first() as X509Certificate
            val serverKey = server.publicKey as? RSAPublicKey ?: throw IllegalStateException("certificate")
            val client = identity.clientRsa()
            val secret = try {
                PairingSecret.hash(
                    client.modulus,
                    client.publicExponent,
                    serverKey.modulus,
                    serverKey.publicExponent,
                    pin,
                )
            } catch (_: IllegalArgumentException) {
                throw BadPin()
            }
            write(pairOut, AtvMessages.pairingSecret(secret))
            val ack = AtvMessages.parsePairing(readPrefixed(input))
            if (ack.kind != PairingKind.SecretAck || ack.status != AtvMessages.STATUS_OK) throw BadPin()
            preferences.setPin(host, PinningTrust.hex(PinningTrust.sha256(server)))
        } finally {
            runCatching { socket.close() }
        }
        connect(host, fromPairing = true)
    }

    private fun open(host: String, port: Int, requirePin: Boolean): SSLSocket {
        val pin = if (requirePin) preferences.pinFor(host)?.let(PinningTrust::unhex) else null
        if (requirePin && pin == null) throw NeedPairing()
        val (ssl, _) = identity.sslContext(pin)
        val socket = ssl.socketFactory.createSocket() as SSLSocket
        socket.soTimeout = if (port == AtvMessages.PAIR_PORT) 180_000 else 20_000
        socket.connect(InetSocketAddress(host, port), 8_000)
        val protocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
        if (protocols.isNotEmpty()) socket.enabledProtocols = protocols.toTypedArray()
        socket.startHandshake()
        return socket
    }

    private fun send(message: ByteArray) {
        val current = output ?: return
        write(current, message)
    }

    private fun write(stream: OutputStream, message: ByteArray) {
        synchronized(writeLock) {
            stream.write(frame(message))
            stream.flush()
        }
    }

    private fun normalizeOrShow(host: String): String? = try {
        TvEndpoint.normalize(host)
    } catch (_: IllegalArgumentException) {
        _state.update { it.copy(status = TvStatus.Error, detail = context.getString(R.string.error_host)) }
        null
    }

    @Suppress("DEPRECATION")
    private fun resolve(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    serviceInfo.host?.hostAddress
                } ?: return
                _state.update { current ->
                    if (host in current.discovered) current else current.copy(discovered = current.discovered + host)
                }
            }
        })
    }

    private class NeedPairing : Exception()
    private class BadPin : Exception()

    private companion object {
        val PACKAGE = Regex("^[A-Za-z0-9_.]+$")
    }
}
