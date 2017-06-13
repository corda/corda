@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
@file:JvmName("CoreTestUtils")

package net.corda.testing

import com.google.common.net.HostAndPort
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.node.VersionInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.*
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.nodeapi.User
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
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

val MEGA_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X509Utilities.getX509Name("MegaCorp","London","demo@r3.com",null), MEGA_CORP_PUBKEY)
val MEGA_CORP: Party get() = MEGA_CORP_IDENTITY.party
val MINI_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X509Utilities.getX509Name("MiniCorp","London","demo@r3.com",null), MINI_CORP_PUBKEY)
val MINI_CORP: Party get() = MINI_CORP_IDENTITY.party

val BOC_KEY: KeyPair by lazy { generateKeyPair() }
val BOC_PUBKEY: PublicKey get() = BOC_KEY.public
val BOC_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(getTestX509Name("BankOfCorda"), BOC_PUBKEY)
val BOC: Party get() = BOC_IDENTITY.party
val BOC_PARTY_REF = BOC.ref(OpaqueBytes.of(1)).reference

val BIG_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val BIG_CORP_PUBKEY: PublicKey get() = BIG_CORP_KEY.public
val BIG_CORP_IDENTITY: PartyAndCertificate get() = getTestPartyAndCertificate(X509Utilities.getX509Name("BigCorporation","London","demo@r3.com",null), BIG_CORP_PUBKEY)
val BIG_CORP: Party get() = BIG_CORP_IDENTITY.party
val BIG_CORP_PARTY_REF = BIG_CORP.ref(OpaqueBytes.of(1)).reference

val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val MOCK_IDENTITIES = listOf(MEGA_CORP_IDENTITY, MINI_CORP_IDENTITY, DUMMY_NOTARY_IDENTITY)
val MOCK_IDENTITY_SERVICE: IdentityService get() = InMemoryIdentityService(MOCK_IDENTITIES, emptyMap(), DUMMY_CA.certificate.cert)

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

private val freePortCounter = AtomicInteger(30000)
/**
 * Returns a localhost address with a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freeLocalHostAndPort(): HostAndPort = HostAndPort.fromParts("localhost", freePort())

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
fun getFreeLocalPorts(hostName: String, numberToAlloc: Int): List<HostAndPort> {
    val freePort =  freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + numberToAlloc) % 10000 }
    return (freePort .. freePort + numberToAlloc - 1).map { HostAndPort.fromParts(hostName, it) }
}

/**
 * Creates and tests a ledger built by the passed in dsl. The provided services can be customised, otherwise a default
 * of a freshly built [MockServices] is used.
 */
@JvmOverloads fun ledger(
        services: ServiceHub = MockServices(),
        dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
    val ledgerDsl = LedgerDSL(TestLedgerDSLInterpreter(services))
    dsl(ledgerDsl)
    return ledgerDsl
}

/**
 * Creates a ledger with a single transaction, built by the passed in dsl.
 *
 * @see LedgerDSLInterpreter._transaction
 */
@JvmOverloads fun transaction(
        transactionLabel: String? = null,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = DUMMY_NOTARY),
        dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
) = ledger { this.transaction(transactionLabel, transactionBuilder, dsl) }

// TODO Replace this with testConfiguration
data class TestNodeConfiguration(
        override val baseDirectory: Path,
        override val myLegalName: X500Name,
        override val networkMapService: NetworkMapInfo?,
        override val minimumPlatformVersion: Int = 1,
        override val keyStorePassword: String = "cordacadevpass",
        override val trustStorePassword: String = "trustpass",
        override val rpcUsers: List<User> = emptyList(),
        override val dataSourceProperties: Properties = makeTestDataSourceProperties(myLegalName.commonName),
        override val emailAddress: String = "",
        override val exportJMXto: String = "",
        override val devMode: Boolean = true,
        override val certificateSigningService: URL = URL("http://localhost"),
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig> = emptyList(),
        override val verifierType: VerifierType = VerifierType.InMemory,
        override val messageRedeliveryDelaySeconds: Int = 5) : NodeConfiguration {
}

fun testConfiguration(baseDirectory: Path, legalName: X500Name, basePort: Int): FullNodeConfiguration {
    return FullNodeConfiguration(
            basedir = baseDirectory,
            myLegalName = legalName,
            networkMapService = null,
            emailAddress = "",
            keyStorePassword = "cordacadevpass",
            trustStorePassword = "trustpass",
            dataSourceProperties = makeTestDataSourceProperties(legalName.commonName),
            certificateSigningService = URL("http://localhost"),
            rpcUsers = emptyList(),
            verifierType = VerifierType.InMemory,
            useHTTPS = false,
            p2pAddress = HostAndPort.fromParts("localhost", basePort),
            rpcAddress = HostAndPort.fromParts("localhost", basePort + 1),
            messagingServerAddress = null,
            extraAdvertisedServiceIds = emptyList(),
            bftReplicaId = null,
            notaryNodeAddress = null,
            notaryClusterAddresses = emptyList(),
            certificateChainCheckPolicies = emptyList(),
            devMode = true)
}

@JvmOverloads
fun configureTestSSL(legalName: X500Name = MEGA_CORP.name): SSLConfiguration = object : SSLConfiguration {
    override val certificatesDirectory = Files.createTempDirectory("certs")
    override val keyStorePassword: String get() = "cordacadevpass"
    override val trustStorePassword: String get() = "trustpass"

    init {
        configureDevKeyAndTrustStores(legalName)
    }
}


/**
 * Return a bogus X.509 for testing purposes.
 */
fun getTestX509Name(commonName: String): X500Name {
    require(!commonName.startsWith("CN="))
    // TODO: Consider if we want to make these more variable, i.e. different locations?
    val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
    nameBuilder.addRDN(BCStyle.CN, commonName)
    nameBuilder.addRDN(BCStyle.O, "R3")
    nameBuilder.addRDN(BCStyle.L, "New York")
    nameBuilder.addRDN(BCStyle.C, "US")
    return nameBuilder.build()
}
