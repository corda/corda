package net.corda.node.services.statemachine

import co.paralleluniverse.strands.concurrent.AbstractQueuedSynchronizer
import co.paralleluniverse.fibers.Suspendable

/**
 * Quasar-compatible latch that may be incremented.
 */
class CountUpDownLatch(initialValue: Int) {

    // See quasar CountDownLatch
    private class Sync(initialValue: Int) : AbstractQueuedSynchronizer() {
        init {
            state = initialValue
        }

        override fun tryAcquireShared(arg: Int): Int {
            if (arg >= 0) {
                return if (state == arg) 1 else -1
            } else {
                return if (state <= -arg) 1 else -1
            }
        }

        override fun tryReleaseShared(arg: Int): Boolean {
            while (true) {
                val c = state
                if (c == 0)
                    return false
                val nextc = c - Math.min(c, arg)
                if (compareAndSetState(c, nextc))
                    return nextc == 0
            }
        }

        fun increment() {
            while (true) {
                val c = state
                val nextc = c + 1
                if (compareAndSetState(c, nextc))
                    return
            }
        }
    }

    private val sync = Sync(initialValue)

    @Suspendable
    fun await() {
        sync.acquireSharedInterruptibly(0)
    }

    @Suspendable
    fun awaitLessThanOrEqual(number: Int) {
        sync.acquireSharedInterruptibly(number)
    }

    fun countDown(number: Int = 1) {
        require(number > 0)
        sync.releaseShared(number)
    }

    fun countUp() {
        sync.increment()
    }
}
