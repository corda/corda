package net.corda.core.flows

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.ByteArraySerializer
import co.paralleluniverse.strands.SuspendableCallable
import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.rootCause
import net.corda.core.utilities.getOrThrow
import org.assertj.core.api.Assertions.catchThrowable
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FastThreadLocalTest {
    private inner class ExpensiveObj {
        init {
            expensiveObjCount.andIncrement
        }
    }

    private val expensiveObjCount = AtomicInteger()

    @Test
    fun `ThreadLocal with plain old Thread is fiber-local`() = scheduled(3, ::Thread) {
        val threadLocal = object : ThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `ThreadLocal with FastThreadLocalThread is fiber-local`() = scheduled(3, ::FastThreadLocalThread) {
        val threadLocal = object : ThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `FastThreadLocal with plain old Thread is fiber-local`() = scheduled(3, ::Thread) {
        val threadLocal = object : FastThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        assertEquals(0, runFibers(100, threadLocal::get))
        assertEquals(100, expensiveObjCount.get())
    }

    @Test
    fun `FastThreadLocal with FastThreadLocalThread is not fiber-local`() =
            scheduled(3, ::FastThreadLocalThread) {
        val threadLocal = object : FastThreadLocal<ExpensiveObj>() {
            override fun initialValue() = ExpensiveObj()
        }
        runFibers(100, threadLocal::get) // Return value could be anything.
        assertThat(expensiveObjCount.get(), lessThanOrEqualTo(3))
    }

    /** @return the number of times a different expensive object was obtained post-suspend. */
    private fun SchedulerContext.runFibers(fiberCount: Int, threadLocalGet: () -> ExpensiveObj): Int {
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

    private class UnserializableObj {
        @Suppress("unused")
        private val fail: Nothing by lazy { throw UnsupportedOperationException("Nice try.") }
    }

    @Test
    fun `ThreadLocal content is not serialized`() {
        contentIsNotSerialized(object : ThreadLocal<UnserializableObj>() {
            override fun initialValue() = UnserializableObj()
        }::get)
    }

    @Test
    fun `FastThreadLocal content is not serialized`() {
        contentIsNotSerialized(object : FastThreadLocal<UnserializableObj>() {
            override fun initialValue() = UnserializableObj()
        }::get)
    }

    private fun contentIsNotSerialized(threadLocalGet: () -> UnserializableObj) = scheduled(1, ::FastThreadLocalThread) {
        // Use false like AbstractKryoSerializationScheme, the default of true doesn't work at all:
        val serializer = Fiber.getFiberSerializer(false)
        val returnValue = UUID.randomUUID()
        val deserializedFiber = serializer.read(openFuture<ByteArray>().let {
            Fiber(scheduler, FiberTask2(threadLocalGet, false, serializer, it, returnValue)).start()
            it.getOrThrow()
        }) as Fiber<*>
        assertEquals(returnValue, Fiber.unparkDeserialized(deserializedFiber, scheduler).get())
        assertEquals("Nice try.", openFuture<ByteArray>().let {
            Fiber(scheduler, FiberTask2(threadLocalGet, true, serializer, it, returnValue)).start()
            catchThrowable { it.getOrThrow() }
        }.rootCause.message)
    }

    private class FiberTask2(
            @Transient private val threadLocalGet: () -> UnserializableObj,
            private val retainObj: Boolean,
            @Transient private val serializer: ByteArraySerializer,
            @Transient private val bytesFuture: OpenFuture<ByteArray>,
            private val returnValue: UUID) : SuspendableCallable<UUID> {
        @Suspendable
        override fun run(): UUID {
            var obj: UnserializableObj? = threadLocalGet()
            assertNotNull(obj)
            if (!retainObj) {
                @Suppress("UNUSED_VALUE")
                obj = null
            }
            // In retainObj false case, check this doesn't attempt to serialize fields of currentThread:
            Fiber.parkAndSerialize { fiber, _ -> bytesFuture.capture { serializer.write(fiber) } }
            return returnValue
        }
    }

    private data class SchedulerContext(private val pool: ExecutorService, val scheduler: FiberExecutorScheduler) {
        fun shutdown() {
            pool.shutdown()
            scheduler.shutdown()
        }
    }

    private fun scheduled(threadCount: Int, threadImpl: (Runnable) -> Thread, test: SchedulerContext.() -> Unit) {
        val pool = Executors.newFixedThreadPool(threadCount, threadImpl)
        val ctx = SchedulerContext(pool, FiberExecutorScheduler(null, pool))
        try {
            ctx.test()
        } finally {
            ctx.shutdown()
        }
    }
}
