@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.r3corda.core.testing

import com.google.common.base.Throwables
import com.google.common.net.HostAndPort
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.*
import com.r3corda.core.node.services.IdentityService
import com.r3corda.core.node.services.StorageService
import com.r3corda.core.node.services.testing.MockIdentityService
import com.r3corda.core.node.services.testing.MockStorageService
import java.net.ServerSocket
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant

/**
 *  JAVA INTEROP. Please keep the following points in mind when extending the Kotlin DSL
 *
 *   - Annotate functions with Kotlin defaults with @JvmOverloads. This produces the relevant overloads for Java.
 *   - Void closures in arguments are inconvenient in Java, use overloading to define non-closure variants as well.
 *   - Top-level funs should be defined in a [JavaTestHelpers] object and annotated with @JvmStatic first and should be
 *     referred to from the global fun. This allows static importing of [JavaTestHelpers] in Java tests, which mimicks
 *     top-level funs.
 *   - Top-level vals are trickier. *DO NOT USE @JvmField INSIDE [JavaTestHelpers]*. It's surprisingly easy to
 *     introduce a static init cycle because of the way Kotlin compiles top-level things, which can cause
 *     non-deterministic behaviour, including your field not being initialized at all! Instead opt for a proper Kotlin
 *     val either with a custom @JvmStatic get() or a lazy delegate if the initialiser has side-effects See examples below.
 *   - Infix functions work as regular ones from Java, but symbols with spaces in them don't! Define a camelCase variant
 *     as well.
 *   - varargs are exposed as array types in Java. Define overloads for common cases.
 *   - The Int.DOLLARS syntax doesn't work from Java. To remedy add a @JvmStatic DOLLARS(Int) function to
 *     [JavaTestHelpers]
 */
object JavaTestHelpers {
    // A dummy time at which we will be pretending test transactions are created.
    @JvmStatic val TEST_TX_TIME: Instant get() = Instant.parse("2015-04-17T12:00:00.00Z")

    // A few dummy values for testing.
    @JvmStatic val MEGA_CORP_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val MEGA_CORP_PUBKEY: PublicKey get() = MEGA_CORP_KEY.public

    @JvmStatic val MINI_CORP_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val MINI_CORP_PUBKEY: PublicKey get() = MINI_CORP_KEY.public

    @JvmStatic val ORACLE_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val ORACLE_PUBKEY: PublicKey get() = ORACLE_KEY.public

    @JvmStatic val DUMMY_PUBKEY_1: PublicKey get() = DummyPublicKey("x1")
    @JvmStatic val DUMMY_PUBKEY_2: PublicKey get() = DummyPublicKey("x2")

    @JvmStatic val DUMMY_KEY_1: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val DUMMY_KEY_2: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val DUMMY_KEY_3: KeyPair by lazy { generateKeyPair() }

    @JvmStatic val ALICE_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val ALICE_PUBKEY: PublicKey get() = ALICE_KEY.public
    @JvmStatic val ALICE: Party get() = Party("Alice", ALICE_PUBKEY)

    @JvmStatic val BOB_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val BOB_PUBKEY: PublicKey get() = BOB_KEY.public
    @JvmStatic val BOB: Party get() = Party("Bob", BOB_PUBKEY)

    @JvmStatic val MEGA_CORP: Party get() = Party("MegaCorp", MEGA_CORP_PUBKEY)
    @JvmStatic val MINI_CORP: Party get() = Party("MiniCorp", MINI_CORP_PUBKEY)

    @JvmStatic val DUMMY_NOTARY_KEY: KeyPair by lazy { generateKeyPair() }
    @JvmStatic val DUMMY_NOTARY: Party get() = Party("Notary Service", DUMMY_NOTARY_KEY.public)

    @JvmStatic val ALL_TEST_KEYS: List<KeyPair> get() = listOf(MEGA_CORP_KEY, MINI_CORP_KEY, ALICE_KEY, BOB_KEY, DUMMY_NOTARY_KEY)

