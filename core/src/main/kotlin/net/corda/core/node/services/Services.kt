package net.corda.core.node.services

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.keys
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.DataFeed
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.toFuture
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.flows.AnonymisedIdentity
import rx.Observable
import rx.subjects.PublishSubject
import java.io.InputStream
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.Instant
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

        /** Checks whether the update contains a state of the specified type and state status */
        fun <T : ContractState> containsType(clazz: Class<T>, status: StateStatus) =
                when (status) {
                    StateStatus.UNCONSUMED -> produced.any { clazz.isAssignableFrom(it.state.data.javaClass) }
                    StateStatus.CONSUMED -> consumed.any { clazz.isAssignableFrom(it.state.data.javaClass) }
                    else -> consumed.any { clazz.isAssignableFrom(it.state.data.javaClass) }
                            || produced.any { clazz.isAssignableFrom(it.state.data.javaClass) }
                }

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

    @CordaSerializable
    enum class StateStatus {
        UNCONSUMED, CONSUMED, ALL
    }

    /**
     * Returned in queries [VaultService.queryBy] and [VaultService.trackBy].
     * A Page contains:
     *  1) a [List] of actual [StateAndRef] requested by the specified [QueryCriteria] to a maximum of [MAX_PAGE_SIZE]
     *  2) a [List] of associated [Vault.StateMetadata], one per [StateAndRef] result
     *  3) a total number of states that met the given [QueryCriteria] if a [PageSpecification] was provided
     *     (otherwise defaults to -1)
     *  4) Status types used in this query: UNCONSUMED, CONSUMED, ALL
     *  5) Other results as a [List] of any type (eg. aggregate function results with/without group by)
     *
     *  Note: currently otherResults are used only for Aggregate Functions (in which case, the states and statesMetadata
     *  results will be empty)
     */
    @CordaSerializable
    data class Page<out T : ContractState>(val states: List<StateAndRef<T>>,
                                           val statesMetadata: List<StateMetadata>,
                                           val totalStatesAvailable: Long,
                                           val stateTypes: StateStatus,
                                           val otherResults: List<Any>)

    @CordaSerializable
    data class StateMetadata(val ref: StateRef,
                             val contractStateClassName: String,
                             val recordedTime: Instant,
                             val consumedTime: Instant?,
                             val status: Vault.StateStatus,
                             val notaryName: String,
                             val notaryKey: String,
                             val lockId: String?,
                             val lockUpdateTime: Instant?)
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
     * Enable creation of observables of updates.
     */
    val updatesPublisher: PublishSubject<Vault.Update>

    /**
     * Returns a map of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null (not present in map), not 0.
     */
    val cashBalances: Map<Currency, Amount<Currency>>

    /**
     * Atomically get the current vault and a stream of updates. Note that the Observable buffers updates until the
     * first subscriber is registered so as to avoid racing with early updates.
     */
    // TODO: Remove this from the interface
    @Deprecated("This function will be removed in a future milestone", ReplaceWith("trackBy(QueryCriteria())"))
    fun track(): DataFeed<Vault<ContractState>, Vault.Update>

    /**
     * Return unconsumed [ContractState]s for a given set of [StateRef]s
     */
    // TODO: Remove this from the interface
    @Deprecated("This function will be removed in a future milestone", ReplaceWith("queryBy(VaultQueryCriteria(stateRefs = listOf(<StateRef>)))"))
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
                      to: AbstractParty,
                      onlyFromParties: Set<AbstractParty>? = null): Pair<TransactionBuilder, List<PublicKey>>

    // DOCSTART VaultStatesQuery
    /**
     * Return [ContractState]s of a given [Contract] type and [Iterable] of [Vault.StateStatus].
     * Optionally may specify whether to include [StateRef] that have been marked as soft locked (default is true)
     */
    // TODO: Remove this from the interface
    @Deprecated("This function will be removed in a future milestone", ReplaceWith("queryBy(QueryCriteria())"))
    fun <T : ContractState> states(clazzes: Set<Class<T>>, statuses: EnumSet<Vault.StateStatus>, includeSoftLockedStates: Boolean = true): Iterable<StateAndRef<T>>
    // DOCEND VaultStatesQuery

    /**
     * Soft locking is used to prevent multiple transactions trying to use the same output simultaneously.
     * Violation of a soft lock would result in a double spend being created and rejected by the notary.
     */

    // DOCSTART SoftLockAPI

    /**
     * Reserve a set of [StateRef] for a given [UUID] unique identifier.
     * Typically, the unique identifier will refer to a Flow lockId associated with a [Transaction] in an in-flight flow.
     * In the case of coin selection, soft locks are automatically taken upon gathering relevant unconsumed input refs.
     *
     * @throws [StatesNotAvailableException] when not possible to softLock all of requested [StateRef]
     */
    @Throws(StatesNotAvailableException::class)
    fun softLockReserve(lockId: UUID, stateRefs: Set<StateRef>)

    /**
     * Release all or an explicitly specified set of [StateRef] for a given [UUID] unique identifier.
     * A vault soft lock manager is automatically notified of a Flows that are terminated, such that any soft locked states
     * may be released.
     * In the case of coin selection, softLock are automatically released once previously gathered unconsumed input refs
     * are consumed as part of cash spending.
     */
    fun softLockRelease(lockId: UUID, stateRefs: Set<StateRef>? = null)

    /**
     * Retrieve softLockStates for a given [UUID] or return all softLockStates in vault for a given
     * [ContractState] type
     */
    fun <T : ContractState> softLockedStates(lockId: UUID? = null): List<StateAndRef<T>>

    // DOCEND SoftLockAPI

    /**
     * TODO: this function should be private to the vault, but currently Cash Exit functionality
     * is implemented in a separate module (finance) and requires access to it.
     */
    @Suspendable
    fun <T : ContractState> unconsumedStatesForSpending(amount: Amount<Currency>, onlyFromIssuerParties: Set<AbstractParty>? = null, notary: Party? = null, lockId: UUID, withIssuerRefs: Set<OpaqueBytes>? = null): List<StateAndRef<T>>
}

