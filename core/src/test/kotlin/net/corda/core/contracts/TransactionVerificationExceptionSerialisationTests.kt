package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.LedgerTransaction
import net.corda.nodeapi.internal.serialization.AMQP_RPC_CLIENT_CONTEXT
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class TransactionVerificationExceptionSerialisationTests {
    private fun defaultFactory() = SerializerFactory(
            AllWhitelist,
            ClassLoader.getSystemClassLoader()
    )

    private val context get() = AMQP_RPC_CLIENT_CONTEXT

    private val txid = SecureHash.allOnesHash
    private val factory = defaultFactory()

    @Test
    fun contractConstraintRejectionTest() {
        val excp = TransactionVerificationException.ContractConstraintRejection(txid, "This is only a test")
        val excp2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(excp, context),
                context)

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause, excp2.cause)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun contractRejectionTest() {
        class TestContract(val thing: Int) : Contract {
            override fun verify(tx: LedgerTransaction) = Unit
        }

        val contract = TestContract(12)
        val cause = Throwable("wibble")

        val exception = TransactionVerificationException.ContractRejection(txid, contract, cause)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test
    fun missingAttachmentRejectionTest() {
        val exception = TransactionVerificationException.MissingAttachmentRejection(txid, "Some contract class")
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test
    fun conflictingAttachmentsRejectionTest() {
        val exception = TransactionVerificationException.ContractConstraintRejection(txid, "Some contract class")
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test
    fun contractCreationErrorTest() {
        val cause = Throwable("wibble")
        val exception = TransactionVerificationException.ContractCreationError(txid, "Some contract class", cause)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test
    fun transactionMissingEncumbranceTest() {
        val exception = TransactionVerificationException.TransactionMissingEncumbranceException(
                txid, 12, TransactionVerificationException.Direction.INPUT)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test
    fun notaryChangeInWrongTransactionTypeTest() {
        val dummyBankA = TestIdentity(DUMMY_BANK_A_NAME, 40).party
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20).party

        val factory = defaultFactory()
        factory.register(PublicKeySerializer)
        val exception = TransactionVerificationException.NotaryChangeInWrongTransactionType(txid, dummyBankA, dummyNotary)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }
}