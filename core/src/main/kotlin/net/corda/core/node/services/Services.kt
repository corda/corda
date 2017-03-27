package net.corda.core.node.services

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowException
import net.corda.core.serialization.CordaSerializable
import net.corda.core.toFuture
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import rx.Observable
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * Session ID to use for services listening for the first message in a session (before a
 * specific session ID has been established).
 */
val DEFAULT_SESSION_ID = 0L

/**
 * This file defines various 'services' which are not currently fleshed out. A service is a module that provides
 * immutable snapshots of data that may be changing in response to user or network events.
 */

/**
 * A vault (name may be temporary) wraps a set of states that are useful for us to keep track of, for instance,
 * because we own them. This class represents an immutable, stable state of a vault: it is guaranteed not to
 * change out from underneath you, even though the canonical currently-best-known vault may change as we learn
 * about new transactions from our peers and generate new transactions that consume states ourselves.
 *
 * This abstract class has no references to Cash contracts.
 *
 * [states] Holds a [VaultService] queried subset of states that are *active* and *relevant*.
 *   Active means they haven't been consumed yet (or we don't know about it).
 *   Relevant means they contain at least one of our pubkeys.
 */
@CordaSerializable
class Vault<out T : ContractState>(val states: Iterable<StateAndRef<T>>) {

    /**
     * Represents an update observed by the vault that will be notified to observers.  Include the [StateRef]s of
     * transaction outputs that were consumed (inputs) and the [ContractState]s produced (outputs) to/by the transaction
     * or transactions observed and the vault.
     *
     * If the vault observes multiple transactions simultaneously, where some transactions consume the outputs of some of the
     * other transactions observed, then the changes are observed "net" of those.
     */
    @CordaSerializable
    data class Update(val consumed: Set<StateAndRef<ContractState>>, val produced: Set<StateAndRef<ContractState>>, val flowId: UUID? = null) {
        /** Checks whether the update contains a state of the specified type. */
        inline fun <reified T : ContractState> containsType() = consumed.any { it.state.data is T } || produced.any { it.state.data is T }

        /**
         * Combine two updates into a single update with the combined inputs and outputs of the two updates but net
         * any outputs of the left-hand-side (this) that are consumed by the inputs of the right-hand-side (rhs).
         *
         * i.e. the net effect in terms of state live-ness of receiving the combined update is the same as receiving this followed by rhs.
         */
        operator fun plus(rhs: Update): Update {
            val combined = Vault.Update(
                    consumed + (rhs.consumed - produced),
                    // The ordering below matters to preserve ordering of consumed/produced Sets when they are insertion order dependent implementations.
                    produced.filter { it !in rhs.consumed }.toSet() + rhs.produced)
            return combined
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.appendln("${consumed.size} consumed, ${produced.size} produced")
            sb.appendln("")
            sb.appendln("Produced:")
            produced.forEach {
                sb.appendln("${it.ref}: ${it.state}")
            }
            return sb.toString()
        }
    }

    companion object {
        val NoUpdate = Update(emptySet(), emptySet())
    }

    enum class StateStatus {
        UNCONSUMED, CONSUMED
    }
}

/**
 * A [VaultService] is responsible for securely and safely persisting the current state of a vault to storage. The
 * vault service vends immutable snapshots of the current vault for working with: if you build a transaction based
 * on a vault that isn't current, be aware that it may end up being invalid if the states that were used have been
 * consumed by someone else first!
 *
 * Note that transactions we've seen are held by the storage service, not the vault.
 */
interface VaultService {

    /**
     * Prefer the use of [updates] unless you know why you want to use this instead.
     *
     * Get a synchronous Observable of updates.  When observations are pushed to the Observer, the Vault will already incorporate
     * the update, and the database transaction associated with the update will still be open and current.  If for some
     * reason the processing crosses outside of the database transaction (for example, the update is pushed outside the current
     * JVM or across to another [Thread] which is executing in a different database transaction) then the Vault may
     * not incorporate the update due to racing with committing the current database transaction.
     */
    val rawUpdates: Observable<Vault.Update>

    /**
     * Get a synchronous Observable of updates.  When observations are pushed to the Observer, the Vault will already incorporate
     * the update, and the database transaction associated with the update will have been committed and closed.
     */
    val updates: Observable<Vault.Update>

    /**
     * Returns a map of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null (not present in map), not 0.
     */
    val cashBalances: Map<Currency, Amount<Currency>>

