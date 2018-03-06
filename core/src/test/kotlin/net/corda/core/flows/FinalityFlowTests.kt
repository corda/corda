/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.testing.core.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinalityFlowTests {
    companion object {
        private val CHARLIE = TestIdentity(CHARLIE_NAME, 90).party
    }

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork(cordappPackages = listOf("net.corda.finance.contracts.asset"))
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `finalise a simple transaction`() {
        val amount = 1000.POUNDS.issuedBy(alice.ref(0))
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, bob, notary)
        val stx = aliceNode.services.signInitialTransaction(builder)
        val flow = aliceNode.startFlow(FinalityFlow(stx))
        mockNet.runNetwork()
        val notarisedTx = flow.getOrThrow()
        notarisedTx.verifyRequiredSignatures()
        val transactionSeenByB = bobNode.transaction {
            bobNode.services.validatedTransactions.getTransaction(notarisedTx.id)
        }
        assertEquals(notarisedTx, transactionSeenByB)
    }

    @Test
    fun `reject a transaction with unknown parties`() {
        val amount = 1000.POUNDS.issuedBy(alice.ref(0))
        val fakeIdentity = CHARLIE // Charlie isn't part of this network, so node A won't recognise them
        val builder = TransactionBuilder(notary)
        Cash().generateIssue(builder, amount, fakeIdentity, notary)
        val stx = aliceNode.services.signInitialTransaction(builder)
        val flow = aliceNode.startFlow(FinalityFlow(stx))
        mockNet.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            flow.getOrThrow()
        }
    }
}