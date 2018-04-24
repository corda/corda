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

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaxTransactionSizeTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var notaryNode: StartedMockNode
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var notary: Party
    private lateinit var alice: Party
    private lateinit var bob: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(listOf("net.corda.testing.contracts", "net.corda.node.services.transactions"), networkParameters = testNetworkParameters(maxTransactionSize = 3_000_000))
        aliceNode = mockNet.createNode(MockNodeParameters(legalName = ALICE_NAME))
        bobNode = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME))
        notaryNode = mockNet.defaultNotaryNode
        notary = mockNet.defaultNotaryIdentity
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `check transaction will fail when exceed max transaction size limit`() {
        // These 4 attachments yield a transaction that's got ~ 4mb, which will exceed the 3mb max transaction size limit
        val bigFile1 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 0)
        val bigFile2 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 1)
        val bigFile3 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 2)
        val bigFile4 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 3)
        val flow = aliceNode.transaction {
            val hash1 = aliceNode.services.attachments.importAttachment(bigFile1.inputStream)
            val hash2 = aliceNode.services.attachments.importAttachment(bigFile2.inputStream)
            val hash3 = aliceNode.services.attachments.importAttachment(bigFile3.inputStream)
            val hash4 = aliceNode.services.attachments.importAttachment(bigFile4.inputStream)
            assertEquals(hash1, bigFile1.sha256)
            SendLargeTransactionFlow(notary, bob, hash1, hash2, hash3, hash4)
        }
        val exception = assertFailsWith<IllegalArgumentException> {
            val future = aliceNode.startFlow(flow)
            mockNet.runNetwork()
            future.getOrThrow()
        }
        assertThat(exception).hasMessageContaining("Transaction exceeded network's maximum transaction size limit")
    }

    @Test
    fun `check transaction will be rejected by counterparty when exceed max transaction size limit`() {
        // These 4 attachments yield a transaction that's got ~ 4mb, which will exceed the 3mb max transaction size limit
        val bigFile1 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 0)
        val bigFile2 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 1)
        val bigFile3 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 2)
        val bigFile4 = InputStreamAndHash.createInMemoryTestZip(1024 * 1024, 3)
        val flow = aliceNode.transaction {
            val hash1 = aliceNode.services.attachments.importAttachment(bigFile1.inputStream)
            val hash2 = aliceNode.services.attachments.importAttachment(bigFile2.inputStream)
            val hash3 = aliceNode.services.attachments.importAttachment(bigFile3.inputStream)
            val hash4 = aliceNode.services.attachments.importAttachment(bigFile4.inputStream)
            assertEquals(hash1, bigFile1.sha256)
            SendLargeTransactionFlow(notary, bob, hash1, hash2, hash3, hash4, verify = false)
        }
        assertFailsWith<UnexpectedFlowEndException> {
            val future = aliceNode.startFlow(flow)
            mockNet.runNetwork()
            future.getOrThrow()
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SendLargeTransactionFlow(private val notary: Party,
                                   private val otherSide: Party,
                                   private val hash1: SecureHash,
                                   private val hash2: SecureHash,
                                   private val hash3: SecureHash,
                                   private val hash4: SecureHash,
                                   private val verify: Boolean = true) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val tx = TransactionBuilder(notary = notary)
                    .addOutputState(DummyState(), DummyContract.PROGRAM_ID)
                    .addCommand(dummyCommand(ourIdentity.owningKey))
                    .addAttachment(hash1)
                    .addAttachment(hash2)
                    .addAttachment(hash3)
                    .addAttachment(hash4)
            val stx = serviceHub.signInitialTransaction(tx, ourIdentity.owningKey)
            if (verify) stx.verify(serviceHub, checkSufficientSignatures = false)
            // Send to the other side and wait for it to trigger resolution from us.
            val otherSideSession = initiateFlow(otherSide)
            subFlow(SendTransactionFlow(otherSideSession, stx))
            otherSideSession.receive<Unit>()
        }
    }

    @InitiatedBy(SendLargeTransactionFlow::class)
    @Suppress("UNUSED")
    class ReceiveLargeTransactionFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveTransactionFlow(otherSide))
            // Unblock the other side by sending some dummy object (Unit is fine here as it's a singleton).
            otherSide.send(Unit)
        }
    }
}
