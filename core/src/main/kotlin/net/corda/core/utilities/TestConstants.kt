@file:JvmName("TestConstants")

package net.corda.core.utilities

import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.CertStore
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.X509Certificate
import java.time.Instant

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

val DUMMY_PUBKEY_1: PublicKey get() = DummyPublicKey("x1")
val DUMMY_PUBKEY_2: PublicKey get() = DummyPublicKey("x2")

val DUMMY_KEY_1: KeyPair by lazy { generateKeyPair() }
val DUMMY_KEY_2: KeyPair by lazy { generateKeyPair() }

val DUMMY_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
/** Dummy notary identity for tests and simulations */
val DUMMY_NOTARY_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X500Name("CN=Notary Service,O=R3,OU=corda,L=Zurich,C=CH"), DUMMY_NOTARY_KEY.public)
val DUMMY_NOTARY: Party get() = DUMMY_NOTARY_IDENTITY.party

val DUMMY_MAP_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(30)) }
/** Dummy network map service identity for tests and simulations */
val DUMMY_MAP: Party get() = Party(X500Name("CN=Network Map Service,O=R3,OU=corda,L=Amsterdam,C=NL"), DUMMY_MAP_KEY.public)

val DUMMY_BANK_A_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(40)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_A: Party get() = Party(X500Name("CN=Bank A,O=Bank A,L=London,C=UK"), DUMMY_BANK_A_KEY.public)

val DUMMY_BANK_B_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(50)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_B: Party get() = Party(X500Name("CN=Bank B,O=Bank B,L=New York,C=US"), DUMMY_BANK_B_KEY.public)

val DUMMY_BANK_C_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(60)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_C: Party get() = Party(X500Name("CN=Bank C,O=Bank C,L=Tokyo,C=JP"), DUMMY_BANK_C_KEY.public)

val ALICE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(70)) }
/** Dummy individual identity for tests and simulations */
val ALICE_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X500Name("CN=Alice Corp,O=Alice Corp,L=Madrid,C=ES"), ALICE_KEY.public)
val ALICE: Party get() = ALICE_IDENTITY.party

val BOB_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(80)) }
/** Dummy individual identity for tests and simulations */
val BOB_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X500Name("CN=Bob Plc,O=Bob Plc,L=Rome,C=IT"), BOB_KEY.public)
val BOB: Party get() = BOB_IDENTITY.party

val CHARLIE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(90)) }
/** Dummy individual identity for tests and simulations */
val CHARLIE: Party get() = Party(X500Name("CN=Charlie Ltd,O=Charlie Ltd,L=Athens,C=GR"), CHARLIE_KEY.public)

val DUMMY_REGULATOR_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(100)) }
/** Dummy regulator for tests and simulations */
val DUMMY_REGULATOR: Party get() = Party(X500Name("CN=Regulator A,OU=Corda,O=AMF,L=Paris,C=FR"), DUMMY_REGULATOR_KEY.public)

val DUMMY_CA_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(110)) }
val DUMMY_CA: CertificateAndKeyPair by lazy {
    // TODO: Should be identity scheme
    val cert = X509Utilities.createSelfSignedCACertificate(X500Name("CN=Dummy CA,OU=Corda,O=R3 Ltd,L=London,C=UK"), DUMMY_CA_KEY)
    CertificateAndKeyPair(cert, DUMMY_CA_KEY)
}

/**
 * Build a test party with a nonsense certificate authority for testing purposes.
 */
fun getTestPartyAndCertificate(name: X500Name, publicKey: PublicKey, ca: CertificateAndKeyPair = DUMMY_CA): PartyAndCertificate {
    val certFactory = CertificateFactory.getInstance("X509")
    val certHolder = X509Utilities.createCertificate(CertificateType.IDENTITY, ca.certificate, ca.keyPair, name, publicKey)
    val certPath = certFactory.generateCertPath(listOf(certHolder.cert, ca.certificate.cert))
    return PartyAndCertificate(name, publicKey, certHolder, certPath)
}
