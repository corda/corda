@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
@file:JvmName("CoreTestUtils")

package net.corda.testing

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.node.VersionInfo
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.toFuture
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.*
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.nodeapi.User
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.testing.node.MockIdentityService
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.bouncycastle.asn1.x500.X500Name
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import kotlin.reflect.KClass

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

val MEGA_CORP: Party get() = Party(X509Utilities.getDevX509Name("MegaCorp"), MEGA_CORP_PUBKEY)
val MINI_CORP: Party get() = Party(X509Utilities.getDevX509Name("MiniCorp"), MINI_CORP_PUBKEY)

val BOC_KEY: KeyPair by lazy { generateKeyPair() }
val BOC_PUBKEY: PublicKey get() = BOC_KEY.public
val BOC: Party get() = Party(X500Name("CN=BankOfCorda,O=R3,OU=corda,L=New York,C=US"), BOC_PUBKEY)
val BOC_PARTY_REF = BOC.ref(OpaqueBytes.of(1)).reference

val BIG_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val BIG_CORP_PUBKEY: PublicKey get() = BIG_CORP_KEY.public
val BIG_CORP: Party get() = Party(X509Utilities.getDevX509Name("BigCorporation"), BIG_CORP_PUBKEY)
val BIG_CORP_PARTY_REF = BIG_CORP.ref(OpaqueBytes.of(1)).reference

val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val MOCK_IDENTITY_SERVICE: MockIdentityService get() = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

/**
 * Returns a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freeLocalHostAndPort(): HostAndPort {
    val freePort = ServerSocket(0).use { it.localPort }
    return HostAndPort.fromParts("localhost", freePort)
}

/**
 * Creates a specified number of ports for use by the Node.
 *
 * Unlikely, but in the time between running this function and handing the ports
 * to the Node, some other process else could allocate the returned ports.
 */
fun getFreeLocalPorts(hostName: String, numberToAlloc: Int): List<HostAndPort> {
    // Create a bunch of sockets up front.
    val sockets = Array(numberToAlloc) { ServerSocket(0) }
    val result = sockets.map { HostAndPort.fromParts(hostName, it.localPort) }
    // Close sockets only once we've grabbed all the ports we need.
    sockets.forEach(ServerSocket::close)
    return result
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

/**
 * The given flow factory will be used to initiate just one instance of a flow of type [P] when a counterparty
 * flow requests for it using [clientFlowClass].
 * @return Returns a [ListenableFuture] holding the single [FlowStateMachineImpl] created by the request.
 */
inline fun <reified P : FlowLogic<*>> AbstractNode.initiateSingleShotFlow(
        clientFlowClass: KClass<out FlowLogic<*>>,
        noinline serviceFlowFactory: (Party) -> P): ListenableFuture<P> {
    val future = smm.changes.filter { it is StateMachineManager.Change.Add && it.logic is P }.map { it.logic as P }.toFuture()
    services.registerServiceFlow(clientFlowClass.java, serviceFlowFactory)
    return future
}

// TODO Replace this with testConfiguration
data class TestNodeConfiguration(
        override val baseDirectory: Path,
        override val myLegalName: String,
        override val networkMapService: NetworkMapInfo?,
        override val minimumPlatformVersion: Int = 1,
        override val keyStorePassword: String = "cordacadevpass",
        override val trustStorePassword: String = "trustpass",
        override val rpcUsers: List<User> = emptyList(),
        override val dataSourceProperties: Properties = makeTestDataSourceProperties(myLegalName),
        override val nearestCity: String = "Null Island",
        override val emailAddress: String = "",
        override val exportJMXto: String = "",
        override val devMode: Boolean = true,
        override val certificateSigningService: URL = URL("http://localhost"),
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig> = emptyList(),
        override val verifierType: VerifierType = VerifierType.InMemory) : NodeConfiguration {
}

fun testConfiguration(baseDirectory: Path, legalName: String, basePort: Int): FullNodeConfiguration {
    return FullNodeConfiguration(
            basedir = baseDirectory,
            myLegalName = legalName,
            networkMapService = null,
            nearestCity = "Null Island",
            emailAddress = "",
            keyStorePassword = "cordacadevpass",
            trustStorePassword = "trustpass",
            dataSourceProperties = makeTestDataSourceProperties(legalName),
            certificateSigningService = URL("http://localhost"),
            rpcUsers = emptyList(),
            verifierType = VerifierType.InMemory,
            useHTTPS = false,
            p2pAddress = HostAndPort.fromParts("localhost", basePort),
            rpcAddress = HostAndPort.fromParts("localhost", basePort + 1),
            messagingServerAddress = null,
            extraAdvertisedServiceIds = emptyList(),
            notaryNodeId = null,
            notaryNodeAddress = null,
            notaryClusterAddresses = emptyList(),
            certificateChainCheckPolicies = emptyList(),
            devMode = true)
}

@JvmOverloads
fun configureTestSSL(legalName: String = "Mega Corp."): SSLConfiguration = object : SSLConfiguration {
    override val certificatesDirectory = Files.createTempDirectory("certs")
    override val keyStorePassword: String get() = "cordacadevpass"
    override val trustStorePassword: String get() = "trustpass"

    init {
        configureDevKeyAndTrustStores(legalName)
    }
}
