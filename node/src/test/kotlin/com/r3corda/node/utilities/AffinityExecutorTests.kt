package com.r3corda.node.utilities

import org.junit.After
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class AffinityExecutorTests {
    @Test fun `AffinityExecutor SAME_THREAD executes on calling thread`() {
        assert(AffinityExecutor.SAME_THREAD.isOnThread)

        run {
            val thatThread = CompletableFuture<Thread>()
            AffinityExecutor.SAME_THREAD.execute { thatThread.complete(Thread.currentThread()) }
            assertEquals(Thread.currentThread(), thatThread.get())
        }
        run {
            val thatThread = CompletableFuture<Thread>()
            AffinityExecutor.SAME_THREAD.executeASAP { thatThread.complete(Thread.currentThread()) }
            assertEquals(Thread.currentThread(), thatThread.get())
        }
    }

    var executor: AffinityExecutor.ServiceAffinityExecutor? = null

    @After fun shutdown() {
        executor?.shutdown()
    }

    @Test fun `single threaded affinity executor runs on correct thread`() {
        val thisThread = Thread.currentThread()
        val executor = AffinityExecutor.ServiceAffinityExecutor("test thread", 1)
        assert(!executor.isOnThread)
        assertFails { executor.checkOnThread() }

        val thread = AtomicReference<Thread>()
        executor.execute {
            assertNotEquals(thisThread, Thread.currentThread())
            executor.checkOnThread()
            thread.set(Thread.currentThread())
        }
        val thread2 = AtomicReference<Thread>()
        executor.execute {
            thread2.set(Thread.currentThread())
            executor.checkOnThread()
        }
        executor.flush()
        assertEquals(thread2.get(), thread.get())
    }

    @Test fun `pooled executor`() {
        val executor = AffinityExecutor.ServiceAffinityExecutor("test2", 3)
        assert(!executor.isOnThread)

        val latch = CountDownLatch(1)
        val latch2 = CountDownLatch(2)
        val threads = Collections.synchronizedList(ArrayList<Thread>())

        fun blockAThread() {
            executor.execute {
                assert(executor.isOnThread)
                threads += Thread.currentThread()
                latch2.countDown()
                latch.await()
            }
        }
        blockAThread()
        blockAThread()
        latch2.await()
        assertEquals(2, threads.size)
        val numThreads = executor.fetchFrom {
            assert(executor.isOnThread)
            threads += Thread.currentThread()
            threads.distinct().size
        }
        assertEquals(3, numThreads)
        latch.countDown()
        executor.flush()
    }

    @Test fun `exceptions are reported to the specified handler`() {
        val exception = AtomicReference<Throwable?>()
        // Run in a separate thread to avoid messing with any default exception handlers in the unit test thread.
        thread {
            Thread.currentThread().setUncaughtExceptionHandler { thread, throwable -> exception.set(throwable) }
            val executor = AffinityExecutor.ServiceAffinityExecutor("test3", 1)
            executor.execute {
                throw Exception("foo")
            }
            executor.flush()
        }.join()
        assertEquals("foo", exception.get()?.message)
    }
}