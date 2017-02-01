package net.corda.core.contracts

import net.corda.contracts.asset.DUMMY_CASH_ISSUER_KEY
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.composite
import net.corda.core.crypto.signWithECDSA
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_KEY_1
import net.corda.core.utilities.DUMMY_KEY_2
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.testing.ALICE
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.BOB
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
                signers = listOf(DUMMY_KEY_1.public.composite, DUMMY_KEY_2.public.composite),
                type = TransactionType.General(),
                timestamp = null
        )
        val bytes: SerializedBytes<WireTransaction> = wtx.serialized
        fun make(vararg keys: KeyPair) = SignedTransaction(bytes, keys.map { it.signWithECDSA(wtx.id.bytes) }, wtx.id)
        assertFailsWith<IllegalArgumentException> { make().verifySignatures() }

        assertEquals(
                setOf(DUMMY_KEY_1.public.composite),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_KEY_2).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public.composite),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_KEY_1).verifySignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public.composite),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { make(DUMMY_CASH_ISSUER_KEY).verifySignatures(DUMMY_KEY_1.public.composite) }.missing
        )

        make(DUMMY_KEY_1).verifySignatures(DUMMY_KEY_2.public.composite)
        make(DUMMY_KEY_2).verifySignatures(DUMMY_KEY_1.public.composite)

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
        val signers = listOf(DUMMY_NOTARY_KEY.public.composite)
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
    fun `transaction verification fails for duplicate inputs`() {
        val baseOutState = TransactionState(DummyContract.SingleOwnerState(0, ALICE_PUBKEY), DUMMY_NOTARY)
        val stateRef = StateRef(SecureHash.randomSHA256(), 0)
        val stateAndRef = StateAndRef(baseOutState, stateRef)
        val inputs = listOf(stateAndRef, stateAndRef)
        val outputs = listOf(baseOutState)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public.composite)
        val timestamp: Timestamp? = null
        val transaction: LedgerTransaction = LedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                DUMMY_NOTARY,
                signers,
                timestamp,
                TransactionType.General()
        )

        assertFailsWith<TransactionVerificationException.DuplicateInputStates> { transaction.type.verify(transaction) }
    }

    @Test
    fun `general transactions cannot change notary`() {
        val notary: Party.Full = DUMMY_NOTARY
        val inState = TransactionState(DummyContract.SingleOwnerState(0, ALICE_PUBKEY), notary)
        val outState = inState.copy(notary = ALICE)
        val inputs = listOf(StateAndRef(inState, StateRef(SecureHash.randomSHA256(), 0)))
        val outputs = listOf(outState)
        val commands = emptyList<AuthenticatedObject<CommandData>>()
        val attachments = emptyList<Attachment>()
        val id = SecureHash.randomSHA256()
        val signers = listOf(DUMMY_NOTARY_KEY.public.composite)
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
