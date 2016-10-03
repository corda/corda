@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
@file:JvmName("CoreTestUtils")
package com.r3corda.testing

import com.google.common.base.Throwables
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.protocols.ProtocolStateMachine
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.services.statemachine.StateMachineManager.Change
import com.r3corda.node.utilities.AddOrRemove.ADD
import com.r3corda.testing.node.MockIdentityService
import com.r3corda.testing.node.MockServices
import rx.Subscriber
import java.net.ServerSocket
import java.security.KeyPair
import java.security.PublicKey
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

val ALICE_KEY: KeyPair by lazy { generateKeyPair() }
val ALICE_PUBKEY: PublicKey get() = ALICE_KEY.public
val ALICE: Party get() = Party("Alice", ALICE_PUBKEY)

val BOB_KEY: KeyPair by lazy { generateKeyPair() }
val BOB_PUBKEY: PublicKey get() = BOB_KEY.public
val BOB: Party get() = Party("Bob", BOB_PUBKEY)

val CHARLIE_KEY: KeyPair by lazy { generateKeyPair() }
val CHARLIE_PUBKEY: PublicKey get() = CHARLIE_KEY.public
val CHARLIE: Party get() = Party("Charlie", CHARLIE_PUBKEY)

val MEGA_CORP: Party get() = Party("MegaCorp", MEGA_CORP_PUBKEY)
val MINI_CORP: Party get() = Party("MiniCorp", MINI_CORP_PUBKEY)

val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

val MOCK_IDENTITY_SERVICE: MockIdentityService get() = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

/** If an exception is thrown by the body, rethrows the root cause exception. */
inline fun <R> rootCauseExceptions(body: () -> R): R {
    try {
        return body()
    } catch(e: Exception) {
        throw Throwables.getRootCause(e)
    }
}

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
 * The given protocol factory will be used to initiate just one instance of a protocol of type [P] when a counterparty
 * protocol requests for it using [markerClass].
 * @return Returns a [ListenableFuture] holding the single [ProtocolStateMachine] created by the request.
 */
inline fun <R, reified P : ProtocolLogic<R>> AbstractNode.initiateSingleShotProtocol(
        markerClass: KClass<out ProtocolLogic<*>>,
        noinline protocolFactory: (Party) -> P): ListenableFuture<ProtocolStateMachine<R>> {
    services.registerProtocolInitiator(markerClass, protocolFactory)

    val future = SettableFuture.create<ProtocolStateMachine<R>>()

    val subscriber = object : Subscriber<Change>() {
        override fun onNext(change: Change) {
            if (change.logic is P && change.addOrRemove == ADD) {
                unsubscribe()
                future.set(change.logic.psm as ProtocolStateMachine<R>)
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