// TODO: Remove this from the interface
@Deprecated("This function will be removed in a future milestone", ReplaceWith("queryBy(VaultQueryCriteria())"))
inline fun <reified T : ContractState> VaultService.unconsumedStates(includeSoftLockedStates: Boolean = true): Iterable<StateAndRef<T>> =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.UNCONSUMED), includeSoftLockedStates)

// TODO: Remove this from the interface
@Deprecated("This function will be removed in a future milestone", ReplaceWith("queryBy(VaultQueryCriteria(status = Vault.StateStatus.CONSUMED))"))
inline fun <reified T : ContractState> VaultService.consumedStates(): Iterable<StateAndRef<T>> =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.CONSUMED))

/** Returns the [linearState] heads only when the type of the state would be considered an 'instanceof' the given type. */
// TODO: Remove this from the interface
@Deprecated("This function will be removed in a future milestone", ReplaceWith("queryBy(LinearStateQueryCriteria(linearId = listOf(<UniqueIdentifier>)))"))
inline fun <reified T : LinearState> VaultService.linearHeadsOfType() =
        states(setOf(T::class.java), EnumSet.of(Vault.StateStatus.UNCONSUMED))
                .associateBy { it.state.data.linearId }.mapValues { it.value }

class StatesNotAvailableException(override val message: String?, override val cause: Throwable? = null) : FlowException(message, cause) {
    override fun toString() = "Soft locking error: $message"
}

interface VaultQueryService {

