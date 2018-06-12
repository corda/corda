/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
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
