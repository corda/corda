package net.corda.bridge

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.utilities.unwrap
import kotlin.test.assertEquals

@StartableByRPC
@InitiatingFlow
class Ping(val pongParty: Party, val times: Int) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val pongSession = initiateFlow(pongParty)
        pongSession.sendAndReceive<Unit>(times)
        BridgeRestartTest.pingStarted.getOrPut(runId) { openFuture() }.set(Unit)
        for (i in 1..times) {
            logger.info("PING $i")
            val j = pongSession.sendAndReceive<Int>(i).unwrap { it }
            assertEquals(i, j)
        }
    }
}

@InitiatedBy(Ping::class)
class Pong(val pingSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val times = pingSession.sendAndReceive<Int>(Unit).unwrap { it }
        for (i in 1..times) {
            logger.info("PONG $i $pingSession")
            val j = pingSession.sendAndReceive<Int>(i).unwrap { it }
            assertEquals(i, j)
        }
    }
}