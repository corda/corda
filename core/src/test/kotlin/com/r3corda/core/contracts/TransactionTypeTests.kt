package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.testing.*
import org.junit.Test
import kotlin.test.assertFailsWith

class TransactionTypeTests {
    @Test
    fun `transactions with no inputs can have any notary`() {
        val baseOutState = TransactionState(DummyContract.SingleOwnerState(0, ALICE_PUBKEY), DUMMY_NOTARY)
        val inputs = emptyList<StateAndRef<*>>()
        val outputs = listOf(baseOutState, baseOutState.copy(notary = ALICE), baseOutState.copy(notary = BOB))
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timestamp: Timestamp? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                null,
                signers,
                timestamp,
                TransactionType.General()
        )

        transaction.type.verify(transaction)
    }

    @Test
    fun `general transactions cannot change notary`() {
        val notary: Party = DUMMY_NOTARY
        val inState = TransactionState(DummyContract.SingleOwnerState(0, ALICE_PUBKEY), notary)
        val outState = inState.copy(notary = ALICE)
        val inputs = listOf(StateAndRef(inState, StateRef(SecureHash.randomSHA256(), 0)))
        val outputs = listOf(outState)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timestamp: Timestamp? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                notary,
                signers,
                timestamp,
                TransactionType.General()
        )

        assertFailsWith<TransactionVerificationException.NotaryChangeInWrongTransactionType> { transaction.type.verify(transaction) }
    }
}