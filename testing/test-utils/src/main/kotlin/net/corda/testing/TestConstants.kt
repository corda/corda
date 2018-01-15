@file:JvmName("TestConstants")

package net.corda.testing

import net.corda.core.contracts.Command
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import java.security.PublicKey
import java.time.Instant

// A dummy time at which we will be pretending test transactions are created.
@JvmField
val TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z")
@JvmField
val DUMMY_NOTARY_NAME = CordaX500Name("Notary Service", "Zurich", "CH")
@JvmField
val DUMMY_BANK_A_NAME = CordaX500Name("Bank A", "London", "GB")
@JvmField
val DUMMY_BANK_B_NAME = CordaX500Name("Bank B", "New York", "US")
@JvmField
val DUMMY_BANK_C_NAME = CordaX500Name("Bank C", "Tokyo", "JP")
@JvmField
val BOC_NAME = CordaX500Name("BankOfCorda", "London", "GB")
@JvmField
val ALICE_NAME = CordaX500Name("Alice Corp", "Madrid", "ES")
@JvmField
val BOB_NAME = CordaX500Name("Bob Plc", "Rome", "IT")
@JvmField
val CHARLIE_NAME = CordaX500Name("Charlie Ltd", "Athens", "GR")

val DEV_INTERMEDIATE_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA }

val DEV_ROOT_CA: CertificateAndKeyPair by lazy { net.corda.nodeapi.internal.DEV_ROOT_CA }

fun dummyCommand(vararg signers: PublicKey = arrayOf(generateKeyPair().public)) = Command<TypeOnlyCommandData>(DummyCommandData, signers.toList())

object DummyCommandData : TypeOnlyCommandData()

/** Maximum artemis message size. 10 MiB maximum allowed file size for attachments, including message headers. */
const val MAX_MESSAGE_SIZE: Int = 10485760
