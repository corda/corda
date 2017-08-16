package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.core.contracts.Amount
import net.corda.finance.USD
import net.corda.core.flows.FlowException
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.CashFlowCommand
import net.corda.loadtest.LoadTest

object StabilityTest {
    private val log = loggerFor<StabilityTest>()
    fun crossCashTest(replication: Int) = LoadTest<CrossCashCommand, Unit>(
            "Creating Cash transactions",
            generate = { _, _ ->
                val payments = simpleNodes.flatMap { payer -> simpleNodes.map { payer to it } }
                        .filter { it.first != it.second }
                        .map { (payer, payee) -> CrossCashCommand(CashFlowCommand.PayCash(Amount(1, USD), payee.info.legalIdentity, anonymous = true), payer) }
                Generator.pure(List(replication) { payments }.flatten())
            },
            interpret = { _, _ -> },
            execute = { command ->
                val result = command.command.startFlow(command.node.proxy).returnValue
                result.thenMatch({
                    log.info("Success[$command]: $result")
                }, {
                    log.error("Failure[$command]", it)
                })
            },
            gatherRemoteState = {}
    )

    fun selfIssueTest(replication: Int) = LoadTest<SelfIssueCommand, Unit>(
            "Self issuing lot of cash",
            generate = { _, _ ->
                // Self issue cash is fast, its ok to flood the node with this command.
                val generateIssue =
                        simpleNodes.map { issuer ->
                            SelfIssueCommand(CashFlowCommand.IssueCash(Amount(100000, USD), OpaqueBytes.of(0), issuer.info.legalIdentity, notary.info.notaryIdentity, anonymous = true), issuer)
                        }
                Generator.pure(List(replication) { generateIssue }.flatten())
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
