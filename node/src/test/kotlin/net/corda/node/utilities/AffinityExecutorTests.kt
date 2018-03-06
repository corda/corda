/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import org.junit.After
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class AffinityExecutorTests {
    var _executor: AffinityExecutor.ServiceAffinityExecutor? = null
    val executor: AffinityExecutor.ServiceAffinityExecutor get() = _executor!!

    @After
    fun shutdown() {
        _executor?.shutdown()
        _executor = null
    }

    @Test
    fun `flush handles nested executes`() {
        _executor = AffinityExecutor.ServiceAffinityExecutor("test4", 1)
        var nestedRan = false
        val latch = CountDownLatch(1)
        executor.execute {
            latch.await()
            executor.execute { nestedRan = true }
        }
        latch.countDown()
        executor.flush()
        assertTrue(nestedRan)
    }

    @Test
    fun `single threaded affinity executor runs on correct thread`() {
        val thisThread = Thread.currentThread()
        _executor = AffinityExecutor.ServiceAffinityExecutor("test thread", 1)
        assertTrue(!executor.isOnThread)
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

    @Test
    fun `pooled executor`() {
        _executor = AffinityExecutor.ServiceAffinityExecutor("test2", 3)
        assertFalse(executor.isOnThread)

        val latch = CountDownLatch(1)
        val latch2 = CountDownLatch(2)
        val threads = Collections.synchronizedList(ArrayList<Thread>())

        fun blockAThread() {
            executor.execute {
                assertTrue(executor.isOnThread)
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
            assertTrue(executor.isOnThread)
            threads += Thread.currentThread()
            threads.distinct().size
        }
        assertEquals(3, numThreads)
        latch.countDown()
        executor.flush()
    }
}
