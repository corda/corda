package net.corda.sleeping

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import java.time.Duration

@StartableByRPC
class SleepingFlow(private val duration: Duration) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        sleep(duration)
    }
}