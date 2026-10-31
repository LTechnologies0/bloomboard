package dev.ltechnologies.bloomboard.tv

object AtvFeature {
    const val PING = 1
    const val KEY = 2
    const val IME = 4
    const val POWER = 32
    const val VOLUME = 64
    const val APP_LINK = 512
    const val CLIENT = PING or KEY or IME or POWER or VOLUME or APP_LINK
}

object AtvDirection {
    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3
}

object AtvKey {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val N0 = 7
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val VOLUME_MUTE = 164
    const val SETTINGS = 176
}

enum class PairingKind {
    RequestAck,
    Options,
    ConfigurationAck,
    SecretAck,
    Other,
    Error,
}

data class PairingIncoming(val kind: PairingKind, val status: Int)

sealed interface RemoteIncoming {
    data class Configure(val features: Int, val model: String, val vendor: String) : RemoteIncoming
    data object SetActive : RemoteIncoming
    data class Ping(val value: Int) : RemoteIncoming
    data class Started(val started: Boolean) : RemoteIncoming
    data class Volume(val level: Int, val max: Int, val muted: Boolean) : RemoteIncoming
    data class TextField(val counter: Int, val value: String, val start: Int, val end: Int, val app: String) : RemoteIncoming
    data object Other : RemoteIncoming
}

object AtvMessages {
    const val PAIR_PORT = 6467
    const val COMMAND_PORT = 6466
    const val STATUS_OK = 200
    const val SERVICE_NAME = "atvremote"
    const val NSD_TYPE = "_androidtvremote2._tcp."

    fun pairingRequest(clientName: String): ByteArray = proto {
        intField(1, 2)
        intField(2, STATUS_OK)
        messageField(10) {
            stringField(1, SERVICE_NAME)
            stringField(2, clientName)
        }
    }

    fun pairingOptions(): ByteArray = proto {
        intField(1, 2)
        intField(2, STATUS_OK)
        messageField(20) {
            messageField(1) {
                intField(1, 3)
                intField(2, 6)
            }
            intField(3, 1)
        }
    }

    fun pairingConfiguration(): ByteArray = proto {
        intField(1, 2)
        intField(2, STATUS_OK)
        messageField(30) {
            messageField(1) {
                intField(1, 3)
                intField(2, 6)
            }
            intField(2, 1)
        }
    }

    fun pairingSecret(secret: ByteArray): ByteArray = proto {
        intField(1, 2)
        intField(2, STATUS_OK)
        messageField(40) {
            bytesField(1, secret)
        }
    }

    fun parsePairing(bytes: ByteArray): PairingIncoming {
        val fields = ProtoReader(bytes).fields()
        val status = fields.firstOrNull { it.number == 2 && it.wire == 0 }?.varint?.toInt() ?: 0
        if (status != STATUS_OK) return PairingIncoming(PairingKind.Error, status)
        val kind = when {
            fields.any { it.number == 11 } -> PairingKind.RequestAck
            fields.any { it.number == 20 } -> PairingKind.Options
            fields.any { it.number == 31 } -> PairingKind.ConfigurationAck
            fields.any { it.number == 41 } -> PairingKind.SecretAck
            else -> PairingKind.Other
        }
        return PairingIncoming(kind, status)
    }

    fun configure(features: Int): ByteArray = proto {
        messageField(1) {
            intField(1, features)
            messageField(2) {
                intField(3, 1)
                stringField(4, "1")
                stringField(5, SERVICE_NAME)
                stringField(6, "1.0.0")
            }
        }
    }

    fun setActive(features: Int): ByteArray = proto {
        messageField(2) {
            intField(1, features)
        }
    }

    fun pingResponse(value: Int): ByteArray = proto {
        messageField(9) {
            intField(1, value)
        }
    }

    fun keyInject(keyCode: Int, direction: Int): ByteArray = proto {
        messageField(10) {
            intField(1, keyCode)
            intField(2, direction)
        }
    }

    fun appLink(url: String): ByteArray = proto {
        messageField(90) {
            stringField(1, url)
        }
    }

    /** Inserts text into the focused Android TV field. [insert] 0 is the insertion edit. */
    fun imeInsert(imeCounter: Int, fieldCounter: Int, start: Int, end: Int, text: String): ByteArray = proto {
        messageField(21) {
            intField(1, imeCounter)
            intField(2, fieldCounter)
            messageField(3) {
                intField(1, 0)
                messageField(2) {
                    intField(1, start)
                    intField(2, end)
                    stringField(3, text)
                }
            }
        }
    }

    fun parseRemote(bytes: ByteArray): RemoteIncoming {
        val fields = ProtoReader(bytes).fields()
        val configure = fields.firstOrNull { it.number == 1 && it.wire == 2 }
        if (configure != null) {
            val inner = ProtoReader(configure.bytes).fields()
            val features = inner.firstOrNull { it.number == 1 }?.varint?.toInt() ?: 0
            val info = inner.firstOrNull { it.number == 2 }?.bytes ?: ByteArray(0)
            val infoFields = if (info.isEmpty()) emptyList() else ProtoReader(info).fields()
            return RemoteIncoming.Configure(
                features = features,
                model = infoFields.string(1),
                vendor = infoFields.string(2),
            )
        }
        if (fields.any { it.number == 2 && it.wire == 2 }) return RemoteIncoming.SetActive
        val ping = fields.firstOrNull { it.number == 8 && it.wire == 2 }
        if (ping != null) {
            val value = ProtoReader(ping.bytes).fields().firstOrNull { it.number == 1 }?.varint?.toInt() ?: 0
            return RemoteIncoming.Ping(value)
        }
        val start = fields.firstOrNull { it.number == 40 && it.wire == 2 }
        if (start != null) {
            val started = ProtoReader(start.bytes).fields().firstOrNull { it.number == 1 }?.varint == 1L
            return RemoteIncoming.Started(started)
        }
        val volume = fields.firstOrNull { it.number == 50 && it.wire == 2 }
        if (volume != null) {
            val parts = ProtoReader(volume.bytes).fields()
            return RemoteIncoming.Volume(
                level = parts.int(7),
                max = parts.int(6),
                muted = parts.int(8) != 0,
            )
        }
        val ime = fields.firstOrNull { (it.number == 20 || it.number == 22) && it.wire == 2 }
        if (ime != null) {
            val parts = ProtoReader(ime.bytes).fields()
            val app = parts.firstOrNull { it.number == 1 }?.bytes?.let { ProtoReader(it).fields().string(12) }.orEmpty()
            val text = parts.firstOrNull { it.number == 2 }?.bytes ?: ByteArray(0)
            val textFields = if (text.isEmpty()) emptyList() else ProtoReader(text).fields()
            return RemoteIncoming.TextField(
                counter = textFields.int(1),
                value = textFields.string(2),
                start = textFields.int(3),
                end = textFields.int(4),
                app = app,
            )
        }
        return RemoteIncoming.Other
    }
}

private fun List<ProtoField>.int(number: Int): Int =
    firstOrNull { it.number == number && it.wire == 0 }?.varint?.toInt() ?: 0

private fun List<ProtoField>.string(number: Int): String {
    val bytes = firstOrNull { it.number == number && it.wire == 2 }?.bytes ?: return ""
    return bytes.toString(Charsets.UTF_8)
}