    @JvmStatic val MOCK_IDENTITY_SERVICE: MockIdentityService get() = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))

    @JvmStatic fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

    /** If an exception is thrown by the body, rethrows the root cause exception. */
    @JvmStatic inline fun <R> rootCauseExceptions(body: () -> R): R {
        try {
            return body()
        } catch(e: Exception) {
            throw Throwables.getRootCause(e)
        }
    }

    @JvmStatic fun freeLocalHostAndPort(): HostAndPort {
        val freePort = ServerSocket(0).use { it.localPort }
        return HostAndPort.fromParts("localhost", freePort)
    }

    /**
     * Creates and tests a ledger built by the passed in dsl.
     * @param identityService: The [IdentityService] to be used while building the ledger.
     * @param storageService: The [StorageService] to be used for storing e.g. [Attachment]s.
     * @param dsl: The dsl building the ledger.
     */
    @JvmStatic @JvmOverloads fun ledger(
            identityService: IdentityService = MOCK_IDENTITY_SERVICE,
            storageService: StorageService = MockStorageService(),
            dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.() -> Unit
    ): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
        val ledgerDsl = LedgerDSL(TestLedgerDSLInterpreter(identityService, storageService))
        dsl(ledgerDsl)
        return ledgerDsl
    }

    /**
     * Creates a ledger with a single transaction, built by the passed in dsl.
     * @see LedgerDSLInterpreter._transaction
     */
    @JvmStatic @JvmOverloads fun transaction(
            transactionLabel: String? = null,
            transactionBuilder: TransactionBuilder = TransactionBuilder(),
            dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail
    ) = ledger { this.transaction(transactionLabel, transactionBuilder, dsl) }
}

val TEST_TX_TIME = JavaTestHelpers.TEST_TX_TIME
val MEGA_CORP_KEY = JavaTestHelpers.MEGA_CORP_KEY
val MEGA_CORP_PUBKEY = JavaTestHelpers.MEGA_CORP_PUBKEY
val MINI_CORP_KEY = JavaTestHelpers.MINI_CORP_KEY
val MINI_CORP_PUBKEY = JavaTestHelpers.MINI_CORP_PUBKEY
val ORACLE_KEY = JavaTestHelpers.ORACLE_KEY
val ORACLE_PUBKEY = JavaTestHelpers.ORACLE_PUBKEY
val DUMMY_PUBKEY_1 = JavaTestHelpers.DUMMY_PUBKEY_1
val DUMMY_PUBKEY_2 = JavaTestHelpers.DUMMY_PUBKEY_2
val DUMMY_KEY_1 = JavaTestHelpers.DUMMY_KEY_1
val DUMMY_KEY_2 = JavaTestHelpers.DUMMY_KEY_2
val DUMMY_KEY_3 = JavaTestHelpers.DUMMY_KEY_3
val ALICE_KEY = JavaTestHelpers.ALICE_KEY
val ALICE_PUBKEY = JavaTestHelpers.ALICE_PUBKEY
val ALICE = JavaTestHelpers.ALICE
val BOB_KEY = JavaTestHelpers.BOB_KEY
val BOB_PUBKEY = JavaTestHelpers.BOB_PUBKEY
val BOB = JavaTestHelpers.BOB
val MEGA_CORP = JavaTestHelpers.MEGA_CORP
val MINI_CORP = JavaTestHelpers.MINI_CORP
val DUMMY_NOTARY_KEY = JavaTestHelpers.DUMMY_NOTARY_KEY
val DUMMY_NOTARY = JavaTestHelpers.DUMMY_NOTARY
val ALL_TEST_KEYS = JavaTestHelpers.ALL_TEST_KEYS
val MOCK_IDENTITY_SERVICE = JavaTestHelpers.MOCK_IDENTITY_SERVICE

fun generateStateRef() = JavaTestHelpers.generateStateRef()
fun freeLocalHostAndPort() = JavaTestHelpers.freeLocalHostAndPort()
inline fun <R> rootCauseExceptions(body: () -> R) = JavaTestHelpers.rootCauseExceptions(body)
