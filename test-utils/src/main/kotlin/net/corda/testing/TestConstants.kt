@file:JvmName("TestConstants")

package net.corda.testing

import net.corda.core.contracts.Command
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.CertificateAndKeyPair
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.testing.DummyPublicKey
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.ServiceInfo
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.User
import net.corda.testing.driver.DriverDSLExposedInterface
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant

// A dummy time at which we will be pretending test transactions are created.
val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

val DUMMY_PUBKEY_1: PublicKey get() = DummyPublicKey("x1")
val DUMMY_PUBKEY_2: PublicKey get() = DummyPublicKey("x2")

val DUMMY_KEY_1: KeyPair by lazy { generateKeyPair() }
val DUMMY_KEY_2: KeyPair by lazy { generateKeyPair() }

val DUMMY_NOTARY_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(20)) }
/** Dummy notary identity for tests and simulations */
val DUMMY_NOTARY_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(DUMMY_NOTARY)
val DUMMY_NOTARY: Party get() = Party(X500Name("CN=Notary Service,O=R3,OU=corda,L=Zurich,C=CH"), DUMMY_NOTARY_KEY.public)

val DUMMY_MAP_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(30)) }
/** Dummy network map service identity for tests and simulations */
val DUMMY_MAP: Party get() = Party(X500Name("CN=Network Map Service,O=R3,OU=corda,L=Amsterdam,C=NL"), DUMMY_MAP_KEY.public)

val DUMMY_BANK_A_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(40)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_A: Party get() = Party(X500Name("CN=Bank A,O=Bank A,L=London,C=GB"), DUMMY_BANK_A_KEY.public)

val DUMMY_BANK_B_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(50)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_B: Party get() = Party(X500Name("CN=Bank B,O=Bank B,L=New York,C=US"), DUMMY_BANK_B_KEY.public)

val DUMMY_BANK_C_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(60)) }
/** Dummy bank identity for tests and simulations */
val DUMMY_BANK_C: Party get() = Party(X500Name("CN=Bank C,O=Bank C,L=Tokyo,C=JP"), DUMMY_BANK_C_KEY.public)

val ALICE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(70)) }
/** Dummy individual identity for tests and simulations */
val ALICE_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(ALICE)
val ALICE: Party get() = Party(X500Name("CN=Alice Corp,O=Alice Corp,L=Madrid,C=ES"), ALICE_KEY.public)

val BOB_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(80)) }
/** Dummy individual identity for tests and simulations */
val BOB_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(BOB)
val BOB: Party get() = Party(X500Name("CN=Bob Plc,O=Bob Plc,L=Rome,C=IT"), BOB_KEY.public)

val CHARLIE_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(90)) }
/** Dummy individual identity for tests and simulations */
val CHARLIE: Party get() = Party(X500Name("CN=Charlie Ltd,O=Charlie Ltd,L=Athens,C=GR"), CHARLIE_KEY.public)

val DUMMY_REGULATOR_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(100)) }
/** Dummy regulator for tests and simulations */
val DUMMY_REGULATOR: Party get() = Party(X500Name("CN=Regulator A,OU=Corda,O=AMF,L=Paris,C=FR"), DUMMY_REGULATOR_KEY.public)

val DUMMY_CA_KEY: KeyPair by lazy { entropyToKeyPair(BigInteger.valueOf(110)) }
val DUMMY_CA: CertificateAndKeyPair by lazy {
    // TODO: Should be identity scheme
    val cert = X509Utilities.createSelfSignedCACertificate(X500Name("CN=Dummy CA,OU=Corda,O=R3 Ltd,L=London,C=GB"), DUMMY_CA_KEY)
    CertificateAndKeyPair(cert, DUMMY_CA_KEY)
}

fun dummyCommand(vararg signers: PublicKey) = Command<TypeOnlyCommandData>(object : TypeOnlyCommandData() {}, signers.toList())

val DUMMY_IDENTITY_1: PartyAndCertificate get() = getTestPartyAndCertificate(DUMMY_PARTY)
val DUMMY_PARTY: Party get() = Party(X500Name("CN=Dummy,O=Dummy,L=Madrid,C=ES"), DUMMY_KEY_1.public)

//
// Extensions to the Driver DSL to auto-manufacture nodes by name.
//

/**
 * A simple wrapper for objects provided by the integration test driver DSL. The fields are lazy so
 * node construction won't start until you access the members. You can get one of these from the
 * [alice], [bob] and [aliceBobAndNotary] functions.
 */
class PredefinedTestNode internal constructor(party: Party, driver: DriverDSLExposedInterface, services: Set<ServiceInfo>) {
    val rpcUsers = listOf(User("admin", "admin", setOf("ALL")))  // TODO: Randomize?
    val nodeFuture by lazy { driver.startNode(party.name, rpcUsers = rpcUsers, advertisedServices = services) }
    val node by lazy { nodeFuture.get()!! }
    val rpc by lazy { node.rpcClientToNode() }

    fun <R> useRPC(block: (CordaRPCOps) -> R) = rpc.use(rpcUsers[0].username, rpcUsers[0].password) { block(it.proxy) }
}

// TODO: Probably we should inject the above keys through the driver to make the nodes use it, rather than have the warnings below.

/**
 * Returns a plain, entirely stock node pre-configured with the [ALICE] identity. Note that a random key will be generated
 * for it: you won't have [ALICE_KEY].
 */
fun DriverDSLExposedInterface.alice(): PredefinedTestNode = PredefinedTestNode(ALICE, this, emptySet())
/**
 * Returns a plain, entirely stock node pre-configured with the [BOB] identity. Note that a random key will be generated
 * for it: you won't have [BOB_KEY].
 */
fun DriverDSLExposedInterface.bob(): PredefinedTestNode = PredefinedTestNode(BOB, this, emptySet())
/**
 * Returns a plain single node notary pre-configured with the [DUMMY_NOTARY] identity. Note that a random key will be generated
 * for it: you won't have [DUMMY_NOTARY_KEY].
 */
fun DriverDSLExposedInterface.notary(): PredefinedTestNode = PredefinedTestNode(DUMMY_NOTARY, this, setOf(ServiceInfo(ValidatingNotaryService.type)))

/**
 * Returns plain, entirely stock nodes pre-configured with the [ALICE], [BOB] and [DUMMY_NOTARY] X.500 names in that
 * order. They have been started up in parallel and are now ready to use.
 */
fun DriverDSLExposedInterface.aliceBobAndNotary(): List<PredefinedTestNode> {
    val alice = alice()
    val bob = bob()
    val notary = notary()
    listOf(alice.nodeFuture, bob.nodeFuture, notary.nodeFuture).transpose().get()
    return listOf(alice, bob, notary)
}