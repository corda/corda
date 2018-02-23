package net.corda.core.flows

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SuspendableCallable
import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class FastThreadLocalTest {
    private inner class ExpensiveObj {
        init {
            expensiveObjCount.andIncrement
        }
    }

    private val expensiveObjCount = AtomicInteger()
    private lateinit var pool: ExecutorService
    private lateinit var scheduler: FiberExecutorScheduler
    private fun init(threadCount: Int, threadImpl: (Runnable) -> Thread) {
        pool = Executors.newFixedThreadPool(threadCount, threadImpl)
        scheduler = FiberExecutorScheduler(null, pool)
    }

    @After
    fun poolShutdown() = pool.shutdown()

    @After
    fun schedulerShutdown() = scheduler.shutdown()

    @Test
    fun `ThreadLocal with plain old Thread is fiber-local`() {
        init(3, ::Thread)
        val threadLocal = object : ThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `ThreadLocal with FastThreadLocalThread is fiber-local`() {
        init(3, ::FastThreadLocalThread)
        val threadLocal = object : ThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `FastThreadLocal with plain old Thread is fiber-local`() {
        init(3, ::Thread)
        val threadLocal = object : FastThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `FastThreadLocal with FastThreadLocalThread is not fiber-local`() {
        init(3, ::FastThreadLocalThread)
        val threadLocal = object : FastThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        runFibers(100, threadLocal::get) // Return value could be anything.
        assertThat(expensiveObjCount.get(), lessThanOrEqualTo(3))
    }

    /** @return the number of times a different expensive object was obtained post-suspend. */
    private fun runFibers(fiberCount: Int, threadLocalGet: () -> ExpensiveObj): Int {
        val fibers = (0 until fiberCount).map { Fiber(scheduler, FiberTask(threadLocalGet)) }
        val startedFibers = fibers.map { it.start() }
        return startedFibers.map { it.get() }.count { it }
    }

    private class FiberTask(private val threadLocalGet: () -> ExpensiveObj) : SuspendableCallable<Boolean> {
        @Suspendable
        override fun run(): Boolean {
            val first = threadLocalGet()
            Fiber.sleep(1)
            return threadLocalGet() != first
        }
    }
}
