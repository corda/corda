package net.corda.core.crypto

import java.security.Provider
import java.security.SecureRandomSpi

@Suppress("unused")
class DelegatingSecureRandomService(provider: CordaSecurityProvider)
    : Provider.Service(provider, "SecureRandom", "dummy-algorithm", UnsupportedSecureRandomSpi::javaClass.name, null, null) {
    private val instance: SecureRandomSpi = UnsupportedSecureRandomSpi(algorithm)
    override fun newInstance(param: Any?) = instance

    private class UnsupportedSecureRandomSpi(private val algorithm: String) : SecureRandomSpi() {
        override fun engineSetSeed(seed: ByteArray) = unsupported()
        override fun engineNextBytes(bytes: ByteArray) = unsupported()
        override fun engineGenerateSeed(numBytes: Int) = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("$algorithm not supported")
    }
}
