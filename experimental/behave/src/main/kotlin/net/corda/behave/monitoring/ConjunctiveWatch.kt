/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.monitoring

import net.corda.behave.await
import rx.Observable
import java.time.Duration
import java.util.concurrent.CountDownLatch

class ConjunctiveWatch(
        private val left: Watch,
        private val right: Watch
) : Watch() {

    override fun await(observable: Observable<String>, timeout: Duration): Boolean {
        val latch = CountDownLatch(2)
        listOf(left, right).parallelStream().forEach {
            if (it.await(observable, timeout)) {
                latch.countDown()
            }
        }
        return latch.await(timeout)
    }

}