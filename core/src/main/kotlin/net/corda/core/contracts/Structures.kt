package net.corda.core.contracts

import net.corda.core.contracts.clauses.Clause
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.jar.JarInputStream

/** Implemented by anything that can be named by a secure hash value (e.g. transactions, attachments). */
interface NamedByHash {
    val id: SecureHash
}

/**
 * Interface for state objects that support being netted with other state objects.
 */
interface BilateralNettableState<N : BilateralNettableState<N>> {
    /**
     * Returns an object used to determine if two states can be subject to close-out netting. If two states return
     * equal objects, they can be close out netted together.
     */
    val bilateralNetState: Any

    /**
     * Perform bilateral netting of this state with another state. The two states must be compatible (as in
     * bilateralNetState objects are equal).
     */
    fun net(other: N): N
}

/**
 * Interface for state objects that support being netted with other state objects.
 */
interface MultilateralNettableState<out T : Any> {
    /**
     * Returns an object used to determine if two states can be subject to close-out netting. If two states return
     * equal objects, they can be close out netted together.
     */
    val multilateralNetState: T
}

interface NettableState<N : BilateralNettableState<N>, T : Any> : BilateralNettableState<N>,
        MultilateralNettableState<T>

/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state. States can be updated (consumed) only once: the
 * notary is responsible for ensuring there is no "double spending" by only signing a transaction if the input states
 * are all free.
 */
interface ContractState {
    /**
     * An instance of the contract class that will verify this state.
     *
     * # Discussion
     *
     * This field is not the final design, it's just a piece of temporary scaffolding. Once the contract sandbox is
     * further along, this field will become a description of which attachments are acceptable for defining the
     * contract.
     *
     * Recall that an attachment is a zip file that can be referenced from any transaction. The contents of the
     * attachments are merged together and cannot define any overlapping files, thus for any given transaction there
     * is a miniature file system in which each file can be precisely mapped to the defining attachment.
     *
     * Attachments may contain many things (data files, legal documents, etc) but mostly they contain JVM bytecode.
     * The class files inside define not only [Contract] implementations but also the classes that define the states.
     * Within the rest of a transaction, user-providable components are referenced by name only.
     *
     * This means that a smart contract in Corda does two things:
     *
     * 1. Define the data structures that compose the ledger (the states)
     * 2. Define the rules for updating those structures
     *
     * The first is merely a utility role ... in theory contract code could manually parse byte streams by hand.
     * The second is vital to the integrity of the ledger. So this field needs to be able to express constraints like:
     *
     * - Only attachment 733c350f396a727655be1363c06635ba355036bd54a5ed6e594fd0b5d05f42f6 may be used with this state.
     * - Any attachment signed by public key 2d1ce0e330c52b8055258d776c40 may be used with this state.
     * - Attachments (1, 2, 3) may all be used with this state.
     *
     * and so on. In this way it becomes possible for the business logic governing a state to be evolved, if the
     * constraints are flexible enough.
     *
     * Because contract classes often also define utilities that generate relevant transactions, and because attachments
     * cannot know their own hashes, we will have to provide various utilities to assist with obtaining the right
     * code constraints from within the contract code itself.
     *
     * TODO: Implement the above description. See COR-226
     */
    val contract: Contract

    /**
     * A _participant_ is any party that is able to consume this state in a valid transaction.
     *
     * The list of participants is required for certain types of transactions. For example, when changing the notary
     * for this state ([TransactionType.NotaryChange]), every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state. E.g. for [Cash] the participants
     * list should just contain the owner.
     */
    val participants: List<CompositeKey>
}

/**
 * A wrapper for [ContractState] containing additional platform-level state information.
 * This is the definitive state that is stored on the ledger and used in transaction outputs.
 */