    /**
     * Atomically get the current vault and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    fun track(): Pair<Vault<ContractState>, Observable<Vault.Update>>

    /**
     * Return unconsumed [ContractState]s for a given set of [StateRef]s
     * TODO: revisit and generalize this exposed API function.
     */
    fun statesForRefs(refs: List<StateRef>): Map<StateRef, TransactionState<*>?>

    /**
     * Possibly update the vault by marking as spent states that these transactions consume, and adding any relevant
     * new states that they create. You should only insert transactions that have been successfully verified here!
     *
     * TODO: Consider if there's a good way to enforce the must-be-verified requirement in the type system.
     */
    fun notifyAll(txns: Iterable<WireTransaction>)

    /** Same as notifyAll but with a single transaction. */
    fun notify(tx: WireTransaction) = notifyAll(listOf(tx))

    /**
     * Provide a [Future] for when a [StateRef] is consumed, which can be very useful in building tests.
     */
    fun whenConsumed(ref: StateRef): ListenableFuture<Vault.Update> {
        return updates.filter { it.consumed.any { it.ref == ref } }.toFuture()
    }

    /** Get contracts we would be willing to upgrade the suggested contract to. */
    // TODO: We need a better place to put business logic functions
    fun getAuthorisedContractUpgrade(ref: StateRef): Class<out UpgradedContract<*, *>>?

    /**
     * Authorise a contract state upgrade.
     * This will store the upgrade authorisation in the vault, and will be queried by [ContractUpgradeFlow.Acceptor] during contract upgrade process.
     * Invoking this method indicate the node is willing to upgrade the [state] using the [upgradedContractClass].
     * This method will NOT initiate the upgrade process. To start the upgrade process, see [ContractUpgradeFlow.Instigator].
     */
    fun authoriseContractUpgrade(stateAndRef: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>)

    /**
     * Authorise a contract state upgrade.
     * This will remove the upgrade authorisation from the vault.
     */
    fun deauthoriseContractUpgrade(stateAndRef: StateAndRef<*>)

    /**
     *  Add a note to an existing [LedgerTransaction] given by its unique [SecureHash] id
     *  Multiple notes may be attached to the same [LedgerTransaction].
     *  These are additively and immutably persisted within the node local vault database in a single textual field
     *  using a semi-colon separator
     */
    fun addNoteToTransaction(txnId: SecureHash, noteText: String)

    fun getTransactionNotes(txnId: SecureHash): Iterable<String>

    /**
     * Generate a transaction that moves an amount of currency to the given pubkey.
     *
     * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
     *
     * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
     *           to move the cash will be added on top.
     * @param amount How much currency to send.
     * @param to a key of the recipient.
     * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
     *                        of given parties. This can be useful if the party you're trying to pay has expectations
     *                        about which type of asset claims they are willing to accept.
     * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
     *         the resulting transaction for it to be valid.
     * @throws InsufficientBalanceException when a cash spending transaction fails because
     *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
     */
    @Throws(InsufficientBalanceException::class)
    @Suspendable
    fun generateSpend(tx: TransactionBuilder,
                      amount: Amount<Currency>,
                      to: CompositeKey,
                      onlyFromParties: Set<AbstractParty>? = null): Pair<TransactionBuilder, List<CompositeKey>>

    // DOCSTART VaultStatesQuery
    /**
     * Return [ContractState]s of a given [Contract] type and [Iterable] of [Vault.StateStatus].
     * Optionally may specify whether to include [StateRef] that have been marked as soft locked (default is true)
     */
    fun <T : ContractState> states(clazzes: Set<Class<T>>, statuses: EnumSet<Vault.StateStatus>, includeSoftLockedStates: Boolean = true): Iterable<StateAndRef<T>>
    // DOCEND VaultStatesQuery

    /**
     * Soft locking is used to prevent multiple transactions trying to use the same output simultaneously.
     * Violation of a soft lock would result in a double spend being created and rejected by the notary.
     */

    // DOCSTART SoftLockAPI

    /**
     * Reserve a set of [StateRef] for a given [UUID] unique identifier.
     * Typically, the unique identifier will refer to a Flow id associated with a [Transaction] in an in-flight flow.
     * In the case of coin selection, soft locks are automatically taken upon gathering relevant unconsumed input refs.
     *
     * @throws [StatesNotAvailableException] when not possible to softLock all of requested [StateRef]
     */
    @Throws(StatesNotAvailableException::class)
    fun softLockReserve(id: UUID, stateRefs: Set<StateRef>)

