package net.corda.behave.monitoring

import net.corda.behave.await
import rx.Observable
import java.time.Duration
import java.util.concurrent.CountDownLatch

class DisjunctiveWatch(
        private val left: Watch,
        private val right: Watch
) : Watch() {

    override fun await(observable: Observable<String>, timeout: Duration): Boolean {
        val latch = CountDownLatch(1)
        listOf(left, right).parallelStream().forEach {
            if (it.await(observable, timeout)) {
                latch.countDown()
            }
        }
        return latch.await(timeout)
    }

}

