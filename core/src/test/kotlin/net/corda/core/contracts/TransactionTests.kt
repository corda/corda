package net.corda.core.contracts

import net.corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.sign
import net.corda.core.identity.Party
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.*
import org.junit.Test
import java.security.KeyPair
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionTests {

    private fun makeSigned(wtx: WireTransaction, vararg keys: KeyPair): SignedTransaction {
        val bytes: SerializedBytes<WireTransaction> = wtx.serialized
        return SignedTransaction(bytes, keys.map { it.sign(wtx.id.bytes) })
    }

    @Test
    fun `signed transaction missing signatures - CompositeKey`() {
        val ak = generateKeyPair()
        val bk = generateKeyPair()
        val ck = generateKeyPair()
        val apub = ak.public
        val bpub = bk.public
        val cpub = ck.public
        val c1 = CompositeKey.Builder().addKeys(apub, bpub).build(2)
        val compKey = CompositeKey.Builder().addKeys(c1, cpub).build(1)
        val wtx = WireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = emptyList(),
                notary = DUMMY_NOTARY,
                signers = listOf(compKey, DUMMY_KEY_1.public, DUMMY_KEY_2.public),
                type = TransactionType.General,
                timeWindow = null
        )
        assertEquals(
                setOf(compKey, DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1).verifySignatures() }.missing
        )

        assertEquals(
                setOf(compKey, DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1, ak).verifySignatures() }.missing
        )
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak, bk).verifySignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ck).verifySignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak, bk, ck).verifySignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak).verifySignatures(compKey)
        makeSigned(wtx, DUMMY_KEY_1, ak).verifySignatures(compKey, DUMMY_KEY_2.public) // Mixed allowed to be missing.
    }

    @Test
    fun `signed transaction missing signatures`() {
        val wtx = WireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = emptyList(),
                notary = DUMMY_NOTARY,
                signers = listOf(DUMMY_KEY_1.public, DUMMY_KEY_2.public),
                type = TransactionType.General,
                timeWindow = null
        )
        assertFailsWith<IllegalArgumentException> { makeSigned(wtx).verifySignatures() }

        assertEquals(
                setOf(DUMMY_KEY_1.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_2).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_CASH_ISSUER_KEY).verifySignatures(DUMMY_KEY_1.public) }.missing
        )

        makeSigned(wtx, DUMMY_KEY_1).verifySignatures(DUMMY_KEY_2.public)
        makeSigned(wtx, DUMMY_KEY_2).verifySignatures(DUMMY_KEY_1.public)

        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2).verifySignatures()
    }

    @Test
    fun `transactions with no inputs can have any notary`() {
        val baseOutState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DUMMY_NOTARY)
        val inputs = emptyList<StateAndRef<*>>()
        val outputs = listOf(baseOutState, baseOutState.copy(notary = ALICE), baseOutState.copy(notary = BOB))
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timeWindow: TimeWindow? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                null,
                signers,
                timeWindow,
                TransactionType.General
        )

        transaction.type.verify(transaction)
    }

    @Test
    fun `transaction verification fails for duplicate inputs`() {
        val baseOutState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DUMMY_NOTARY)
        val stateRef = StateRef(SecureHash.randomSHA256(), 0)
        val stateAndRef = StateAndRef(baseOutState, stateRef)
        val inputs = listOf(stateAndRef, stateAndRef)
        val outputs = listOf(baseOutState)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timeWindow: TimeWindow? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                DUMMY_NOTARY,
                signers,
                timeWindow,
                TransactionType.General
        )

        assertFailsWith<TransactionVerificationException.DuplicateInputStates> { transaction.type.verify(transaction) }
    }

    @Test
    fun `general transactions cannot change notary`() {
        val notary: Party = DUMMY_NOTARY
        val inState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), notary)
        val outState = inState.copy(notary = ALICE)
        val inputs = listOf(StateAndRef(inState, StateRef(SecureHash.randomSHA256(), 0)))
        val outputs = listOf(outState)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public)
        val timeWindow: TimeWindow? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                notary,
                signers,
                timeWindow,
                TransactionType.General
        )

        assertFailsWith<TransactionVerificationException.NotaryChangeInWrongTransactionType> { transaction.type.verify(transaction) }
    }
}
