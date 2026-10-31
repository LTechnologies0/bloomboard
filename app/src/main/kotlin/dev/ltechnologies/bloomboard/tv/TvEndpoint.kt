package dev.ltechnologies.bloomboard.tv

import java.net.InetAddress

object TvEndpoint {
    private val hostPattern = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")

    fun normalize(raw: String): String {
        var host = raw.trim()
        if (host.startsWith("[") && host.endsWith("]") && host.length > 2) {
            host = host.substring(1, host.length - 1)
        }
        require(host.length in 1..253) { "host" }
        require(hostPattern.matches(host)) { "host" }
        require(!host.equals("0.0.0.0", ignoreCase = true)) { "host" }
        require(!host.contains("metadata.google.internal", ignoreCase = true)) { "host" }
        return host
    }

    fun isPublicAddress(host: String): Boolean {
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            return false
        }
        if (addresses.isEmpty()) return false
        return addresses.any { !isLocal(it) }
    }

    fun isLocal(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            if (first and 0xFE == 0xFC) return true
        }
        return false
    }
}
