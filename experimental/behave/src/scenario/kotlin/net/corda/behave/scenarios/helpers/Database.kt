package net.corda.behave.scenarios.helpers

import net.corda.behave.await
import net.corda.behave.scenarios.ScenarioState
import net.corda.core.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.CountDownLatch

class Database(state: ScenarioState) : Substeps(state) {

    fun canConnectTo(nodeName: String) {
        withNetwork {
            val latch = CountDownLatch(1)
            log.info("Connecting to the database of node '$nodeName' ...")
            node(nodeName).database.use {
                log.info("Connected to the database of node '$nodeName'")
                latch.countDown()
            }
            assertThat(latch.await(10.seconds)).isTrue()
        }
    }
}