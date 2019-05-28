@file:JvmName("PlatformSecureRandom")
@file:DeleteForDJVM
package net.corda.core.crypto.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.newSecureRandom
import org.apache.commons.lang3.SystemUtils
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi

/**
 * This has been migrated into a separate class so that it
 * is easier to delete from the core-deterministic module.
 */
val platformSecureRandom: () -> SecureRandom = when {
    SystemUtils.IS_OS_LINUX -> {
        { SecureRandom.getInstance("NativePRNGNonBlocking") }
    }
    else -> SecureRandom::getInstanceStrong
}

@DeleteForDJVM
class PlatformSecureRandomService(provider: Provider)
    : Provider.Service(provider, "SecureRandom", algorithm, PlatformSecureRandomSpi::javaClass.name, null, null) {

    companion object {
        const val algorithm = "CordaPRNG"
    }

    private val instance: SecureRandomSpi = PlatformSecureRandomSpi()
    override fun newInstance(constructorParameter: Any?) = instance
}

@DeleteForDJVM
private class PlatformSecureRandomSpi : SecureRandomSpi() {
    private val secureRandom: SecureRandom = newSecureRandom()

    override fun engineSetSeed(seed: ByteArray) = secureRandom.setSeed(seed)
    override fun engineNextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
    override fun engineGenerateSeed(numBytes: Int): ByteArray = secureRandom.generateSeed(numBytes)
}
