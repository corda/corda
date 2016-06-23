package com.r3corda.core.node.services

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Postfix for base topics when sending a request to a service.
 */
val TOPIC_DEFAULT_POSTFIX = ".0"

/**
 * This file defines various 'services' which are not currently fleshed out. A service is a module that provides
 * immutable snapshots of data that may be changing in response to user or network events.
 */

/**
 * A wallet (name may be temporary) wraps a set of states that are useful for us to keep track of, for instance,
 * because we own them. This class represents an immutable, stable state of a wallet: it is guaranteed not to
 * change out from underneath you, even though the canonical currently-best-known wallet may change as we learn
 * about new transactions from our peers and generate new transactions that consume states ourselves.
 *
 * This abstract class has no references to Cash contracts.
 *
 * [states] Holds the list of states that are *active* and *relevant*.
 *   Active means they haven't been consumed yet (or we don't know about it).
 *   Relevant means they contain at least one of our pubkeys
 */
class Wallet(val states: List<StateAndRef<ContractState>>) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : OwnableState> statesOfType() = states.filter { it.state.data is T } as List<StateAndRef<T>>

    /**
     * Represents an update observed by the Wallet that will be notified to observers.  Include the [StateRef]s of
     * transaction outputs that were consumed (inputs) and the [ContractState]s produced (outputs) to/by the transaction
     * or transactions observed and the Wallet.
     *
     * If the Wallet observes multiple transactions simultaneously, where some transactions consume the outputs of some of the
     * other transactions observed, then the changes are observed "net" of those.
     */
    data class Update(val consumed: Set<StateRef>, val produced: Set<StateAndRef<ContractState>>) {

        /**
         * Combine two updates into a single update with the combined inputs and outputs of the two updates but net
         * any outputs of the left-hand-side (this) that are consumed by the inputs of the right-hand-side (rhs).
         *
         * i.e. the net effect in terms of state live-ness of receiving the combined update is the same as receiving this followed by rhs.
         */
        operator fun plus(rhs: Update): Update {
            val previouslyProduced = produced.map { it.ref }
            val previouslyConsumed = consumed
            val combined = Wallet.Update(
                    previouslyConsumed + (rhs.consumed - previouslyProduced),
                    rhs.produced + produced.filter { it.ref !in rhs.consumed })
            return combined
        }
    }

    companion object {
        val NoUpdate = Update(emptySet(), emptySet())
    }
}

/**
 * A [WalletService] is responsible for securely and safely persisting the current state of a wallet to storage. The
 * wallet service vends immutable snapshots of the current wallet for working with: if you build a transaction based
 * on a wallet that isn't current, be aware that it may end up being invalid if the states that were used have been
 * consumed by someone else first!
 */
interface WalletService {
    /**
     * Returns a read-only snapshot of the wallet at the time the call is made. Note that if you consume states or
     * keys in this wallet, you must inform the wallet service so it can update its internal state.
     */
    val currentWallet: Wallet

    /**
     * Returns a snapshot of the heads of LinearStates
     */
    val linearHeads: Map<SecureHash, StateAndRef<LinearState>>

    // TODO: When KT-10399 is fixed, rename this and remove the inline version below.

    /** Returns the [linearHeads] only when the type of the state would be considered an 'instanceof' the given type. */
    @Suppress("UNCHECKED_CAST")
    fun <T : LinearState> linearHeadsOfType_(stateType: Class<T>): Map<SecureHash, StateAndRef<T>> {
        return linearHeads.filterValues { stateType.isInstance(it.state.data) }.mapValues { StateAndRef(it.value.state as TransactionState<T>, it.value.ref) }
    }

    fun statesForRefs(refs: List<StateRef>): Map<StateRef, TransactionState<*>?> {
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

    /**
     * Get a synchronous Observable of updates.  When observations are pushed to the Observer, the Wallet will already incorporate
     * the update.
     */
    val updates: rx.Observable<Wallet.Update>
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
    /** Returns a snapshot of the current pubkey->privkey mapping. */
    val keys: Map<PublicKey, PrivateKey>

    fun toPrivate(publicKey: PublicKey) = keys[publicKey] ?: throw IllegalStateException("No private key known for requested public key")

    fun toKeyPair(publicKey: PublicKey) = KeyPair(publicKey, toPrivate(publicKey))

    /** Generates a new random key and adds it to the exposed map. */
    fun freshKey(): KeyPair
}

/**
 * A sketch of an interface to a simple key/value storage system. Intended for persistence of simple blobs like
 * transactions, serialised protocol state machines and so on. Again, this isn't intended to imply lack of SQL or
 * anything like that, this interface is only big enough to support the prototyping work.
 */
interface StorageService {
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    val validatedTransactions: ReadOnlyTransactionStorage

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
 * Storage service, with extensions to allow validated transactions to be added to. For use only within [ServiceHub].
 */
interface TxWritableStorageService : StorageService {
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    override val validatedTransactions: TransactionStorage
}


