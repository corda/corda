package core.utilities

import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class AffinityExecutorTests {
    @Test fun `direct thread executor works`() {
        val thisThread = Thread.currentThread()
        AffinityExecutor.SAME_THREAD.execute { assertEquals(thisThread, Thread.currentThread()) }
        AffinityExecutor.SAME_THREAD.executeASAP { assertEquals(thisThread, Thread.currentThread()) }
        assert(AffinityExecutor.SAME_THREAD.isOnThread)
    }

    @Test fun `single threaded affinity executor works`() {
        val thisThread = Thread.currentThread()
        val executor = AffinityExecutor.ServiceAffinityExecutor("test thread", 1)
        assert(!executor.isOnThread)
        assertFails { executor.checkOnThread() }

        var thread: Thread? = null
        executor.execute {
            assertNotEquals(thisThread, Thread.currentThread())
            executor.checkOnThread()
            thread = Thread.currentThread()
        }
        executor.execute {
            assertEquals(thread, Thread.currentThread())
            executor.checkOnThread()
        }
        executor.fetchFrom { }   // Serialize

        executor.service.shutdown()
    }

    @Test fun `pooled executor works`() {
        val executor = AffinityExecutor.ServiceAffinityExecutor("test2", 3)
        assert(!executor.isOnThread)

        val latch = CountDownLatch(1)
        val threads = Collections.synchronizedList(ArrayList<Thread>())

        fun blockAThread() {
            executor.execute {
                assert(executor.isOnThread)
                threads += Thread.currentThread()
                latch.await()
            }
        }
        blockAThread()
        blockAThread()
        executor.fetchFrom { }  // Serialize
        assertEquals(2, threads.size)
        executor.fetchFrom {
            assert(executor.isOnThread)
            threads += Thread.currentThread()
            assertEquals(3, threads.distinct().size)
        }
        latch.countDown()
        executor.fetchFrom { }  // Serialize
        executor.service.shutdown()
    }

    @Volatile var exception: Throwable? = null
    @Test fun exceptions() {
        // Run in a separate thread to avoid messing with any default exception handlers in the unit test thread.
        thread {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable -> exception = throwable }
            val executor = AffinityExecutor.ServiceAffinityExecutor("test3", 1)
            executor.execute {
                throw Exception("foo")
            }
            executor.fetchFrom { }   // Serialize
            assertEquals("foo", exception!!.message)
        }.join()
    }
}