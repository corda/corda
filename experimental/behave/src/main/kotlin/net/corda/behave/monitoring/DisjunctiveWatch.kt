package net.corda.behave.monitoring

import net.corda.behave.await
import rx.Observable
import java.time.Duration
import java.util.concurrent.CountDownLatch

class DisjunctiveWatch(
        private val left: Watch,
        private val right: Watch
) : Watch {

    override fun ready() = left.ready() || right.ready()

    override fun await(timeout: Duration): Boolean {
        val countDownLatch =  CountDownLatch(1)
        listOf(left, right).parallelStream().forEach {
            if (it.await(timeout)) {
                countDownLatch.countDown()
            }
        }
        return countDownLatch.await(timeout)
    }

}

