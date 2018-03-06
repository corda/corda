/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import org.assertj.core.api.Assertions.assertThat
import rx.observers.TestSubscriber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Ssh(state: ScenarioState) : Substeps(state) {

    fun canConnectTo(nodeName: String) {
        withNetwork {
            log.info("Connecting to node '$nodeName' over SSH ...")
            hasSshStartupMessage(nodeName)
            val latch = CountDownLatch(1)
            val subscriber = TestSubscriber<String>()
            node(nodeName).ssh {
                it.output.subscribe(subscriber)
                assertThat(subscriber.onNextEvents).isNotEmpty
                log.info("Successfully connect to node '$nodeName' over SSH")
                latch.countDown()
            }
            if (!latch.await(15, TimeUnit.SECONDS)) {
                fail("Failed to connect to node '$nodeName' over SSH")
            }
        }
    }

    private fun hasSshStartupMessage(nodeName: String) {
        var i = 5
        while (i > 0) {
            Thread.sleep(2000)
            if (state.node(nodeName).logOutput.find(".*SSH server listening on port.*").any()) {
                break
            }
            i -= 1
        }
        if (i == 0) {
            state.fail("Unable to find SSH start-up message for node $nodeName")
        }
    }

}