data class TransactionState<out T : ContractState> @JvmOverloads constructor(
        /** The custom contract state */
        val data: T,
        /** Identity of the notary that ensures the state is not used as an input to a transaction more than once */
        val notary: Party,
        /**
         * All contract states may be _encumbered_ by up to one other state.
         *
         * The encumbrance state, if present, forces additional controls over the encumbered state, since the platform checks
         * that the encumbrance state is present as an input in the same transaction that consumes the encumbered state, and
         * the contract code and rules of the encumbrance state will also be verified during the execution of the transaction.
         * For example, a cash contract state could be encumbered with a time-lock contract state; the cash state is then only
         * processable in a transaction that verifies that the time specified in the encumbrance time-lock has passed.
         *
         * The encumbered state refers to another by index, and the referred encumbrance state
         * is an output state in a particular position on the same transaction that created the encumbered state. An alternative
         * implementation would be encumbering by reference to a [StateRef], which would allow the specification of encumbrance
         * by a state created in a prior transaction.
         *
         * Note that an encumbered state that is being consumed must have its encumbrance consumed in the same transaction,
         * otherwise the transaction is not valid.
         */
        val encumbrance: Int? = null) {

    /**
     * Copies the underlying state, replacing the notary field with the new value.
     * To replace the notary, we need an approval (signature) from _all_ participants of the [ContractState].
     */
    fun withNotary(newNotary: Party) = TransactionState(this.data, newNotary, encumbrance)
}

/** Wraps the [ContractState] in a [TransactionState] object */
infix fun <T : ContractState> T.`with notary`(newNotary: Party) = withNotary(newNotary)

infix fun <T : ContractState> T.withNotary(newNotary: Party) = TransactionState(this, newNotary)

/**
 * Marker interface for data classes that represent the issuance state for a contract. These are intended as templates
 * from which the state object is initialised.
 */
interface IssuanceDefinition

/**
 * Definition for an issued product, which can be cash, a cash-like thing, assets, or generally anything else that's
 * quantifiable with integer quantities.
 *
 * @param P the type of product underlying the definition, for example [Currency].
 */
data class Issued<out P>(val issuer: PartyAndReference, val product: P) {
    override fun toString() = "$product issued by $issuer"
}

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T> Amount<Issued<T>>.withoutIssuer(): Amount<T> = Amount(quantity, token.product)

/**
 * A contract state that can have a single owner.
 */
interface OwnableState : ContractState {
    /** There must be a MoveCommand signed by this key to claim the amount */
    val owner: CompositeKey

    /** Copies the underlying data structure, replacing the owner field with this new value and leaving the rest alone */
    fun withNewOwner(newOwner: CompositeKey): Pair<CommandData, OwnableState>
}

/** Something which is scheduled to happen at a point in time */
interface Scheduled {
    val scheduledAt: Instant
}

/**
 * Represents a contract state (unconsumed output) of type [LinearState] and a point in time that a lifecycle event is expected to take place
 * for that contract state.
 *
 * This is effectively the input to a scheduler, which wakes up at that point in time and asks the contract state what
 * lifecycle processing needs to take place.  e.g. a fixing or a late payment etc.
 */
data class ScheduledStateRef(val ref: StateRef, override val scheduledAt: Instant) : Scheduled

/**
 * This class represents the lifecycle activity that a contract state of type [LinearState] would like to perform at a given point in time.
 * e.g. run a fixing flow.
 *
 * Note the use of [FlowLogicRef] to represent a safe way to transport a [FlowLogic] out of the contract sandbox.
 *
 * Currently we support only flow based activities as we expect there to be a transaction generated off the back of
 * the activity, otherwise we have to start tracking secondary state on the platform of which scheduled activities
 * for a particular [ContractState] have been processed/fired etc.  If the activity is not "on ledger" then the
 * scheduled activity shouldn't be either.
 */
data class ScheduledActivity(val logicRef: FlowLogicRef, override val scheduledAt: Instant) : Scheduled

/**
 * A state that evolves by superseding itself, all of which share the common "linearId".
 *
 * This simplifies the job of tracking the current version of certain types of state in e.g. a vault.
 */
interface LinearState : ContractState {
    /**
     * Unique id shared by all LinearState states throughout history within the vaults of all parties.
     * Verify methods should check that one input and one output share the id in a transaction,
     * except at issuance/termination.
     */
    val linearId: UniqueIdentifier

    /**
     * True if this should be tracked by our vault(s).
     * */
    fun isRelevant(ourKeys: Set<PublicKey>): Boolean

