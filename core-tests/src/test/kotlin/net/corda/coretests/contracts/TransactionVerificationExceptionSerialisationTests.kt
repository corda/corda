package net.corda.coretests.contracts

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.Contract
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.core.internal.createContractCreationError
import net.corda.core.internal.createContractRejection
import net.corda.core.transactions.LedgerTransaction
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.custom.PublicKeySerializer
import net.corda.serialization.internal.amqp.custom.ThrowableSerializer
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class TransactionVerificationExceptionSerialisationTests {
    private fun defaultFactory() = SerializerFactoryBuilder.build(
            AllWhitelist,
            ClassLoader.getSystemClassLoader()
    ).apply {
        register(ThrowableSerializer(this))
    }

    private val context get() = AMQP_RPC_CLIENT_CONTEXT

    private val txid = SecureHash.allOnesHash
    private val attachmentHash = SecureHash.allOnesHash
    private val factory = defaultFactory()

    @Test(timeout=300_000)
	fun contractConstraintRejectionTest() {
        val excp = TransactionVerificationException.ContractConstraintRejection(txid, "This is only a test")
        val excp2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(excp, context),
                context)

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause, excp2.cause)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test(timeout=300_000)
	fun contractRejectionTest() {
        class TestContract(val thing: Int) : Contract {
            override fun verify(tx: LedgerTransaction) = Unit
        }

        val contract = TestContract(12)
        val cause = Throwable("wibble")

        val exception = createContractRejection(txid, contract, cause)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals("java.lang.Throwable: ${exception.cause?.message}", exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun missingAttachmentRejectionTest() {
        val exception = TransactionVerificationException.MissingAttachmentRejection(txid, "Some contract class")
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun conflictingAttachmentsRejectionTest() {
        val exception = TransactionVerificationException.ContractConstraintRejection(txid, "Some contract class")
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun invalidConstraintRejectionError() {
        val exception = TransactionVerificationException.InvalidConstraintRejection(txid, "Some contract class", "for being too funny")
        val exceptionAfterSerialisation = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context
        )

        assertEquals(exception.message, exceptionAfterSerialisation.message)
        assertEquals(exception.cause?.message, exceptionAfterSerialisation.cause?.message)
        assertEquals(exception.contractClass, exceptionAfterSerialisation.contractClass)
        assertEquals(exception.reason, exceptionAfterSerialisation.reason)
    }

    @Test(timeout=300_000)
	fun contractCreationErrorTest() {
        val cause = Throwable("wibble")
        val exception = createContractCreationError(txid, "Some contract class", cause)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals("java.lang.Throwable: ${exception.cause?.message}", exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun overlappingAttachmentsExceptionTest() {
        val exc = TransactionVerificationException.OverlappingAttachmentsException(txid, "foo/bar/baz")
        val exc2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exc, context),
                context)

        assertEquals(exc.message, exc2.message)
    }

    @Test(timeout=300_000)
	fun packageOwnershipExceptionTest() {
        val exc = TransactionVerificationException.PackageOwnershipException(
                txid,
                attachmentHash,
                "InvalidClass",
                "com.invalid")

        val exc2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exc, context),
                context)

        assertEquals(exc.message, exc2.message)
    }

    @Test(timeout=300_000)
	fun invalidAttachmentExceptionTest() {
        val exc = TransactionVerificationException.InvalidAttachmentException(
                txid,
                attachmentHash)

        val exc2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exc, context),
                context)

        assertEquals(exc.message, exc2.message)
    }

    @Test(timeout=300_000)
	fun untrustedAttachmentsExceptionTest() {
        val exc = TransactionVerificationException.UntrustedAttachmentsException(
                txid,
                listOf(attachmentHash))

        val exc2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exc, context),
                context)

        assertEquals(exc.message, exc2.message)
    }

    @Test(timeout=300_000)
	fun transactionNetworkParameterOrderingExceptionTest() {
        val exception = TransactionVerificationException.TransactionNetworkParameterOrderingException(
                txid,
                StateRef(SecureHash.zeroHash, 1),
                testNetworkParameters(),
                testNetworkParameters())
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun missingNetworkParametersExceptionTest() {
        val exception = TransactionVerificationException.MissingNetworkParametersException(txid, SecureHash.zeroHash)
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun constraintPropagationRejectionTest() {
        val exception = TransactionVerificationException.ConstraintPropagationRejection(txid, "com.test.Contract",
                AlwaysAcceptAttachmentConstraint, AlwaysAcceptAttachmentConstraint)
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
        assertEquals("com.test.Contract", exception2.contractClass)
    }

    @Test(timeout=300_000)
	fun transactionDuplicateEncumbranceExceptionTest() {
        val exception = TransactionVerificationException.TransactionDuplicateEncumbranceException(txid, 1)
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun transactionNonMatchingEncumbranceExceptionTest() {
        val exception = TransactionVerificationException.TransactionNonMatchingEncumbranceException(txid, listOf(1, 2, 3))
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun transactionNotaryMismatchEncumbranceExceptionTest() {
        val exception = TransactionVerificationException.TransactionNotaryMismatchEncumbranceException(
                txid, 1, 2, Party(ALICE_NAME, generateKeyPair().public), Party(BOB_NAME, generateKeyPair().public))
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun transactionContractConflictExceptionTest() {
        val exception = TransactionVerificationException.TransactionContractConflictException(
                txid, TransactionState(DummyContractState(), notary = Party(BOB_NAME, generateKeyPair().public)), "aa")
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
	fun transactionRequiredContractUnspecifiedExceptionTest() {
        val exception = TransactionVerificationException.TransactionRequiredContractUnspecifiedException(
                txid, TransactionState(DummyContractState(), notary = Party(BOB_NAME, generateKeyPair().public)))
        val exception2 = DeserializationInput(factory)
                .deserialize(
                        SerializationOutput(factory)
                                .serialize(exception, context),
                        context)

        assertEquals(exception.message, exception2.message)
        assertEquals(exception.cause?.message, exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }

    @Test(timeout=300_000)
    fun unsupportedClassVersionErrorTest() {
        val cause = UnsupportedClassVersionError("wobble")
        val exception = TransactionVerificationException.UnsupportedClassVersionError(txid, cause.message!!, cause)
        val exception2 = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(exception, context),
                context)

        assertEquals(exception.message, exception2.message)
        assertEquals("java.lang.UnsupportedClassVersionError: ${exception.cause?.message}", exception2.cause?.message)
        assertEquals(exception.txId, exception2.txId)
    }
}