@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
@file:JvmName("CoreTestUtils")
package net.corda.testing

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.typesafe.config.Config
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.node.internal.AbstractNode
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager.Change
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.testing.node.MockIdentityService
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import rx.Subscriber
import java.net.ServerSocket
import java.nio.file.Path
import java.security.KeyPair
import java.time.Duration
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
val MEGA_CORP_PUBKEY: CompositeKey get() = MEGA_CORP_KEY.public.composite

val MINI_CORP_KEY: KeyPair by lazy { generateKeyPair() }
val MINI_CORP_PUBKEY: CompositeKey get() = MINI_CORP_KEY.public.composite

val ORACLE_KEY: KeyPair by lazy { generateKeyPair() }
val ORACLE_PUBKEY: CompositeKey get() = ORACLE_KEY.public.composite

val ALICE_KEY: KeyPair by lazy { generateKeyPair() }
val ALICE_PUBKEY: CompositeKey get() = ALICE_KEY.public.composite
val ALICE: Party get() = Party("Alice", ALICE_PUBKEY)

val BOB_KEY: KeyPair by lazy { generateKeyPair() }
val BOB_PUBKEY: CompositeKey get() = BOB_KEY.public.composite
val BOB: Party get() = Party("Bob", BOB_PUBKEY)

val CHARLIE_KEY: KeyPair by lazy { generateKeyPair() }
val CHARLIE_PUBKEY: CompositeKey get() = CHARLIE_KEY.public.composite
val CHARLIE: Party get() = Party("Charlie", CHARLIE_PUBKEY)

val MEGA_CORP: Party get() = Party("MegaCorp", MEGA_CORP_PUBKEY)
val MINI_CORP: Party get() = Party("MiniCorp", MINI_CORP_PUBKEY)

val BOC_KEY: KeyPair by lazy { generateKeyPair() }
val BOC_PUBKEY: CompositeKey get() = BOC_KEY.public.composite
val BOC: Party get() = Party("BankOfCorda", BOC_PUBKEY)
val BOC_PARTY_REF = BOC.ref(OpaqueBytes.of(1)).reference

val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val MOCK_IDENTITY_SERVICE: MockIdentityService get() = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

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
 * flow requests for it using [markerClass].
 * @return Returns a [ListenableFuture] holding the single [FlowStateMachineImpl] created by the request.
 */
inline fun <reified P : FlowLogic<*>> AbstractNode.initiateSingleShotFlow(
        markerClass: KClass<out FlowLogic<*>>,
        noinline flowFactory: (Party) -> P): ListenableFuture<P> {
    services.registerFlowInitiator(markerClass, flowFactory)

    val future = SettableFuture.create<P>()

    val subscriber = object : Subscriber<Change>() {
        override fun onNext(change: Change) {
            if (change.addOrRemove == ADD) {
                unsubscribe()
                future.set(change.logic as P)
            }
        }
        override fun onError(e: Throwable) {
            future.setException(e)
        }
        override fun onCompleted() {}
    }

    smm.changes.subscribe(subscriber)

    return future
}

fun Config.getHostAndPort(name: String) = HostAndPort.fromString(getString(name))

inline fun elapsedTime(block: () -> Unit): Duration {
    val start = System.nanoTime()
    block()
    val end = System.nanoTime()
    return Duration.ofNanos(end-start)
}

data class TestNodeConfiguration(
    override val basedir: Path,
    override val myLegalName: String,
    override val networkMapService: NetworkMapInfo?,
    override val keyStorePassword: String = "cordacadevpass",
    override val trustStorePassword: String = "trustpass",
    override val dataSourceProperties: Properties = makeTestDataSourceProperties(myLegalName),
    override val nearestCity: String = "Null Island",
    override val emailAddress: String = "",
    override val exportJMXto: String = "",
    override val devMode: Boolean = true) : NodeConfiguration