    /**
     * Standard clause to verify the LinearState safety properties.
     */
    class ClauseVerifier<S : LinearState, C : CommandData>() : Clause<S, C, Unit>() {
        override fun verify(tx: TransactionForContract,
                            inputs: List<S>,
                            outputs: List<S>,
                            commands: List<AuthenticatedObject<C>>,
                            groupingKey: Unit?): Set<C> {
            val inputIds = inputs.map { it.linearId }.distinct()
            val outputIds = outputs.map { it.linearId }.distinct()
            requireThat {
                "LinearStates are not merged" by (inputIds.count() == inputs.count())
                "LinearStates are not split" by (outputIds.count() == outputs.count())
            }
            return emptySet()
        }
    }
}

interface SchedulableState : ContractState {
    /**
     * Indicate whether there is some activity to be performed at some future point in time with respect to this
     * [ContractState], what that activity is and at what point in time it should be initiated.
     * This can be used to implement deadlines for payment or processing of financial instruments according to a schedule.
     *
     * The state has no reference to it's own StateRef, so supply that for use as input to any FlowLogic constructed.
     *
     * @return null if there is no activity to schedule.
     */
    fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity?
}

/**
 * Interface representing an agreement that exposes various attributes that are common. Implementing it simplifies
 * implementation of general flows that manipulate many agreement types.
 */
interface DealState : LinearState {
    /** Human readable well known reference (e.g. trade reference) */
    val ref: String

    /**
     * Exposes the Parties involved in a generic way.
     *
     * Appears to duplicate [participants] a property of [ContractState]. However [participants] only holds public keys.
     * Currently we need to hard code Party objects into [ContractState]s. [Party] objects are a wrapper for public
     * keys which also contain some identity information about the public key owner. You can keep track of individual
     * parties by adding a property for each one to the state, or you can append parties to the [parties] list if you
     * are implementing [DealState]. We need to do this as identity management in Corda is currently incomplete,
     * therefore the only way to record identity information is in the [ContractState]s themselves. When identity
     * management is completed, parties to a transaction will only record public keys in the [DealState] and through a
     * separate process exchange certificates to ascertain identities. Thus decoupling identities from
     * [ContractState]s.
     * */
    val parties: List<Party>

    /**
     * Generate a partial transaction representing an agreement (command) to this deal, allowing a general
     * deal/agreement flow to generate the necessary transaction for potential implementations.
     *
     * TODO: Currently this is the "inception" transaction but in future an offer of some description might be an input state ref
     *
     * TODO: This should more likely be a method on the Contract (on a common interface) and the changes to reference a
     * Contract instance from a ContractState are imminent, at which point we can move this out of here.
     */
    fun generateAgreement(notary: Party): TransactionBuilder
}

/**
 * Interface adding fixing specific methods.
 */
interface FixableDealState : DealState {
    /**
     * When is the next fixing and what is the fixing for?
     */
    fun nextFixingOf(): FixOf?

    /**
     * What oracle service to use for the fixing
     */
    val oracleType: ServiceType

    /**
     * Generate a fixing command for this deal and fix.
     *
     * TODO: This would also likely move to methods on the Contract once the changes to reference
     * the Contract from the ContractState are in.
     */
    fun generateFix(ptx: TransactionBuilder, oldState: StateAndRef<*>, fix: Fix)
}

/** Returns the SHA-256 hash of the serialised contents of this state (not cached!) */
fun ContractState.hash(): SecureHash = SecureHash.sha256(serialize().bytes)

/**
 * A stateref is a pointer (reference) to a state, this is an equivalent of an "outpoint" in Bitcoin. It records which
 * transaction defined the state and where in that transaction it was.
 */
data class StateRef(val txhash: SecureHash, val index: Int) {
    override fun toString() = "$txhash($index)"
}

/** A StateAndRef is simply a (state, ref) pair. For instance, a vault (which holds available assets) contains these. */
data class StateAndRef<out T : ContractState>(val state: TransactionState<T>, val ref: StateRef)

/** Filters a list of [StateAndRef] objects according to the type of the states */
inline fun <reified T : ContractState> Iterable<StateAndRef<ContractState>>.filterStatesOfType(): List<StateAndRef<T>> {
    return mapNotNull { if (it.state.data is T) StateAndRef(TransactionState(it.state.data, it.state.notary), it.ref) else null }
}

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
data class PartyAndReference(val party: AnonymousParty, val reference: OpaqueBytes) {
    override fun toString() = "${party}$reference"
}

/** Marker interface for classes that represent commands */
interface CommandData

