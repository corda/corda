/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.issueInvalidState
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidatingNotaryServiceTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var aliceNode: StartedMockNode
    private lateinit var notary: Party
    private lateinit var alice: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = listOf("net.corda.testing.contracts"))
        aliceNode = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        notaryNode = mockNet.defaultNotaryNode
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(aliceNode.services, alice, notary)
            val tx = TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addCommand(dummyCommand(alice.owningKey))
            aliceNode.services.signInitialTransaction(tx)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(NotaryException::class) { future.getOrThrow() }
        val notaryError = ex.error as NotaryError.TransactionInvalid
        assertThat(notaryError.cause).isInstanceOf(SignedTransaction.SignaturesMissingException::class.java)
    }

    @Test
    fun `should report error for missing signatures`() {
        val expectedMissingKey = generateKeyPair().public
        val stx = run {
            val inputState = issueState(aliceNode.services, alice)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionBuilder(notary).withItems(inputState, command)
            aliceNode.services.signInitialTransaction(tx)
        }

        // Expecting SignaturesMissingException instead of NotaryException, since the exception should originate from
        // the client flow.
        val ex = assertFailsWith<SignedTransaction.SignaturesMissingException> {
            val future = runClient(stx)
            future.getOrThrow()
        }
        val missingKeys = ex.missing
        assertEquals(setOf(expectedMissingKey), missingKeys)
    }

    private fun runClient(stx: SignedTransaction): CordaFuture<List<TransactionSignature>> {
        val flow = NotaryFlow.Client(stx)
        val future = aliceNode.startFlow(flow)
        mockNet.runNetwork()
        return future
    }

    private fun issueState(serviceHub: ServiceHub, identity: Party): StateAndRef<*> {
        val tx = DummyContract.generateInitial(Random().nextInt(), notary, identity.ref(0))
        val signedByNode = serviceHub.signInitialTransaction(tx)
        val stx = notaryNode.services.addSignature(signedByNode, notary.owningKey)
        serviceHub.recordTransactions(stx)
        return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
    }
}
