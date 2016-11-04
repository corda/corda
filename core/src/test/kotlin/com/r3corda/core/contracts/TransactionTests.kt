package com.r3corda.core.contracts

import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.transactions.LedgerTransaction
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.DUMMY_KEY_1
import com.r3corda.core.utilities.DUMMY_KEY_2
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.testing.ALICE
import com.r3corda.testing.ALICE_PUBKEY
import com.r3corda.testing.BOB
import org.junit.Test
import java.security.KeyPair
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionTests {
    @Test
    fun `signed transaction missing signatures`() {
        val wtx = WireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = emptyList(),
                notary = DUMMY_NOTARY,
                signers = listOf(DUMMY_KEY_1.public, DUMMY_KEY_2.public),
                type = TransactionType.General(),
                timestamp = null
        )
        val bits: SerializedBytes<WireTransaction> = wtx.serialized
        fun make(vararg keys: KeyPair) = SignedTransaction(bits, keys.map { it.signWithECDSA(wtx.id.bits) }, wtx.id)
        assertFailsWith<IllegalArgumentException> { make().verifySignatures() }

        assertEquals(
                setOf(DUMMY_KEY_1.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_KEY_2).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_KEY_1).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_CASH_ISSUER_KEY).verifySignatures(DUMMY_KEY_1.public) }.missing
        )

        make(DUMMY_KEY_1).verifySignatures(DUMMY_KEY_2.public)
        make(DUMMY_KEY_2).verifySignatures(DUMMY_KEY_1.public)

        make(DUMMY_KEY_1, DUMMY_KEY_2).verifySignatures()
    }

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
