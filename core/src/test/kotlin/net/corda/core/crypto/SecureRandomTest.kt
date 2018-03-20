package net.corda.core.crypto

import net.corda.core.crypto.internal.cordaSecurityProvider
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.join
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SecureRandomTest {
    private companion object {
        private val getSpi = SecureRandom::class.java.getDeclaredMethod("getSecureRandomSpi").apply { isAccessible = true }
        private fun SecureRandom.spi() = getSpi.invoke(this)

        init {
            newSecureRandom() // Ensure all globals installed before running tests.
        }
    }

    @Test
    fun `newSecureRandom returns a global that delegates to thread-local`() {
        val sr = newSecureRandom()
        assertSame(sr, newSecureRandom())
        checkDelegatesToThreadLocal(sr)
    }

    @Test
    fun `regular SecureRandom delegates to thread-local`() {
        val sr = SecureRandom()
        assertSame(sr.spi(), SecureRandom().spi())
        checkDelegatesToThreadLocal(sr)
    }

    @Test(timeout = 1000)
    fun `regular SecureRandom does not spend a lot of time seeding itself`() {
        val bytes = ByteArray(1000)
        repeat(10) {
            val sr = SecureRandom()
            repeat(100) {
                sr.nextBytes(bytes)
            }
        }
    }

    @Test
    fun `regular SecureRandom with seed delegates to thread-local`() {
        val sr = SecureRandom(byteArrayOf(1, 2, 3))
        assertSame(sr.spi(), SecureRandom(byteArrayOf(4, 5, 6)).spi())
        checkDelegatesToThreadLocal(sr)
    }

    @Test
    fun `SecureRandom#getInstance makes a SecureRandom that delegates to thread-local`() {
        CORDA_SECURE_RANDOM_ALGORITHM.let {
            val sr = SecureRandom.getInstance(it)
            assertEquals(it, sr.algorithm)
            assertSame(sr.spi(), SecureRandom.getInstance(it).spi())
            checkDelegatesToThreadLocal(sr)
        }
    }

    private fun checkDelegatesToThreadLocal(sr: SecureRandom) {
        val spi = sr.spi() as DelegatingSecureRandomSpi
        val fg = spi.currentThreadSecureRandom()
        val e = Executors.newSingleThreadExecutor()
        val bg = e.fork(spi::currentThreadSecureRandom).getOrThrow()
        assertNotSame(fg, bg) // Background thread got a distinct instance.
        // Each thread always gets the same instance:
        assertSame(fg, spi.currentThreadSecureRandom())
        assertSame(bg, e.fork(spi::currentThreadSecureRandom).getOrThrow())
        e.join()
        assertSame(fg.provider, bg.provider)
        assertNotSame(cordaSecurityProvider, fg.provider)
        assertEquals(fg.algorithm, bg.algorithm)
        assertNotEquals(CORDA_SECURE_RANDOM_ALGORITHM, fg.algorithm)
        assertSame(cordaSecurityProvider, sr.provider)
        assertEquals(CORDA_SECURE_RANDOM_ALGORITHM, sr.algorithm)
    }
}
