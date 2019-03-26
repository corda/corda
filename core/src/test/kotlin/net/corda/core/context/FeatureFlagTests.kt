package net.corda.core.context

import net.corda.core.internal.context.FeatureFlag
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class FeatureFlagTests {

    @Test
    fun `feature flags in an unconfigured context are always false`() {
        assertFalse(FeatureFlag.DISABLE_CORDA_2707)
    }

    @Test
    fun `feature flags can be overriden in tests`() {
        FeatureFlag.withSet(FeatureFlag::DISABLE_CORDA_2707 to true) {
            assertTrue(FeatureFlag.DISABLE_CORDA_2707)
        }
    }

    @Test
    fun `feature flag overrides are thread-local`() {
        val threadPool = Executors.newCachedThreadPool()
        val semaphore = Semaphore(0)

        // Create two threads and have them wait on the semaphore.
        val falseFuture = threadPool.submit<Boolean> {
            semaphore.acquire()
            FeatureFlag.DISABLE_CORDA_2707
        }
        val trueFuture = threadPool.submit<Boolean> {
            FeatureFlag.withSet(FeatureFlag::DISABLE_CORDA_2707 to true) {
                semaphore.acquire()
                FeatureFlag.DISABLE_CORDA_2707
            }
        }

        // Release both threads at once; one has DISABLE_CORDA_2707=true in its thread local, the other does not.
        semaphore.release(2)

        assertFalse(falseFuture.get())
        assertTrue(trueFuture.get())
    }

}