package net.corda.traderdemo

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.*
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import java.time.Instant

fun main(args: Array<String>) {
    val chainLength = args[0].toInt()
    driver(DriverParameters(inMemoryDB = false, notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, false)))) {
        val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                .map { startNode(NodeParameters(providedName = it, additionalCordapps = FINANCE_CORDAPPS)) }
                .map { it.getOrThrow() }
        alice.rpc.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(1), defaultNotaryIdentity).returnValue.getOrThrow()
        repeat(chainLength / 2) {
            alice.rpc.startFlow(::CashPaymentFlow, 1.DOLLARS, bob.nodeInfo.singleIdentity(), false).returnValue.getOrThrow()
            bob.rpc.startFlow(::CashPaymentFlow, 1.DOLLARS, alice.nodeInfo.singleIdentity(), false).returnValue.getOrThrow()
            val current = (it + 1) * 2
            if (current % 100 == 0) {
                println("${Instant.now()} $current")
            }
        }
    }
}

private fun DriverDSL.asds(nodeA: NodeHandle, nodeB: NodeHandle, nodeC: NodeHandle, backchainLength: Int) {
    timeBackchainResolution(nodeA, nodeB, nodeC, backchainLength) // warm up
    var time = 0L
    repeat(3) {
        val duration = timeBackchainResolution(nodeA, nodeB, nodeC, backchainLength)
        println("Backchain length $backchainLength took $duration ms")
        time += duration
    }
    println("Average ${time / 3} ms")
}

private fun DriverDSL.timeBackchainResolution(nodeA: NodeHandle, nodeB: NodeHandle, nodeC: NodeHandle, backchainLength: Int): Long {
    nodeA.rpc.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(1), defaultNotaryIdentity).returnValue.getOrThrow()
    repeat(backchainLength / 2) {
        nodeA.rpc.startFlow(::CashPaymentFlow, 1.DOLLARS, nodeB.nodeInfo.singleIdentity(), false).returnValue.getOrThrow()
        nodeB.rpc.startFlow(::CashPaymentFlow, 1.DOLLARS, nodeA.nodeInfo.singleIdentity(), false).returnValue.getOrThrow()
    }
    val start = System.currentTimeMillis()
    nodeA.rpc.startFlow(::CashPaymentFlow, 1.DOLLARS, nodeC.nodeInfo.singleIdentity(), false).returnValue.getOrThrow()
    return System.currentTimeMillis() - start
}