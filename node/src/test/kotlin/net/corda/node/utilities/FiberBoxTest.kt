package net.corda.node.utilities

import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.RetryableException
import net.corda.core.concurrent.getOrThrow
import net.corda.core.utilities.hours
import net.corda.core.utilities.minutes
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class FiberBoxTest {

    class Content {
        var integer: Int = 0
    }

    class TestRetryableException(message: String) : RetryableException(message)

    lateinit var mutex: FiberBox<Content>
    lateinit var realClock: Clock
    lateinit var stoppedClock: Clock
    lateinit var executor: ExecutorService

    @Before
    fun setup() {
        mutex = FiberBox(Content())
        realClock = Clock.systemUTC()
        stoppedClock = Clock.fixed(realClock.instant(), realClock.zone)
        executor = Executors.newSingleThreadExecutor()
    }

    @After
    fun teardown() {
        executor.shutdown()
    }

    @Test
    fun `write and read`() {
        mutex.write { integer = 1 }
        assertEquals(1, mutex.read { integer })
    }

    @Test
    fun `readWithDeadline with no wait`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)

        mutex.write { integer = 1 }
        assertEquals(1, mutex.readWithDeadline(realClock, advancedClock.instant()) { integer })
    }

    @Test
    fun `readWithDeadline with stopped clock and background write`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)

        assertEquals(1, mutex.readWithDeadline(stoppedClock, advancedClock.instant()) {
            backgroundWrite()
            if (integer == 1) 1 else throw TestRetryableException("Not 1")
        })
    }

    @Test(expected = TestRetryableException::class)
    fun `readWithDeadline with clock advanced`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)

        assertEquals(1, mutex.readWithDeadline(testClock, advancedClock.instant()) {
            backgroundAdvanceClock(testClock, 1.hours)
            if (integer == 1) 0 else throw TestRetryableException("Not 1")
        })
    }

    @Test
    fun `readWithDeadline with clock advanced 5x and background write`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)

        assertEquals(5, mutex.readWithDeadline(testClock, advancedClock.instant()) {
            backgroundAdvanceClock(testClock, 10.minutes)
            backgroundWrite()
            if (integer == 5) 5 else throw TestRetryableException("Not 5")
        })
    }

    /**
     * If this test seems to hang and throw an NPE, then likely that quasar suspendables scanner has not been
     * run on core module (in IntelliJ, open gradle side tab and run:
     * r3prototyping -> core -> Tasks -> other -> quasarScan
     */
    @Test(expected = TestRetryableException::class)
    @Suspendable
    fun `readWithDeadline with clock advanced on Fibers`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = CompletableFuture<Int>()
        val scheduler = FiberExecutorScheduler("test", executor)
        val fiber = scheduler.newFiber(@Suspendable {
            try {
                future.complete(mutex.readWithDeadline(testClock, advancedClock.instant()) {
                    if (integer == 1) 1 else throw TestRetryableException("Not 1")
                })
            } catch(e: Exception) {
                future.completeExceptionally(e)
            }
        }).start()
        for (advance in 1..6) {
            scheduler.newFiber(@Suspendable {
                // Wait until fiber is waiting
                while (fiber.state != Strand.State.TIMED_WAITING) {
                    Strand.sleep(1)
                }
                testClock.advanceBy(10.minutes)
            }).start()
        }
        assertEquals(2, future.getOrThrow())
    }

    /**
     * If this test seems to hang and throw an NPE, then likely that quasar suspendables scanner has not been
     * run on core module (in IntelliJ, open gradle side tab and run:
     * r3prototyping -> core -> Tasks -> other -> quasarScan
     */
    @Test
    @Suspendable
    fun `readWithDeadline with background write on Fibers`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = CompletableFuture<Int>()
        val scheduler = FiberExecutorScheduler("test", executor)
        val fiber = scheduler.newFiber(@Suspendable {
            try {
                future.complete(mutex.readWithDeadline(testClock, advancedClock.instant()) {
                    if (integer == 1) 1 else throw TestRetryableException("Not 1")
                })
            } catch(e: Exception) {
                future.completeExceptionally(e)
            }
        }).start()
        scheduler.newFiber(@Suspendable {
            // Wait until fiber is waiting
            while (fiber.state != Strand.State.TIMED_WAITING) {
                Strand.sleep(1)
            }
            mutex.write { integer = 1 }
        }).start()
        assertEquals(1, future.getOrThrow())
    }

    private fun backgroundWrite() {
        executor.execute {
            mutex.write { integer += 1 }
        }
    }

    private fun backgroundAdvanceClock(clock: TestClock, duration: Duration) {
        executor.execute {
            clock.advanceBy(duration)
        }
    }
}
