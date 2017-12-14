@file:JvmName("TestConstants")

package net.corda.testing

import net.corda.core.contracts.Command
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toX509CertHolder
import net.corda.nodeapi.internal.crypto.*
import org.bouncycastle.cert.X509CertificateHolder
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
val DEV_CA: CertificateAndKeyPair by lazy {
    // TODO: Should be identity scheme
    val caKeyStore = loadKeyStore(ClassLoader.getSystemResourceAsStream("certificates/cordadevcakeys.jks"), "cordacadevpass")
    caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_INTERMEDIATE_CA, "cordacadevkeypass")
}

val ROOT_CA: CertificateAndKeyPair by lazy {
    // TODO: Should be identity scheme
    val caKeyStore = loadKeyStore(ClassLoader.getSystemResourceAsStream("certificates/cordadevcakeys.jks"), "cordacadevpass")
    caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA, "cordacadevkeypass")
}
val DEV_TRUST_ROOT: X509CertificateHolder by lazy {
    // TODO: Should be identity scheme
    val caKeyStore = loadKeyStore(ClassLoader.getSystemResourceAsStream("certificates/cordadevcakeys.jks"), "cordacadevpass")
    caKeyStore.getCertificateChain(X509Utilities.CORDA_INTERMEDIATE_CA).last().toX509CertHolder()
}

fun dummyCommand(vararg signers: PublicKey = arrayOf(generateKeyPair().public)) = Command<TypeOnlyCommandData>(DummyCommandData, signers.toList())

object DummyCommandData : TypeOnlyCommandData()