    // DOCSTART VaultQueryAPI
    /**
     * Generic vault query function which takes a [QueryCriteria] object to define filters,
     * optional [PageSpecification] and optional [Sort] modification criteria (default unsorted),
     * and returns a [Vault.Page] object containing the following:
     *  1. states as a List of <StateAndRef> (page number and size defined by [PageSpecification])
     *  2. states metadata as a List of [Vault.StateMetadata] held in the Vault States table.
     *  3. total number of results available if [PageSpecification] supplied (otherwise returns -1)
     *  4. status types used in this query: UNCONSUMED, CONSUMED, ALL
     *  5. other results (aggregate functions with/without using value groups)
     *
     * @throws VaultQueryException if the query cannot be executed for any reason
     *        (missing criteria or parsing error, paging errors, unsupported query, underlying database error)
     *
     * Notes
     *   If no [PageSpecification] is provided, a maximum of [DEFAULT_PAGE_SIZE] results will be returned.
     *   API users must specify a [PageSpecification] if they are expecting more than [DEFAULT_PAGE_SIZE] results,
     *   otherwise a [VaultQueryException] will be thrown alerting to this condition.
     *   It is the responsibility of the API user to request further pages and/or specify a more suitable [PageSpecification].
     */
    @Throws(VaultQueryException::class)
    fun <T : ContractState> _queryBy(criteria: QueryCriteria,
                                     paging: PageSpecification,
                                     sorting: Sort,
                                     contractType: Class<out T>): Vault.Page<T>
    /**
     * Generic vault query function which takes a [QueryCriteria] object to define filters,
     * optional [PageSpecification] and optional [Sort] modification criteria (default unsorted),
     * and returns a [Vault.PageAndUpdates] object containing
     * 1) a snapshot as a [Vault.Page] (described previously in [queryBy])
     * 2) an [Observable] of [Vault.Update]
     *
     * @throws VaultQueryException if the query cannot be executed for any reason
     *
     * Notes: the snapshot part of the query adheres to the same behaviour as the [queryBy] function.
     *        the [QueryCriteria] applies to both snapshot and deltas (streaming updates).
     */
    @Throws(VaultQueryException::class)
    fun <T : ContractState> _trackBy(criteria: QueryCriteria,
                                     paging: PageSpecification,
                                     sorting: Sort,
                                     contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update>
    // DOCEND VaultQueryAPI

    // Note: cannot apply @JvmOverloads to interfaces nor interface implementations
    // Java Helpers
    fun <T : ContractState> queryBy(contractType: Class<out T>): Vault.Page<T> {
        return _queryBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractType)
    }
    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria): Vault.Page<T> {
        return _queryBy(criteria, PageSpecification(), Sort(emptySet()), contractType)
    }
    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
        return _queryBy(criteria, paging, Sort(emptySet()), contractType)
    }
    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
        return _queryBy(criteria, PageSpecification(), sorting, contractType)
    }
    fun <T : ContractState> queryBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): Vault.Page<T> {
        return _queryBy(criteria, paging, sorting, contractType)
    }

    fun <T : ContractState> trackBy(contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update> {
        return _trackBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractType)
    }
    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update> {
        return _trackBy(criteria, PageSpecification(), Sort(emptySet()), contractType)
    }
    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update> {
        return _trackBy(criteria, paging, Sort(emptySet()), contractType)
    }
    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update> {
        return _trackBy(criteria, PageSpecification(), sorting, contractType)
    }
    fun <T : ContractState> trackBy(contractType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update> {
        return _trackBy(criteria, paging, sorting, contractType)
    }
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(): Vault.Page<T> {
    return _queryBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria): Vault.Page<T> {
    return _queryBy(criteria, PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
    return _queryBy(criteria, paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
    return _queryBy(criteria, PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.queryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): Vault.Page<T> {
    return _queryBy(criteria, paging, sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(): DataFeed<Vault.Page<T>, Vault.Update> {
    return _trackBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update> {
    return _trackBy(criteria, PageSpecification(), Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update> {
    return _trackBy(criteria, paging, Sort(emptySet()), T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update> {
    return _trackBy(criteria, PageSpecification(), sorting, T::class.java)
}

inline fun <reified T : ContractState> VaultQueryService.trackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update> {
    return _trackBy(criteria, paging, sorting, T::class.java)
}

class VaultQueryException(description: String) : FlowException("$description")

/**
 * The KMS is responsible for storing and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 */

interface KeyManagementService {
    /**
     * Returns a snapshot of the current signing [PublicKey]s.
     * For each of these keys a [PrivateKey] is available, that can be used later for signing.
     */
    val keys: Set<PublicKey>

    /**
     * Generates a new random [KeyPair] and adds it to the internal key storage. Returns the public part of the pair.
     */
    @Suspendable
    fun freshKey(): PublicKey

    /**
     * Generates a new random [KeyPair], adds it to the internal key storage, then generates a corresponding
     * [X509Certificate] and adds it to the identity service.
     *
     * @param identity identity to generate a key and certificate for. Must be an identity this node has CA privileges for.
     * @param revocationEnabled whether to check revocation status of certificates in the certificate path.
     * @return X.509 certificate and path to the trust root.
     */
    @Suspendable
    fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): AnonymisedIdentity

    /**
     * Filter some keys down to the set that this node owns (has private keys for).
     *
     * @param candidateKeys keys which this node may own.
     */
    fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey>

    /**
     * Using the provided signing [PublicKey] internally looks up the matching [PrivateKey] and signs the data.
     * @param bytes The data to sign over using the chosen key.
     * @param publicKey The [PublicKey] partner to an internally held [PrivateKey], either derived from the node's primary identity,
     * or previously generated via the [freshKey] method.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf signing key hosted by the node is used.
     * @throws IllegalArgumentException if the input key is not a member of [keys].
     * TODO A full [KeyManagementService] implementation needs to record activity to the [AuditService] and to limit signing to
     * appropriately authorised contexts and initiating users.
     */
    @Suspendable
    fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey
}

/**
 * An interface that denotes a service that can accept file uploads.
 */
// TODO This is no longer used and can be removed
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

/**
 * Provides verification service. The implementation may be a simple in-memory verify() call or perhaps an IPC/RPC.
 */
interface TransactionVerifierService {
    /**
     * @param transaction The transaction to be verified.
     * @return A future that completes successfully if the transaction verified, or sets an exception the verifier threw.
     */
    fun verify(transaction: LedgerTransaction): ListenableFuture<*>
}
