package net.corda.behave.monitoring

import net.corda.behave.await
import net.corda.behave.seconds
import rx.Observable
import java.time.Duration
import java.util.concurrent.CountDownLatch

interface Watch {
    fun await(timeout: Duration = 10.seconds): Boolean
    fun ready(): Boolean

    operator fun times(other: Watch): Watch {
        return ConjunctiveWatch(this, other)
    }
    operator fun div(other: Watch): Watch {
        return DisjunctiveWatch(this, other)
    }
}

/**
 * @param if [autostart] is true starting of Watch can be deferred - it helps in case of initialization
 *  order problems (like match() using fields from subclass which won't get initialized before superclass
 *  constructor finishes. It is the responsibility of the subclass to manually call the run method
 *  if autostart is false.
 */
abstract class AbstractWatch<T>(val observable: Observable<T>, autostart: Boolean = true) : Watch {

    private val latch = CountDownLatch(1)

    init {
        if (autostart) {
            run()
        }
    }

    fun run() {
        observable.exists { match(it) }.filter{ it }.subscribe {
            latch.countDown()
        }
    }

    override fun await(timeout: Duration): Boolean {
        return latch.await(timeout)
    }

    override fun ready(): Boolean = latch.count == 0L

    open fun match(data: T): Boolean = false
}