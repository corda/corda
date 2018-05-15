/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.loadtest.tests

import de.danielbechler.diff.ObjectDifferFactory
import net.corda.client.mock.Generator
import net.corda.finance.contracts.asset.Cash
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.USD
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.loadtest.LoadTest
import net.corda.loadtest.NodeConnection
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("SelfIssue")

// DOCS START 1
data class SelfIssueCommand(
        val request: IssueAndPaymentRequest,
        val node: NodeConnection
)

data class SelfIssueState(
        val vaultsSelfIssued: Map<AbstractParty, Long>
) {
    fun copyVaults(): HashMap<AbstractParty, Long> {
        return HashMap(vaultsSelfIssued)
    }
}

val selfIssueTest = LoadTest<SelfIssueCommand, SelfIssueState>(
        // DOCS END 1
        "Self issuing cash randomly",
        generate = { _, parallelism ->
            val notaryIdentity = simpleNodes[0].proxy.notaryIdentities().first()
            val generateIssue = Generator.pickOne(simpleNodes).flatMap { node ->
                generateIssue(1000, USD, notaryIdentity, listOf(node.mainIdentity)).map {
                    SelfIssueCommand(it, node)
                }
            }
            Generator.replicatePoisson(parallelism.toDouble(), generateIssue).flatMap {
                // We need to generate at least one
                if (it.isEmpty()) {
                    Generator.sequence(listOf(generateIssue))
                } else {
                    Generator.pure(it)
                }
            }
        },

        interpret = { state, (request, node) ->
            val vaults = state.copyVaults()
            val issuer = node.mainIdentity
            vaults[issuer] = (vaults[issuer] ?: 0L) + request.amount.quantity
            SelfIssueState(vaults)
        },

        execute = { (request, node) ->
            try {
                val result = node.proxy.startFlow(::CashIssueAndPaymentFlow, request).returnValue.getOrThrow()
                log.info("Success: $result")
            } catch (e: FlowException) {
                log.error("Failure", e)
            }
        },

        gatherRemoteState = { previousState ->
            val selfIssueVaults = HashMap<AbstractParty, Long>()
            simpleNodes.forEach { connection ->
                val vault = connection.proxy.vaultQueryBy<Cash.State>().states
                vault.forEach {
                    val state = it.state.data
                    val issuer = state.amount.token.issuer.party
                    if (issuer == connection.mainIdentity as AbstractParty) {
                        selfIssueVaults[issuer] = (selfIssueVaults[issuer] ?: 0L) + state.amount.quantity
                    }
                }
            }
            log.info("$selfIssueVaults")
            if (previousState != null) {
                val diff = ObjectDifferFactory.getInstance().compare(previousState.vaultsSelfIssued, selfIssueVaults)
                if (!diff.isUntouched) {

                    var diffString = ""
                    diff.visit { node, _ ->
                        if (node.isChanged && node.children.all { !it.isChanged }) {
                            diffString += "${node.propertyPath}: simulated[${node.canonicalGet(previousState.vaultsSelfIssued)}], actual[${node.canonicalGet(selfIssueVaults)}]\n"
                        }
                    }
                    throw Exception(
                            "Simulated state diverged from actual state" +
                                    "\nSimulated state:\n${previousState.vaultsSelfIssued}" +
                                    "\nActual state:\n$selfIssueVaults" +
                                    "\nDiff:\n$diffString"
                    )
                }
            }
            SelfIssueState(selfIssueVaults)
        }
)
