@file:Suppress("UNUSED_PARAMETER")
@file:JvmName("CoreTestUtils")

package net.corda.testing

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.internal.serialization.AMQP_ENABLED
import java.nio.file.Files
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 *  JAVA INTEROP
 *  ------------
 *
 *  Please keep the following points in mind when extending the Kotlin DSL:
 *
 *   - Annotate functions with Kotlin defaults with @JvmOverloads. This produces the relevant overloads for Java.
 *   - Void closures in arguments are inconvenient in Java, use overloading to define non-closure variants as well.
 *   - Top-level vals are trickier. *DO NOT USE @JvmField at the top level!* It's surprisingly easy to
 *     introduce a static init cycle because of the way Kotlin compiles top-level things, which can cause
 *     non-deterministic behaviour, including your field not being initialized at all! Instead opt for a proper Kotlin
 *     val either with a custom @JvmStatic get() or a lazy delegate if the initialiser has side-effects. See examples below.
 *   - Infix functions work as regular ones from Java, but symbols with spaces in them don't! Define a camelCase variant
 *     as well.
 *   - varargs are exposed as array types in Java. Define overloads for common cases.
 *   - The Int.DOLLARS syntax doesn't work from Java.  Use the DOLLARS(int) function instead.
 */

// TODO: Refactor these dummies to work with the new identities framework.

// A few dummy values for testing.
val MEGA_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val MEGA_CORP_PUBKEY: PublicKey get() = MEGA_CORP_KEY.public

val MINI_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val MINI_CORP_PUBKEY: PublicKey get() = MINI_CORP_KEY.public

val ORACLE_KEY: KeyPair by lazy { generateKeyPair() }
val ORACLE_PUBKEY: PublicKey get() = ORACLE_KEY.public

val ALICE_PUBKEY: PublicKey get() = ALICE_KEY.public
val BOB_PUBKEY: PublicKey get() = BOB_KEY.public
val CHARLIE_PUBKEY: PublicKey get() = CHARLIE_KEY.public

val MEGA_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CordaX500Name(organisation = "MegaCorp", locality = "London", country = "GB"), MEGA_CORP_PUBKEY)
val MEGA_CORP: Party get() = MEGA_CORP_IDENTITY.party
val MINI_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CordaX500Name(organisation = "MiniCorp", locality = "London", country = "GB"), MINI_CORP_PUBKEY)
val MINI_CORP: Party get() = MINI_CORP_IDENTITY.party

val BOC_KEY: KeyPair by lazy { generateKeyPair() }
val BOC_PUBKEY: PublicKey get() = BOC_KEY.public
val BOC_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CordaX500Name(organisation = "BankOfCorda", locality = "London", country = "GB"), BOC_PUBKEY)
val BOC: Party get() = BOC_IDENTITY.party
val BOC_PARTY_REF = BOC.ref(OpaqueBytes.of(1)).reference

val BIG_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val BIG_CORP_PUBKEY: PublicKey get() = BIG_CORP_KEY.public
val BIG_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(CordaX500Name(organisation = "BigCorporation", locality = "New York", country = "US"), BIG_CORP_PUBKEY)
val BIG_CORP: Party get() = BIG_CORP_IDENTITY.party
val BIG_CORP_PARTY_REF = BIG_CORP.ref(OpaqueBytes.of(1)).reference

val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val DUMMY_CASH_ISSUER_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(DUMMY_CASH_ISSUER.party as Party)

val MOCK_IDENTITIES = listOf(MEGA_CORP_IDENTITY, MINI_CORP_IDENTITY, DUMMY_CASH_ISSUER_IDENTITY, DUMMY_NOTARY_IDENTITY)
val MOCK_IDENTITY_SERVICE: IdentityService get() = InMemoryIdentityService(MOCK_IDENTITIES, emptySet(), DEV_CA.certificate.cert)

val MOCK_HOST_AND_PORT = NetworkHostAndPort("mockHost", 30000)

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

private val freePortCounter = AtomicInteger(30000)
/**
 * Returns a localhost address with a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freeLocalHostAndPort() = NetworkHostAndPort("localhost", freePort())

/**
 * Returns a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freePort(): Int = freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + 1) % 10000 }

/**
 * Creates a specified number of ports for use by the Node.
 *
 * Unlikely, but in the time between running this function and handing the ports
 * to the Node, some other process else could allocate the returned ports.
 */
fun getFreeLocalPorts(hostName: String, numberToAlloc: Int): List<NetworkHostAndPort> {
    val freePort =  freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + numberToAlloc) % 10000 }
    return (freePort .. freePort + numberToAlloc - 1).map { NetworkHostAndPort(hostName, it) }
}

@JvmOverloads
fun configureTestSSL(legalName: CordaX500Name = MEGA_CORP.name): SSLConfiguration = object : SSLConfiguration {
    override val certificatesDirectory = Files.createTempDirectory("certs")
    override val keyStorePassword: String get() = "cordacadevpass"
    override val trustStorePassword: String get() = "trustpass"

    init {
        configureDevKeyAndTrustStores(legalName)
    }
}

fun getTestPartyAndCertificate(party: Party, trustRoot: CertificateAndKeyPair = DEV_CA): PartyAndCertificate {
    val certFactory = CertificateFactory.getInstance("X509")
    val certHolder = X509Utilities.createCertificate(CertificateType.IDENTITY, trustRoot.certificate, trustRoot.keyPair, party.name, party.owningKey)
    val certPath = certFactory.generateCertPath(listOf(certHolder.cert, trustRoot.certificate.cert))
    return PartyAndCertificate(certPath)
}

/**
 * Build a test party with a nonsense certificate authority for testing purposes.
 */
fun getTestPartyAndCertificate(name: CordaX500Name, publicKey: PublicKey, trustRoot: CertificateAndKeyPair = DEV_CA): PartyAndCertificate {
    return getTestPartyAndCertificate(Party(name, publicKey), trustRoot)
}

@Suppress("unused")
inline fun <reified T : Any> T.kryoSpecific(reason: String, function: () -> Unit) = if(!AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring Kryo specific test, reason: $reason" )
}

@Suppress("unused")
inline fun <reified T : Any> T.amqpSpecific(reason: String, function: () -> Unit) = if(AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring AMQP specific test, reason: $reason" )
}

/**
 * Until we have proper handling of multiple identities per node, for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.first()
fun NodeInfo.chooseIdentity(): Party = chooseIdentityAndCert().party
/** Returns the identity of the first notary found on the network */
fun ServiceHub.getDefaultNotary(): Party = networkMapCache.notaryIdentities.first()

/**
 * Set the package to scan for cordapps - this overrides the default behaviour of scanning the cordapps directory
 * @param packageNames A package name that you wish to scan for cordapps
 */
fun setCordappPackages(vararg packageNames: String) {
    CordappLoader.testPackages = packageNames.toList()
}

/**
 * Unsets the default overriding behaviour of [setCordappPackages]
 */
fun unsetCordappPackages() {
    CordappLoader.testPackages = emptyList()
}