@file:JvmName("PlatformSecureRandom")
@file:DeleteForDJVM
package net.corda.core.crypto.internal

import io.netty.util.concurrent.FastThreadLocal
import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.DummySecureRandom
import net.corda.core.utilities.SgxSupport
import org.apache.commons.lang.SystemUtils
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi

/**
 * This has been migrated into a separate class so that it
 * is easier to delete from the core-deterministic module.
 */
internal val platformSecureRandom: () -> SecureRandom = when {
    SgxSupport.isInsideEnclave -> { { DummySecureRandom } }
    SystemUtils.IS_OS_LINUX -> {
        { SunSecureRandom() }
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
    private val threadLocalSecureRandom = object : FastThreadLocal<SecureRandom>() {
        override fun initialValue() = platformSecureRandom()
    }

    private val secureRandom: SecureRandom = threadLocalSecureRandom.get()

    override fun engineSetSeed(seed: ByteArray) = secureRandom.setSeed(seed)
    override fun engineNextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
    override fun engineGenerateSeed(numBytes: Int): ByteArray = secureRandom.generateSeed(numBytes)
}

// Enterprise performance tweak: Unlike all the NativePRNG algorithms, this doesn't use a global lock:
// TODO: This is using private Java API. Just replace this with an implementation that always reads /dev/urandom on Linux.
private class SunSecureRandom : SecureRandom(sun.security.provider.SecureRandom(), null)
