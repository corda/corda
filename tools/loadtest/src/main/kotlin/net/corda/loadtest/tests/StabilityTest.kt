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

import net.corda.client.mock.Generator
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.USD
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashExitFlow.ExitRequest
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.flows.CashPaymentFlow.PaymentRequest
import net.corda.loadtest.LoadTest
import org.slf4j.LoggerFactory

object StabilityTest {
    private val log = LoggerFactory.getLogger(javaClass)
    fun crossCashTest(replication: Int) = LoadTest<CrossCashCommand, Unit>(
            "Creating Cash transactions",
            generate = { _, _ ->
                val payments = simpleNodes.flatMap { payer -> simpleNodes.map { payer to it } }
                        .filter { it.first != it.second }
                        .map { (payer, payee) -> CrossCashCommand(PaymentRequest(Amount(1, USD), payee.mainIdentity, anonymous = true), payer) }
                Generator.pure(List(replication) { payments }.flatten())
            },
            interpret = { _, _ -> },
            execute = { command ->
                val request = command.request
                val result = when (request) {
                    is IssueAndPaymentRequest -> command.node.proxy.startFlow(::CashIssueAndPaymentFlow, request).returnValue
                    is PaymentRequest -> command.node.proxy.startFlow(::CashPaymentFlow, request).returnValue
                    is ExitRequest -> command.node.proxy.startFlow(::CashExitFlow, request).returnValue
                    else -> throw IllegalArgumentException("Unexpected request type: $request")
                }
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
                val notaryIdentity = simpleNodes[0].proxy.notaryIdentities().first()
                // Self issue cash is fast, its ok to flood the node with this command.
                val generateIssue =
                        simpleNodes.map { issuer ->
                            SelfIssueCommand(IssueAndPaymentRequest(Amount(100000, USD), OpaqueBytes.of(0), issuer.mainIdentity, notaryIdentity, anonymous = true), issuer)
                        }
                Generator.pure(List(replication) { generateIssue }.flatten())
            },
            interpret = { _, _ -> },
            execute = { (request, node) ->
                try {
                    val result = node.proxy.startFlow(::CashIssueAndPaymentFlow, request).returnValue.getOrThrow()
                    log.info("Success: $result")
                } catch (e: FlowException) {
                    log.error("Failure", e)
                }
            },
            gatherRemoteState = {}
    )
}
