package dev.ltechnologies.bloomboard.tv

import java.math.BigInteger
import java.security.MessageDigest

/** SHA-256 secret used by the Android TV pairing handshake. */
object PairingSecret {
    fun hash(
        clientModulus: BigInteger,
        clientExponent: BigInteger,
        serverModulus: BigInteger,
        serverExponent: BigInteger,
        pin: String,
    ): ByteArray {
        val normalized = pin.trim()
        require(normalized.length == 6 && normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "pin"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(modulusBytes(clientModulus))
        digest.update(exponentBytes(clientExponent))
        digest.update(modulusBytes(serverModulus))
        digest.update(exponentBytes(serverExponent))
        digest.update(hexToBytes(normalized.substring(2)))
        val hash = digest.digest()
        val prefix = normalized.substring(0, 2).toInt(16)
        if ((hash[0].toInt() and 0xFF) != prefix) {
            throw IllegalArgumentException("pin")
        }
        return hash
    }

    private fun modulusBytes(value: BigInteger): ByteArray {
        var hex = value.abs().toString(16)
        if (hex.length % 2 == 1) hex = "0$hex"
        return hexToBytes(hex)
    }

    private fun exponentBytes(value: BigInteger): ByteArray {
        var hex = "0" + value.abs().toString(16)
        if (hex.length % 2 == 1) hex = "0$hex"
        return hexToBytes(hex)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
