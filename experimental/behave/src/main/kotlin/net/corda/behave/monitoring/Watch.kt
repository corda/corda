package net.corda.behave.monitoring

import net.corda.behave.await
import net.corda.behave.seconds
import rx.Observable
import java.time.Duration
import java.util.concurrent.CountDownLatch

abstract class Watch {

    private val latch = CountDownLatch(1)

    open fun await(
            observable: Observable<String>,
            timeout: Duration = 10.seconds
    ): Boolean {
        observable
                .filter { match(it) }
                .forEach { latch.countDown() }
        return latch.await(timeout)
    }

    open fun match(data: String): Boolean = false

    operator fun times(other: Watch): Watch {
        return ConjunctiveWatch(this, other)
    }

    operator fun div(other: Watch): Watch {
        return DisjunctiveWatch(this, other)
    }

}