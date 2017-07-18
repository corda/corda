package net.corda.loadtest.tests

import de.danielbechler.diff.ObjectDifferFactory
import net.corda.client.mock.Generator
import net.corda.client.mock.pickOne
import net.corda.client.mock.replicatePoisson
import net.corda.client.rpc.notUsed
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.USD
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.concurrent.getOrThrow
import net.corda.flows.CashFlowCommand
import net.corda.loadtest.LoadTest
import net.corda.loadtest.NodeConnection
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("SelfIssue")

// DOCS START 1
data class SelfIssueCommand(
        val command: CashFlowCommand.IssueCash,
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
            val generateIssue = Generator.pickOne(simpleNodes).flatMap { node ->
                generateIssue(1000, USD, notary.info.notaryIdentity, listOf(node.info.legalIdentity), anonymous = true).map {
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

        interpret = { state, command ->
            val vaults = state.copyVaults()
            val issuer = command.node.info.legalIdentity
            vaults.put(issuer, (vaults[issuer] ?: 0L) + command.command.amount.quantity)
            SelfIssueState(vaults)
        },

        execute = { command ->
            try {
                val result = command.command.startFlow(command.node.proxy).returnValue.getOrThrow()
                log.info("Success: $result")
            } catch (e: FlowException) {
                log.error("Failure", e)
            }
        },

        gatherRemoteState = { previousState ->
            val selfIssueVaults = HashMap<AbstractParty, Long>()
            simpleNodes.forEach { connection ->
                val (vault, vaultUpdates) = connection.proxy.vaultAndUpdates()
                vaultUpdates.notUsed()
                vault.forEach {
                    val state = it.state.data
                    if (state is Cash.State) {
                        val issuer = state.amount.token.issuer.party
                        if (issuer == connection.info.legalIdentity as AbstractParty) {
                            selfIssueVaults.put(issuer, (selfIssueVaults[issuer] ?: 0L) + state.amount.quantity)
                        }
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
