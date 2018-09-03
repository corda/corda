package net.corda.node.utilities


import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.hours
import net.corda.core.utilities.minutes
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.services.events.NodeSchedulerService
import net.corda.testing.node.TestClock
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ClockUtilsTest {

    lateinit var realClock: Clock
    lateinit var stoppedClock: CordaClock
    lateinit var executor: ExecutorService

    @Before
    fun setup() {
        realClock = Clock.systemUTC()
        stoppedClock = SimpleClock(Clock.fixed(realClock.instant(), realClock.zone))
        executor = Executors.newSingleThreadExecutor()
    }

    @After
    fun teardown() {
        executor.shutdown()
    }

    @Test
    fun `test waiting no time for a deadline`() {
        assertFalse(NodeSchedulerService.awaitWithDeadline(stoppedClock, stoppedClock.instant()), "Should have reached deadline")
    }

    @Test
    fun `test waiting negative time for a deadline`() {
        assertFalse(NodeSchedulerService.awaitWithDeadline(stoppedClock, stoppedClock.instant().minus(1.hours)), "Should have reached deadline")
    }

    @Test
    fun `test waiting no time for a deadline with incomplete future`() {
        val future = SettableFuture.create<Boolean>()
        assertFalse(NodeSchedulerService.awaitWithDeadline(stoppedClock, stoppedClock.instant(), future), "Should have reached deadline")
    }

    @Test
    fun `test waiting negative time for a deadline with incomplete future`() {
        val future = SettableFuture.create<Boolean>()
        assertFalse(NodeSchedulerService.awaitWithDeadline(stoppedClock, stoppedClock.instant().minus(1.hours), future), "Should have reached deadline")
    }


    @Test
    fun `test waiting for a deadline with future completed before wait`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val future = SettableFuture.create<Boolean>()
        completeNow(future)
        assertTrue(NodeSchedulerService.awaitWithDeadline(stoppedClock, advancedClock.instant(), future), "Should not have reached deadline")
    }

    @Test
    fun `test waiting for a deadline with future completed after wait`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val future = SettableFuture.create<Boolean>()
        completeAfterWaiting(future)
        assertTrue(NodeSchedulerService.awaitWithDeadline(stoppedClock, advancedClock.instant(), future), "Should not have reached deadline")
    }

    @Test
    fun `test waiting for a deadline with clock advance`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        advanceClockAfterWait(testClock, 1.hours)
        assertFalse(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant()), "Should have reached deadline")
    }

    @Test
    fun `test waiting for a deadline with clock advance and incomplete future`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = SettableFuture.create<Boolean>()
        advanceClockAfterWait(testClock, 1.hours)
        assertFalse(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), future), "Should have reached deadline")
    }

    @Test
    fun `test waiting for a deadline with clock advance and complete future`() {
        val advancedClock = Clock.offset(stoppedClock, 2.hours)
        val testClock = TestClock(stoppedClock)
        val future = SettableFuture.create<Boolean>()
        advanceClockAfterWait(testClock, 1.hours)
        completeAfterWaiting(future)
        assertTrue(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), future), "Should not have reached deadline")
    }

    @Test
    fun `test waiting for a deadline with multiple clock advance and incomplete future`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = SettableFuture.create<Boolean>()
        for (advance in 1..6) {
            advanceClockAfterWait(testClock, 10.minutes)
        }
        assertFalse(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), future), "Should have reached deadline")
    }

    @Test
    fun `test external interrupt of a clock future`() {
        val mainStrand = Strand.currentStrand()
        executor.execute @Suspendable {
            // Wait until main thread is waiting
            while (mainStrand.state != Strand.State.TIMED_WAITING) {
                Strand.sleep(1)
            }
            mainStrand.interrupt()
        }

        val testClock = TestClock(stoppedClock)
        val advancedClock = Clock.offset(stoppedClock, 10.hours)

        try {
            NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), SettableFuture.create<Boolean>())
            fail("Expected InterruptedException")
        } catch (exception: InterruptedException) {
        }
    }

    @Test
    @Suspendable
    fun `test waiting for a deadline with multiple clock advance and incomplete JDK8 future on Fibers`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = CompletableFuture<Boolean>()
        val scheduler = FiberExecutorScheduler("test", executor)
        val fiber = scheduler.newFiber(@Suspendable {
            future.complete(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), future))
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
        assertFalse(future.getOrThrow(), "Should have reached deadline")
    }

    @Test
    @Suspendable
    fun `test waiting for a deadline with multiple clock advance and incomplete Guava future on Fibers`() {
        val advancedClock = Clock.offset(stoppedClock, 1.hours)
        val testClock = TestClock(stoppedClock)
        val future = SettableFuture.create<Boolean>()
        val scheduler = FiberExecutorScheduler("test", executor)
        val fiber = scheduler.newFiber(@Suspendable {
            future.set(NodeSchedulerService.awaitWithDeadline(testClock, advancedClock.instant(), future))
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
        assertFalse(future.getOrThrow(), "Should have reached deadline")
    }

    @Suspendable
    private fun advanceClockAfterWait(testClock: TestClock, duration: Duration) {
        val mainStrand = Strand.currentStrand()
        executor.execute @Suspendable {
            // Wait until main thread is waiting
            while (mainStrand.state != Strand.State.TIMED_WAITING) {
                Strand.sleep(1)
            }
            testClock.advanceBy(duration)
        }
    }

    @Suspendable
    private fun completeNow(future: SettableFuture<Boolean>) {
        future.set(true)
    }

    private fun completeAfterWaiting(future: SettableFuture<Boolean>) {
        val mainStrand = Strand.currentStrand()
        executor.execute @Suspendable {
            // Wait until main thread is waiting
            while (mainStrand.state != Strand.State.TIMED_WAITING) {
                Strand.sleep(1)
            }
            completeNow(future)
        }
    }
}
