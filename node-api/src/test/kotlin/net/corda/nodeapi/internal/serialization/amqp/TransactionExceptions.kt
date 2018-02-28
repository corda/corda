package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.contracts.Contract
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.assertEqualAfterRoundTripSerialization
import net.corda.nodeapiinterfaces.serialization.SerializationOnlyParameter
import net.corda.nodeapiinterfaces.serialization.UnusedConstructorParameter
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals

class TransactionExceptions {
    private fun defaultFactory() = SerializerFactory(
            AllWhitelist,
            ClassLoader.getSystemClassLoader(),
            EvolutionSerializerGetterTesting())

    val txid = SecureHash.allOnesHash
    val factory = defaultFactory()

    @Test
    fun contractConstrainRejectionTest() {
        val excp = TransactionVerificationException.ContractConstraintRejection(txid, "This is only a test")
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

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

        val excp = TransactionVerificationException.ContractRejection(txid, contract, cause)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun missingAttachmentRejectionTest() {
        val excp = TransactionVerificationException.MissingAttachmentRejection(txid, "Some contract class")
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun conflictingAttachmentsRejectionTest() {
        val excp = TransactionVerificationException.ContractConstraintRejection(txid, "Some contract class")
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun contractCreationErrorTest() {
        val cause = Throwable("wibble")
        val excp = TransactionVerificationException.ContractCreationError(txid, "Some contract class", cause)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun moreThanOneNotaryTest() {
        val excp = TransactionVerificationException.MoreThanOneNotary(txid)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun signersMissingTest() {
        class TestKey(val b : ByteArray) : PublicKey {
            override fun getAlgorithm() = "someStupidTest"
            override fun getEncoded() = b
            override fun getFormat() = "testFormat"
        }

        val k1 = TestKey(ByteArray(50).also { Random(0).nextBytes(it) })
        val k2 = TestKey(ByteArray(50).also { Random(0).nextBytes(it) })
        val keys = listOf(k1, k2)

        val excp = TransactionVerificationException.SignersMissing(txid, keys)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun invalidNotaryChangeTest() {
        val excp = TransactionVerificationException.InvalidNotaryChange(txid)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun transactionMissingEncumbranceTest() {
        val excp = TransactionVerificationException.TransactionMissingEncumbranceException(
                txid, 12, TransactionVerificationException.Direction.INPUT)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun duplicateInputStateTest() {
        val duplicates = NonEmptySet.copyOf(setOf(
                StateRef(SecureHash.randomSHA256(), 1),
                StateRef(SecureHash.randomSHA256(), 2)))

        val excp = TransactionVerificationException.DuplicateInputStates(txid, duplicates)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun notaryyChangeInWrongTransactionTypeTest() {
        val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 40).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party

        val excp = TransactionVerificationException.NotaryChangeInWrongTransactionType(txid, DUMMY_BANK_A, DUMMY_NOTARY)
        val excp2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(excp))

        assertEquals(excp.message, excp2.message)
        assertEquals(excp.cause?.message, excp2.cause?.message)
        assertEquals(excp.txId, excp2.txId)
    }

    @Test
    fun silly() {
        class Example {
            var property = 0

            constructor(property: Int) {
                this.property = property + 1
            }
        }

        val eg1 = Example(99)
        println (eg1.property)

        val eg2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(eg1))
        println (eg2.property)

        class CatException(msg: String, @UnusedConstructorParameter p: SerializationOnlyParameter?)
            : Exception(msg) {
            constructor(name: String) : this ("Cat $name was a bad girl", null)
        }

        throw CatException("Jane")
    }
}