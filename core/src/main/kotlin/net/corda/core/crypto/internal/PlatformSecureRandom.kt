@file:JvmName("PlatformSecureRandom")
@file:DeleteForDJVM
package net.corda.core.crypto.internal

import io.netty.util.concurrent.FastThreadLocal
import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.DummySecureRandom
import net.corda.core.utilities.SgxSupport
import net.corda.core.utilities.loggerFor
import org.apache.commons.lang3.SystemUtils
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi

/**
 * This has been migrated into a separate class so that it
 * is easier to delete from the core-deterministic module.
 */
internal val platformSecureRandom: () -> SecureRandom = when {
    SgxSupport.isInsideEnclave -> {
        { DummySecureRandom }
    }
    else -> {
        { sharedSecureRandom }
    }
}

@DeleteForDJVM
class PlatformSecureRandomService(provider: Provider)
    : Provider.Service(provider, "SecureRandom", algorithm, PlatformSecureRandomSpi::javaClass.name, null, null) {

    companion object {
        const val algorithm = "CordaPRNG"
        private val logger = loggerFor<PlatformSecureRandomService>()
    }

    private val instance: SecureRandomSpi = if (SystemUtils.IS_OS_LINUX) tryAndUseLinuxSecureRandomSpi() else PlatformSecureRandomSpi()

    @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
    private fun tryAndUseLinuxSecureRandomSpi(): SecureRandomSpi = try {
        LinuxSecureRandomSpi()
    } catch (e: Exception) {
        logger.error("Unable to initialise LinuxSecureRandomSpi. The exception logged with this message might assist with diagnosis." +
                "  The process will now exit.", e)
        System.exit(1)
        throw RuntimeException("Never reached, but calms the compiler.")
    }

    override fun newInstance(constructorParameter: Any?) = instance
}

@DeleteForDJVM
private class PlatformSecureRandomSpi : SecureRandomSpi() {
    private val threadLocalSecureRandom = object : FastThreadLocal<SecureRandom>() {
        override fun initialValue() = SecureRandom.getInstanceStrong()
    }

    private val secureRandom: SecureRandom get() = threadLocalSecureRandom.get()

    override fun engineSetSeed(seed: ByteArray) = secureRandom.setSeed(seed)
    override fun engineNextBytes(bytes: ByteArray) = secureRandom.nextBytes(bytes)
    override fun engineGenerateSeed(numBytes: Int): ByteArray = secureRandom.generateSeed(numBytes)
}

@DeleteForDJVM
@Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
private class LinuxSecureRandomSpi : SecureRandomSpi() {
    private fun openURandom(): InputStream {
        try {
            val file = File("/dev/urandom")
            val stream = FileInputStream(file)
            if (stream.read() == -1)
                throw RuntimeException("/dev/urandom not readable?")
            return stream
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private var urandom = DataInputStream(openURandom())

    override fun engineSetSeed(seed: ByteArray) {}
    override fun engineNextBytes(bytes: ByteArray) = try {
        urandom.readFully(bytes)
    } catch (e: IOException) {
        throw RuntimeException(e)
    }

    override fun engineGenerateSeed(numBytes: Int): ByteArray = ByteArray(numBytes).apply { engineNextBytes(this) }
}

// This is safe to share because of the underlying implementation of SecureRandomSpi
private val sharedSecureRandom: SecureRandom by lazy(LazyThreadSafetyMode.PUBLICATION) {
    SecureRandom.getInstance(PlatformSecureRandomService.algorithm)
}
