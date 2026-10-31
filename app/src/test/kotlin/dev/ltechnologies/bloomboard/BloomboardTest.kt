package dev.ltechnologies.bloomboard

import dev.ltechnologies.bloomboard.hid.HidReports
import dev.ltechnologies.bloomboard.hid.HidUsage
import dev.ltechnologies.bloomboard.hid.LayoutId
import dev.ltechnologies.bloomboard.hid.glyphText
import dev.ltechnologies.bloomboard.tv.AtvMessages
import dev.ltechnologies.bloomboard.tv.PairingKind
import dev.ltechnologies.bloomboard.tv.PairingSecret
import dev.ltechnologies.bloomboard.tv.TvEndpoint
import dev.ltechnologies.bloomboard.tv.frame
import dev.ltechnologies.bloomboard.tv.proto
import dev.ltechnologies.bloomboard.tv.readPrefixed
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger

class BloomboardTest {
    @Test
    fun keyInjectMatchesTheAndroidTvWireShape() {
        val bytes = AtvMessages.keyInject(243, 3)
        assertEquals("520508f3011003", bytes.toHex())
    }

    @Test
    fun framedMessageRoundTrips() {
        val message = AtvMessages.keyInject(19, 3)
        val framed = frame(message)
        assertArrayEquals(message, readPrefixed(ByteArrayInputStream(framed)))
    }

    @Test
    fun pairingMessagesKeepTheirRoles() {
        assertEquals(PairingKind.Options, AtvMessages.parsePairing(AtvMessages.pairingOptions()).kind)
        val ack = proto {
            intField(1, 2)
            intField(2, 200)
            messageField(31) {}
        }
        assertEquals(PairingKind.ConfigurationAck, AtvMessages.parsePairing(ack).kind)
    }

    @Test
    fun pairingSecretMatchesKnownHash() {
        val hash = PairingSecret.hash(
            clientModulus = BigInteger("ABCD", 16),
            clientExponent = BigInteger.valueOf(65537),
            serverModulus = BigInteger("1234", 16),
            serverExponent = BigInteger.valueOf(3),
            pin = "BCBEEF",
        )
        assertEquals("bcb1722517921d848838f227767ae34fa12080abc5e3d252b496a91704835d94", hash.toHex())
    }

    @Test
    fun layoutsKeepPhysicalPositions() {
        assertEquals("a", glyphText(LayoutId.AZERTY, HidUsage.Q, shifted = false))
        assertEquals("q", glyphText(LayoutId.QWERTY, HidUsage.Q, shifted = false))
        assertEquals("z", glyphText(LayoutId.QWERTZ, HidUsage.Y, shifted = false))
        assertEquals("f", glyphText(LayoutId.COLEMAK, HidUsage.E, shifted = false))
        assertEquals("'", glyphText(LayoutId.DVORAK, HidUsage.Q, shifted = false))
        assertEquals("b", glyphText(LayoutId.BEPO, HidUsage.Q, shifted = false))
        assertEquals("1", glyphText(LayoutId.AZERTY, HidUsage.N1, shifted = true))
    }

    @Test
    fun hostMustBeAPlainName() {
        assertEquals("192.168.1.20", TvEndpoint.normalize(" 192.168.1.20 "))
        assertFalse(TvEndpoint.isPublicAddress("10.0.0.8"))
        assertTrue(runCatching { TvEndpoint.normalize("tv.local/admin") }.isFailure)
        assertTrue(runCatching { TvEndpoint.normalize("metadata.google.internal") }.isFailure)
    }

    @Test
    fun descriptorDeclaresKeyboardAndMouse() {
        assertTrue(HidReports.DESCRIPTOR.contains(HidReports.KEYBOARD_ID.toByte()))
        assertEquals(8, HidReports.keyboardReport(0x02, listOf(HidUsage.A)).size)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
