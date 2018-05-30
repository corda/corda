package net.corda.deterministic

import org.junit.Assert.*
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi
import java.security.Security

/**
 * Temporarily restore Sun's [SecureRandom] provider.
 * This is ONLY for allowing us to generate test data, e.g. signatures.
 */
class CheatingSecurityProvider : Provider(NAME, 1.8, "$NAME security provider"), AutoCloseable {
    private companion object {
        private const val NAME = "Cheat!"
    }

    init {
        putService(CheatingSecureRandomService(this))
        assertEquals(1, Security.insertProviderAt(this, 1))
    }

    override fun close() {
        Security.removeProvider(NAME)
    }

    private class SunSecureRandom : SecureRandom(sun.security.provider.SecureRandom(), null)

    private class CheatingSecureRandomService(provider: Provider)
        : Provider.Service(provider, "SecureRandom", "CheatingPRNG", CheatingSecureRandomSpi::javaClass.name, null, null) {

        private val instance: SecureRandomSpi = CheatingSecureRandomSpi()
        override fun newInstance(constructorParameter: Any?) = instance
    }

    private class CheatingSecureRandomSpi : SecureRandomSpi() {
        private val secureRandom: SecureRandom = SunSecureRandom()

        override fun engineSetSeed(seed: ByteArray) = secureRandom.setSeed(seed)
        override fun engineNextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
        override fun engineGenerateSeed(numBytes: Int): ByteArray = secureRandom.generateSeed(numBytes)
    }
}