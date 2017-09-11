@file:JvmName("TestConstants")

package net.corda.testing

import net.corda.core.contracts.Command
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.core.utilities.getX500Name
import net.corda.node.internal.AbstractNode
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.getCertificateAndKeyPair
import net.corda.node.utilities.loadKeyStore
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

val DUMMY_KEY_1: KeyPair by lazy { generateKeyPair() }
val DUMMY_KEY_2: KeyPair by lazy { generateKeyPair() }

val DUMMY_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
/** Dummy notary identity for tests and simulations */
val DUMMY_NOTARY_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(DUMMY_NOTARY)
val DUMMY_NOTARY: Party get() = Party(getX500Name(O = "Notary Service", OU = "corda", L = "Zurich", C = "CH"), DUMMY_NOTARY_KEY.public)

val DUMMY_MAP_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(30)) }
/** Dummy network map service identity for tests and simulations */
val DUMMY_MAP: Party get() = Party(getX500Name(O = "Network Map Service", OU = "corda", L = "Amsterdam", C = "NL"), DUMMY_MAP_KEY.public)

val DUMMY_BANK_A_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(40)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_A: Party get() = Party(getX500Name(O = "Bank A", L = "London", C = "GB"), DUMMY_BANK_A_KEY.public)

val DUMMY_BANK_B_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(50)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_B: Party get() = Party(getX500Name(O = "Bank B", L = "New York", C = "US"), DUMMY_BANK_B_KEY.public)

val DUMMY_BANK_C_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(60)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_C: Party get() = Party(getX500Name(O = "Bank C", L = "Tokyo", C = "JP"), DUMMY_BANK_C_KEY.public)

val ALICE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(70)) }
/** Dummy individual identity for tests and simulations */
val ALICE_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(ALICE)
val ALICE: Party get() = Party(getX500Name(O = "Alice Corp", L = "Madrid", C = "ES"), ALICE_KEY.public)

val BOB_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(80)) }
/** Dummy individual identity for tests and simulations */
val BOB_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(BOB)
val BOB: Party get() = Party(getX500Name(O = "Bob Plc", L = "Rome", C = "IT"), BOB_KEY.public)

val CHARLIE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(90)) }
/** Dummy individual identity for tests and simulations */
val CHARLIE_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CHARLIE)
val CHARLIE: Party get() = Party(getX500Name(O = "Charlie Ltd", L = "Athens", C = "GR"), CHARLIE_KEY.public)

val DUMMY_REGULATOR_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(100)) }
/** Dummy regulator for tests and simulations */
val DUMMY_REGULATOR: Party get() = Party(getX500Name(O = "Regulator A", OU = "Corda", L = "Paris", C = "FR"), DUMMY_REGULATOR_KEY.public)

val DUMMY_CA_KEY: KeyPair by lazy { DUMMY_CA.keyPair }
val DUMMY_CA: CertificateAndKeyPair by lazy {
    val caKeyStore = loadKeyStore(AbstractNode::class.java.classLoader.getResourceAsStream("net/corda/node/internal/certificates/cordadevcakeys.jks"), "cordacadevpass")
    caKeyStore.getCertificateAndKeyPair(X509Utilities.CORDA_ROOT_CA,"cordacadevkeypass")
}

fun dummyCommand(vararg signers: PublicKey = arrayOf(generateKeyPair().public) ) = Command<TypeOnlyCommandData>(DummyCommandData, signers.toList())

object DummyCommandData : TypeOnlyCommandData()

val DUMMY_IDENTITY_1: PartyAndCertificate get() = getTestPartyAndCertificate(DUMMY_PARTY)
val DUMMY_PARTY: Party get() = Party(getX500Name(O = "Dummy", L = "Madrid", C = "ES"), DUMMY_KEY_1.public)
