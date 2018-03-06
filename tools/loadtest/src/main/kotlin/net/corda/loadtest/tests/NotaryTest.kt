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
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.loadtest.LoadTest
import net.corda.loadtest.NodeConnection
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NotaryTest")
private val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
private val DUMMY_CASH_ISSUER = dummyCashIssuer.ref(1)
private val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))

data class NotariseCommand(val issueTx: SignedTransaction, val moveTx: SignedTransaction, val node: NodeConnection)

val dummyNotarisationTest = LoadTest<NotariseCommand, Unit>(
        "Notarising dummy transactions",
        generate = { _, _ ->
            val issuerServices = MockServices(emptyList(), megaCorp.name, makeTestIdentityService(megaCorp.identity, miniCorp.identity, dummyCashIssuer.identity, dummyNotary.identity), dummyCashIssuer.keyPair)
            val generateTx = Generator.pickOne(simpleNodes).flatMap { node ->
                Generator.int().map {
                    val issueBuilder = DummyContract.generateInitial(it, notary.info.legalIdentities[0], DUMMY_CASH_ISSUER) // TODO notary choice
                    val issueTx = issuerServices.signInitialTransaction(issueBuilder)
                    val asset = issueTx.tx.outRef<DummyContract.SingleOwnerState>(0)
                    val moveBuilder = DummyContract.move(asset, dummyCashIssuer.party)
                    val moveTx = issuerServices.signInitialTransaction(moveBuilder)
                    NotariseCommand(issueTx, moveTx, node)
                }
            }
            Generator.replicate(10, generateTx)
        },
        interpret = { _, _ -> },
        execute = { (issueTx, moveTx, node) ->
            try {
                val proxy = node.proxy
                val issueFlow = proxy.startFlow(::FinalityFlow, issueTx)
                issueFlow.returnValue.thenMatch({
                    proxy.startFlow(::FinalityFlow, moveTx)
                }, {})
            } catch (e: FlowException) {
                log.error("Failure", e)
            }
        },
        gatherRemoteState = {}
)
