package core.node.services

import com.codahale.metrics.MetricRegistry
import contracts.Cash
import core.*
import core.crypto.SecureHash
import core.messaging.MessagingService
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.util.*

/**
 * This file defines various 'services' which are not currently fleshed out. A service is a module that provides
 * immutable snapshots of data that may be changing in response to user or network events.
 */

/**
 * A wallet (name may be temporary) wraps a set of states that are useful for us to keep track of, for instance,
 * because we own them. This class represents an immutable, stable state of a wallet: it is guaranteed not to
 * change out from underneath you, even though the canonical currently-best-known wallet may change as we learn
 * about new transactions from our peers and generate new transactions that consume states ourselves.
 */
data class Wallet(val states: List<StateAndRef<ContractState>>) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : OwnableState> statesOfType() = states.filter { it.state is T } as List<StateAndRef<T>>

    /**
     * Returns a map of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null (not present in map), not 0.
     */
    val cashBalances: Map<Currency, Amount> get() = states.
            // Select the states we own which are cash, ignore the rest, take the amounts.
            mapNotNull { (it.state as? Cash.State)?.amount }.
            // Turn into a Map<Currency, List<Amount>> like { GBP -> (£100, £500, etc), USD -> ($2000, $50) }
            groupBy { it.currency }.
            // Collapse to Map<Currency, Amount> by summing all the amounts of the same currency together.
            mapValues { it.value.sumOrThrow() }
}

/**
 * A [WalletService] is responsible for securely and safely persisting the current state of a wallet to storage. The
 * wallet service vends immutable snapshots of the current wallet for working with: if you build a transaction based
 * on a wallet that isn't current, be aware that it may end up being invalid if the states that were used have been
 * consumed by someone else first!
 */
interface WalletService {
    object Type : ServiceType("corda.wallet")
    /**
     * Returns a read-only snapshot of the wallet at the time the call is made. Note that if you consume states or
     * keys in this wallet, you must inform the wallet service so it can update its internal state.
     */
    val currentWallet: Wallet

    /**
     * Returns a snapshot of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null, not 0.
     */
    val cashBalances: Map<Currency, Amount>

    /**
     * Returns a snapshot of the heads of LinearStates
     */
    val linearHeads: Map<SecureHash, StateAndRef<LinearState>>

    // TODO: When KT-10399 is fixed, rename this and remove the inline version below.

    /** Returns the [linearHeads] only when the type of the state would be considered an 'instanceof' the given type. */
    @Suppress("UNCHECKED_CAST")
    fun <T : LinearState> linearHeadsOfType_(stateType: Class<T>): Map<SecureHash, StateAndRef<T>> {
        return linearHeads.filterValues { stateType.isInstance(it.state) }.mapValues { StateAndRef(it.value.state as T, it.value.ref) }
    }

    fun statesForRefs(refs: List<StateRef>): Map<StateRef, ContractState?> {
        val refsToStates = currentWallet.states.associateBy { it.ref }
        return refs.associateBy({ it }, { refsToStates[it]?.state })
    }

    /**
     * Possibly update the wallet by marking as spent states that these transactions consume, and adding any relevant
     * new states that they create. You should only insert transactions that have been successfully verified here!
     *
     * Returns the new wallet that resulted from applying the transactions (note: it may quickly become out of date).
     *
     * TODO: Consider if there's a good way to enforce the must-be-verified requirement in the type system.
     */
    fun notifyAll(txns: Iterable<WireTransaction>): Wallet

    /** Same as notifyAll but with a single transaction. */
    fun notify(tx: WireTransaction): Wallet = notifyAll(listOf(tx))
}

inline fun <reified T : LinearState> WalletService.linearHeadsOfType() = linearHeadsOfType_(T::class.java)

/**
 * The KMS is responsible for storing and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 *
 * The current interface is obviously not usable for those use cases: this is just where we'd put a real signing
 * interface if/when one is developed.
 */
interface KeyManagementService {
    object Type : ServiceType("corda.key_management")
    /** Returns a snapshot of the current pubkey->privkey mapping. */
    val keys: Map<PublicKey, PrivateKey>

    fun toPrivate(publicKey: PublicKey) = keys[publicKey] ?: throw IllegalStateException("No private key known for requested public key")

    /** Generates a new random key and adds it to the exposed map. */
    fun freshKey(): KeyPair
}

/**
 * A sketch of an interface to a simple key/value storage system. Intended for persistence of simple blobs like
 * transactions, serialised protocol state machines and so on. Again, this isn't intended to imply lack of SQL or
 * anything like that, this interface is only big enough to support the prototyping work.
 */
interface StorageService {
    object Type : ServiceType("corda.storage")
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    val validatedTransactions: MutableMap<SecureHash, SignedTransaction>

    val stateMachines: MutableMap<SecureHash, ByteArray>

    /** Provides access to storage of arbitrary JAR files (which may contain only data, no code). */
    val attachments: AttachmentStorage

    /**
     * Returns the legal identity that this node is configured with. Assumed to be initialised when the node is
     * first installed.
     */
    val myLegalIdentity: Party
    val myLegalIdentityKey: KeyPair
}

/**
 * Provides access to various metrics and ways to notify monitoring services of things, for sysadmin purposes.
 * This is not an interface because it is too lightweight to bother mocking out.
 */
class MonitoringService(val metrics: MetricRegistry) {
    object Type : ServiceType("corda.monitoring")
}

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 */
interface ServiceHub {
    val walletService: WalletService
    val keyManagementService: KeyManagementService
    val identityService: IdentityService
    val storageService: StorageService
    val networkService: MessagingService
    val networkMapCache: NetworkMapCache
    val monitoringService: MonitoringService
    val clock: Clock

    /**
     * Given a [LedgerTransaction], looks up all its dependencies in the local database, uses the identity service to map
     * the [SignedTransaction]s the DB gives back into [LedgerTransaction]s, and then runs the smart contracts for the
     * transaction. If no exception is thrown, the transaction is valid.
     */
    fun verifyTransaction(ltx: LedgerTransaction) {
        val dependencies = ltx.inputs.map {
            storageService.validatedTransactions[it.txhash] ?: throw TransactionResolutionException(it.txhash)
        }
        val ltxns = dependencies.map { it.verifyToLedgerTransaction(identityService, storageService.attachments) }
        TransactionGroup(setOf(ltx), ltxns.toSet()).verify()
    }

    /**
     * Use this for storing transactions to StorageService and WalletService
     *
     * TODO Need to come up with a way for preventing transactions being written other than by this method
     */
    fun recordTransactions(txs: List<SignedTransaction>) {
        storageService.validatedTransactions.putAll(txs.groupBy { it.id }.mapValues { it.value.first() })
        walletService.notifyAll(txs.map { it.tx })
    }
}