/** Commands that inherit from this are intended to have no data items: it's only their presence that matters. */
abstract class TypeOnlyCommandData : CommandData {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
}

/** Command data/content plus pubkey pair: the signature is stored at the end of the serialized bytes */
data class Command(val value: CommandData, val signers: List<CompositeKey>) {
    init {
        require(signers.isNotEmpty())
    }

    constructor(data: CommandData, key: CompositeKey) : this(data, listOf(key))

    private fun commandDataToString() = value.toString().let { if (it.contains("@")) it.replace('$', '.').split("@")[0] else it }
    override fun toString() = "${commandDataToString()} with pubkeys ${signers.joinToString()}"
}

/** A common issue command, to enforce that issue commands have a nonce value. */
// TODO: Revisit use of nonce values - should this be part of the TX rather than the command perhaps?
interface IssueCommand : CommandData {
    val nonce: Long
}

/** A common move command for contracts which can change owner. */
interface MoveCommand : CommandData {
    /**
     * Contract code the moved state(s) are for the attention of, for example to indicate that the states are moved in
     * order to settle an obligation contract's state object(s).
     */
    // TODO: Replace SecureHash here with a general contract constraints object
    val contractHash: SecureHash?
}

/** A common netting command for contracts whose states can be netted. */
interface NetCommand : CommandData {
    /** The type of netting to apply, see [NetType] for options. */
    val type: NetType
}

/** Wraps an object that was signed by a public key, which may be a well known/recognised institutional key. */
data class AuthenticatedObject<out T : Any>(
        val signers: List<CompositeKey>,
        /** If any public keys were recognised, the looked up institutions are available here */
        val signingParties: List<Party>,
        val value: T
)

/**
 * If present in a transaction, contains a time that was verified by the uniqueness service. The true time must be
 * between (after, before).
 */
data class Timestamp(val after: Instant?, val before: Instant?) {
    init {
        if (after == null && before == null)
            throw IllegalArgumentException("At least one of before/after must be specified")
        if (after != null && before != null)
            check(after <= before)
    }

    constructor(time: Instant, tolerance: Duration) : this(time - tolerance, time + tolerance)

    val midpoint: Instant get() = after!! + Duration.between(after, before!!).dividedBy(2)
}

/**
 * Implemented by a program that implements business logic on the shared ledger. All participants run this code for
 * every [LedgerTransaction] they see on the network, for every input and output state. All contracts must accept the
 * transaction for it to be accepted: failure of any aborts the entire thing. The time is taken from a trusted
 * timestamp attached to the transaction itself i.e. it is NOT necessarily the current time.
 */
interface Contract {
    /**
     * Takes an object that represents a state transition, and ensures the inputs/outputs/commands make sense.
     * Must throw an exception if there's a problem that should prevent state transition. Takes a single object
     * rather than an argument so that additional data can be added without breaking binary compatibility with
     * existing contract code.
     */
    @Throws(IllegalArgumentException::class)
    fun verify(tx: TransactionForContract)

    /**
     * Unparsed reference to the natural language contract that this code is supposed to express (usually a hash of
     * the contract's contents).
     */
    val legalContractReference: SecureHash
}

/**
 * An attachment is a ZIP (or an optionally signed JAR) that contains one or more files. Attachments are meant to
 * contain public static data which can be referenced from transactions and utilised from contracts. Good examples
 * of how attachments are meant to be used include:
 *
 * - Calendar data
 * - Fixes (e.g. LIBOR)
 * - Smart contract code
 * - Legal documents
 * - Facts generated by oracles which might be reused a lot
 */
interface Attachment : NamedByHash {
    fun open(): InputStream
    fun openAsJAR() = JarInputStream(open())

    /**
     * Finds the named file case insensitively and copies it to the output stream.
     *
     * @throws FileNotFoundException if the given path doesn't exist in the attachment.
     */
    fun extractFile(path: String, outputTo: OutputStream) {
        val p = path.toLowerCase().split('\\', '/')
        openAsJAR().use { jar ->
            while (true) {
                val e = jar.nextJarEntry ?: break
                if (e.name.toLowerCase().split('\\', '/') == p) {
                    jar.copyTo(outputTo)
                    return
                }
                jar.closeEntry()
            }
        }
        throw FileNotFoundException()
    }
}


