package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.pickOne
import net.corda.client.mock.replicatePoisson
import net.corda.core.contracts.USD
import net.corda.core.failure
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.success
import net.corda.core.utilities.loggerFor
import net.corda.loadtest.LoadTest

object StabilityTest {
    private val log = loggerFor<StabilityTest>()
    val crossCashTest = LoadTest<CrossCashCommand, Unit>(
            "Creating Cash transactions randomly",
            generate = { _, _ ->
                val nodeMap = simpleNodes.associateBy { it.info.legalIdentity }
                Generator.sequence(simpleNodes.map { node ->
                    val possibleRecipients = nodeMap.keys.toList()
                    val moves = 0.5 to generateMove(1, USD, node.info.legalIdentity, possibleRecipients, anonymous = true)
                    val exits = 0.5 to generateExit(1, USD)
                    val command = Generator.frequency(listOf(moves, exits))
                    command.map { CrossCashCommand(it, nodeMap[node.info.legalIdentity]!!) }
                })
            },
            interpret = { _, _ -> },
            execute = { command ->
                val result = command.command.startFlow(command.node.proxy).returnValue
                result.failure {
                    log.error("Failure[$command]", it)
                }
                result.success {
                    log.info("Success[$command]: $result")
                }
            },
            gatherRemoteState = {}
    )

    val selfIssueTest = LoadTest<SelfIssueCommand, Unit>(
            "Self issuing cash randomly",
            generate = { _, parallelism ->
                val generateIssue = Generator.pickOne(simpleNodes).bind { node ->
                    generateIssue(1000, USD, notary.info.notaryIdentity, listOf(node.info.legalIdentity), anonymous = true).map {
                        SelfIssueCommand(it, node)
                    }
                }
                Generator.replicatePoisson(parallelism.toDouble(), generateIssue).bind {
                    // We need to generate at least one
                    if (it.isEmpty()) {
                        Generator.sequence(listOf(generateIssue))
                    } else {
                        Generator.pure(it)
                    }
                }
            },

            interpret = { _, _ -> },
            execute = { command ->
                try {
                    val result = command.command.startFlow(command.node.proxy).returnValue.getOrThrow()
                    log.info("Success: $result")
                } catch (e: FlowException) {
                    log.error("Failure", e)
                }
            },
            gatherRemoteState = {}
    )
}