    /**
     * Release all or an explicitly specified set of [StateRef] for a given [UUID] unique identifier.
     * A vault soft lock manager is automatically notified of a Flows that are terminated, such that any soft locked states
     * may be released.
     * In the case of coin selection, softLock are automatically released once previously gathered unconsumed input refs
     * are consumed as part of cash spending.
     */
    fun softLockRelease(id: UUID, stateRefs: Set<StateRef>? = null)

    /**
     * Retrieve softLockStates for a given [UUID] or return all softLockStates in vault for a given
     * [ContractState] type
     */
    fun <T : ContractState> softLockedStates(lockId: UUID? = null): List<StateAndRef<T>>

    // DOCEND SoftLockAPI
}

inline fun <reified T: ContractState> VaultService.unconsumedStates(includeSoftLockedStates: Boolean = true): Iterable<StateAndRef<T>> =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.UNCONSUMED), includeSoftLockedStates)

inline fun <reified T: ContractState> VaultService.consumedStates(): Iterable<StateAndRef<T>> =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.CONSUMED))

/** Returns the [linearState] heads only when the type of the state would be considered an 'instanceof' the given type. */
inline fun <reified T : LinearState> VaultService.linearHeadsOfType() =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.UNCONSUMED))
                .associateBy { it.state.data.linearId }.mapValues { it.value }

inline fun <reified T : DealState> VaultService.dealsWith(party: AbstractParty) = linearHeadsOfType<T>().values.filter {
    it.state.data.parties.any { it == party }
}

class StatesNotAvailableException(override val message: String?, override val cause: Throwable? = null) : FlowException(message, cause) {
    override fun toString() = "Soft locking error: $message"
}

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

    fun toPrivate(publicKey: PublicKey) = keys[publicKey] ?: throw IllegalStateException("No private key known for requested public key ${publicKey.toStringShort()}")

    fun toKeyPair(publicKey: PublicKey) = KeyPair(publicKey, toPrivate(publicKey))

    /** Returns the first [KeyPair] matching any of the [publicKeys] */
    fun toKeyPair(publicKeys: Iterable<PublicKey>) = publicKeys.first { keys.contains(it) }.let { toKeyPair(it) }

    /** Generates a new random key and adds it to the exposed map. */
    fun freshKey(): KeyPair
}

// TODO: Move to a more appropriate location
/**
 * An interface that denotes a service that can accept file uploads.
 */
interface FileUploader {
    /**
     * Accepts the data in the given input stream, and returns some sort of useful return message that will be sent
     * back to the user in the response.
     */
    fun upload(file: InputStream): String

    /**
     * Check if this service accepts this type of upload. For example if you are uploading interest rates this could
     * be "my-service-interest-rates". Type here does not refer to file extentions or MIME types.
     */
    fun accepts(type: String): Boolean
}

interface AttachmentsStorageService {
    /** Provides access to storage of arbitrary JAR files (which may contain only data, no code). */
    val attachments: AttachmentStorage
}

/**
 * A sketch of an interface to a simple key/value storage system. Intended for persistence of simple blobs like
 * transactions, serialised flow state machines and so on. Again, this isn't intended to imply lack of SQL or
 * anything like that, this interface is only big enough to support the prototyping work.
 */
interface StorageService : AttachmentsStorageService {
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    val validatedTransactions: ReadOnlyTransactionStorage

    @Suppress("DEPRECATION")
    @Deprecated("This service will be removed in a future milestone")
    val uploaders: List<FileUploader>

    val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage
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

/**
 * Provides access to schedule activity at some point in time.  This interface might well be expanded to
 * increase the feature set in the future.
 *
 * If the point in time is in the past, the expectation is that the activity will happen shortly after it is scheduled.
 *
 * The main consumer initially is an observer of the vault to schedule activities based on transactions as they are
 * recorded.
 */
interface SchedulerService {
    /**
     * Schedule a new activity for a TX output, probably because it was just produced.
     *
     * Only one activity can be scheduled for a particular [StateRef] at any one time.  Scheduling a [ScheduledStateRef]
     * replaces any previously scheduled [ScheduledStateRef] for any one [StateRef].
     */
    fun scheduleStateActivity(action: ScheduledStateRef)

    /** Unschedule all activity for a TX output, probably because it was consumed. */
    fun unscheduleStateActivity(ref: StateRef)
}
