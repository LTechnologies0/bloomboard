package dev.ltechnologies.bloomboard.tv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

class AtvIdentity {
    fun ensure() {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) return
        val generator = java.security.KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, STORE)
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=Bloomboard"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(Date(now - 60_000))
            .setCertificateNotAfter(Date(now + 3650L * 24L * 60L * 60L * 1000L))
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    fun clientRsa(): RSAPublicKey {
        val cert = keyStore().getCertificate(ALIAS) as X509Certificate
        return cert.publicKey as RSAPublicKey
    }

    fun sslContext(expectedPin: ByteArray?): Pair<SSLContext, PinningTrust> {
        ensure()
        val store = keyStore()
        val factory = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, null)
        val origin = factory.keyManagers.filterIsInstance<javax.net.ssl.X509KeyManager>().first()
        val trust = PinningTrust(expectedPin)
        val context = SSLContext.getInstance("TLS")
        context.init(arrayOf(AliasKeyManager(origin)), arrayOf<javax.net.ssl.TrustManager>(trust), SecureRandom())
        return context to trust
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private class AliasKeyManager(private val origin: javax.net.ssl.X509KeyManager) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: java.net.Socket?) = ALIAS
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: java.net.Socket?) = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = origin.getCertificateChain(ALIAS)
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null
        override fun getPrivateKey(alias: String?): PrivateKey? = origin.getPrivateKey(ALIAS)
    }

    companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "bloomboard-atv"
    }
}

class PinningTrust(private val expectedPin: ByteArray?) : X509TrustManager {
    @Volatile var seen: X509Certificate? = null

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val cert = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("empty")
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val required = expectedPin
        if (required != null && !MessageDigest.isEqual(required, actual)) {
            throw java.security.cert.CertificateException("pin")
        }
        seen = cert
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun sha256(cert: X509Certificate): ByteArray = MessageDigest.getInstance("SHA-256").digest(cert.encoded)

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun unhex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
