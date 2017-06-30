package net.corda.core.contracts

import net.corda.core.contracts.clauses.Clause
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.*
import java.io.FileNotFoundException
import java.io.IOException
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

// DOCSTART 1
/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state. States can be updated (consumed) only once: the
 * notary is responsible for ensuring there is no "double spending" by only signing a transaction if the input states
 * are all free.
 */
@CordaSerializable
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
     * The participants list should normally be derived from the contents of the state.
     */
    val participants: List<AbstractParty>
}
// DOCEND 1

// DOCSTART 4
/**
 * A wrapper for [ContractState] containing additional platform-level state information.
 * This is the definitive state that is stored on the ledger and used in transaction outputs.
 */
@CordaSerializable
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
        val encumbrance: Int? = null)
// DOCEND 4

/** Wraps the [ContractState] in a [TransactionState] object */
infix fun <T : ContractState> T.`with notary`(newNotary: Party) = withNotary(newNotary)

infix fun <T : ContractState> T.withNotary(newNotary: Party) = TransactionState(this, newNotary)

/**
 * Definition for an issued product, which can be cash, a cash-like thing, assets, or generally anything else that's
 * quantifiable with integer quantities.
 *
 * @param P the type of product underlying the definition, for example [java.util.Currency].
 */
@CordaSerializable
data class Issued<out P : Any>(val issuer: PartyAndReference, val product: P) {
    override fun toString() = "$product issued by $issuer"
}

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T : Any> Amount<Issued<T>>.withoutIssuer(): Amount<T> = Amount(quantity, token.product)

// DOCSTART 3
/**
 * A contract state that can have a single owner.
 */
interface OwnableState : ContractState {
    /** There must be a MoveCommand signed by this key to claim the amount */
    val owner: AbstractParty

    /** Copies the underlying data structure, replacing the owner field with this new value and leaving the rest alone */
    fun withNewOwner(newOwner: AbstractParty): Pair<CommandData, OwnableState>
}
// DOCEND 3

/** Something which is scheduled to happen at a point in time */
interface Scheduled {
    val scheduledAt: Instant
}

/**
 * Represents a contract state (unconsumed output) of type [LinearState] and a point in time that a lifecycle event is
 * expected to take place for that contract state.
 *
 * This is effectively the input to a scheduler, which wakes up at that point in time and asks the contract state what
 * lifecycle processing needs to take place.  e.g. a fixing or a late payment etc.
 */
data class ScheduledStateRef(val ref: StateRef, override val scheduledAt: Instant) : Scheduled

/**
 * This class represents the lifecycle activity that a contract state of type [LinearState] would like to perform at a
 * given point in time. e.g. run a fixing flow.
 *
 * Note the use of [FlowLogicRef] to represent a safe way to transport a [net.corda.core.flows.FlowLogic] out of the
 * contract sandbox.
 *
 * Currently we support only flow based activities as we expect there to be a transaction generated off the back of
 * the activity, otherwise we have to start tracking secondary state on the platform of which scheduled activities
 * for a particular [ContractState] have been processed/fired etc.  If the activity is not "on ledger" then the
 * scheduled activity shouldn't be either.
 */
data class ScheduledActivity(val logicRef: FlowLogicRef, override val scheduledAt: Instant) : Scheduled

// DOCSTART 2
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
     */
    fun isRelevant(ourKeys: Set<PublicKey>): Boolean

    /**
     * Standard clause to verify the LinearState safety properties.
     */
    @CordaSerializable
    class ClauseVerifier<in S : LinearState, C : CommandData> : Clause<S, C, Unit>() {
        override fun verify(tx: TransactionForContract,
                            inputs: List<S>,
                            outputs: List<S>,
                            commands: List<AuthenticatedObject<C>>,
                            groupingKey: Unit?): Set<C> {
            val inputIds = inputs.map { it.linearId }.distinct()
            val outputIds = outputs.map { it.linearId }.distinct()
            requireThat {
                "LinearStates are not merged" using (inputIds.count() == inputs.count())
                "LinearStates are not split" using (outputIds.count() == outputs.count())
            }
            return emptySet()
        }
    }
}
// DOCEND 2

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

/** Returns the SHA-256 hash of the serialised contents of this state (not cached!) */
fun ContractState.hash(): SecureHash = SecureHash.sha256(serialize().bytes)

/**
 * A stateref is a pointer (reference) to a state, this is an equivalent of an "outpoint" in Bitcoin. It records which
 * transaction defined the state and where in that transaction it was.
 */
@CordaSerializable
// DOCSTART 8
data class StateRef(val txhash: SecureHash, val index: Int) {
    override fun toString() = "$txhash($index)"
}
// DOCEND 8

/** A StateAndRef is simply a (state, ref) pair. For instance, a vault (which holds available assets) contains these. */
@CordaSerializable
// DOCSTART 7
data class StateAndRef<out T : ContractState>(val state: TransactionState<T>, val ref: StateRef)
// DOCEND 7

/** Filters a list of [StateAndRef] objects according to the type of the states */
inline fun <reified T : ContractState> Iterable<StateAndRef<ContractState>>.filterStatesOfType(): List<StateAndRef<T>> {
    return mapNotNull { if (it.state.data is T) StateAndRef(TransactionState(it.state.data, it.state.notary), it.ref) else null }
}

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
@CordaSerializable
data class PartyAndReference(val party: AbstractParty, val reference: OpaqueBytes) {
    override fun toString() = "$party$reference"
}

/** Marker interface for classes that represent commands */
@CordaSerializable
interface CommandData

/** Commands that inherit from this are intended to have no data items: it's only their presence that matters. */
abstract class TypeOnlyCommandData : CommandData {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
}

/** Command data/content plus pubkey pair: the signature is stored at the end of the serialized bytes */
@CordaSerializable
// DOCSTART 9
data class Command(val value: CommandData, val signers: List<PublicKey>) {
// DOCEND 9
    init {
        require(signers.isNotEmpty())
    }

    constructor(data: CommandData, key: PublicKey) : this(data, listOf(key))

    private fun commandDataToString() = value.toString().let { if (it.contains("@")) it.replace('$', '.').split("@")[0] else it }
    override fun toString() = "${commandDataToString()} with pubkeys ${signers.joinToString()}"
}

/** A common issue command, to enforce that issue commands have a nonce value. */
// TODO: Revisit use of nonce values - should this be part of the TX rather than the command perhaps?
interface IssueCommand : CommandData {
    val nonce: Long
}

/** A common move command for contract states which can change owner. */
interface MoveCommand : CommandData {
    /**
     * Contract code the moved state(s) are for the attention of, for example to indicate that the states are moved in
     * order to settle an obligation contract's state object(s).
     */
    // TODO: Replace SecureHash here with a general contract constraints object
    val contractHash: SecureHash?
}

/** Indicates that this transaction replaces the inputs contract state to another contract state */
data class UpgradeCommand(val upgradedContractClass: Class<out UpgradedContract<*, *>>) : CommandData

// DOCSTART 6
/** Wraps an object that was signed by a public key, which may be a well known/recognised institutional key. */
@CordaSerializable
data class AuthenticatedObject<out T : Any>(
        val signers: List<PublicKey>,
        /** If any public keys were recognised, the looked up institutions are available here */
        val signingParties: List<Party>,
        val value: T
)
// DOCEND 6

/**
 * A time-window is required for validation/notarization purposes.
 * If present in a transaction, contains a time that was verified by the uniqueness service. The true time must be
 * between (fromTime, untilTime).
 * Usually, a time-window is required to have both sides set (fromTime, untilTime).
 * However, some apps may require that a time-window has a start [Instant] (fromTime), but no end [Instant] (untilTime) and vice versa.
 * TODO: Consider refactoring using TimeWindow abstraction like TimeWindow.From, TimeWindow.Until, TimeWindow.Between.
 */
@CordaSerializable
class TimeWindow private constructor(
        /** The time at which this transaction is said to have occurred is after this moment. */
        val fromTime: Instant?,
        /** The time at which this transaction is said to have occurred is before this moment. */
        val untilTime: Instant?
) {
    companion object {
        /** Use when the left-side [fromTime] of a [TimeWindow] is only required and we don't need an end instant (untilTime). */
        @JvmStatic
        fun fromOnly(fromTime: Instant) = TimeWindow(fromTime, null)

        /** Use when the right-side [untilTime] of a [TimeWindow] is only required and we don't need a start instant (fromTime). */
        @JvmStatic
        fun untilOnly(untilTime: Instant) = TimeWindow(null, untilTime)

        /** Use when both sides of a [TimeWindow] must be set ([fromTime], [untilTime]). */
        @JvmStatic
        fun between(fromTime: Instant, untilTime: Instant): TimeWindow {
            require(fromTime < untilTime) { "fromTime should be earlier than untilTime" }
            return TimeWindow(fromTime, untilTime)
        }

        /** Use when we have a start time and a period of validity. */
        @JvmStatic
        fun fromStartAndDuration(fromTime: Instant, duration: Duration): TimeWindow = between(fromTime, fromTime + duration)

        /**
         * When we need to create a [TimeWindow] based on a specific time [Instant] and some tolerance in both sides of this instant.
         * The result will be the following time-window: ([time] - [tolerance], [time] + [tolerance]).
         */
        @JvmStatic
        fun withTolerance(time: Instant, tolerance: Duration) = between(time - tolerance, time + tolerance)
    }

    /** The midpoint is calculated as fromTime + (untilTime - fromTime)/2. Note that it can only be computed if both sides are set. */
    val midpoint: Instant get() = fromTime!! + Duration.between(fromTime, untilTime!!).dividedBy(2)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimeWindow) return false
        return (fromTime == other.fromTime && untilTime == other.untilTime)
    }

    override fun hashCode() = 31 * (fromTime?.hashCode() ?: 0) + (untilTime?.hashCode() ?: 0)

    override fun toString() = "TimeWindow(fromTime=$fromTime, untilTime=$untilTime)"
}

// DOCSTART 5
/**
 * Implemented by a program that implements business logic on the shared ledger. All participants run this code for
 * every [net.corda.core.transactions.LedgerTransaction] they see on the network, for every input and output state. All
 * contracts must accept the transaction for it to be accepted: failure of any aborts the entire thing. The time is taken
 * from a trusted time-window attached to the transaction itself i.e. it is NOT necessarily the current time.
 *
 * TODO: Contract serialization is likely to change, so the annotation is likely temporary.
 */
@CordaSerializable
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
// DOCEND 5

/**
 * Interface which can upgrade state objects issued by a contract to a new state object issued by a different contract.
 *
 * @param OldState the old contract state (can be [ContractState] or other common supertype if this supports upgrading
 * more than one state).
 * @param NewState the upgraded contract state.
 */
interface UpgradedContract<in OldState : ContractState, out NewState : ContractState> : Contract {
    val legacyContract: Class<out Contract>
    /**
     * Upgrade contract's state object to a new state object.
     *
     * @throws IllegalArgumentException if the given state object is not one that can be upgraded. This can be either
     * that the class is incompatible, or that the data inside the state object cannot be upgraded for some reason.
     */
    fun upgrade(state: OldState): NewState
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

    fun openAsJAR(): JarInputStream {
        val stream = open()
        try {
            return JarInputStream(stream)
        } catch (t: Throwable) {
            stream.use { throw t }
        }
    }

    /**
     * Finds the named file case insensitively and copies it to the output stream.
     *
     * @throws FileNotFoundException if the given path doesn't exist in the attachment.
     */
    fun extractFile(path: String, outputTo: OutputStream) = openAsJAR().use { it.extractFile(path, outputTo) }
}

abstract class AbstractAttachment(dataLoader: () -> ByteArray) : Attachment {
    companion object {
        fun SerializeAsTokenContext.attachmentDataLoader(id: SecureHash): () -> ByteArray {
            return {
                val a = serviceHub.attachments.openAttachment(id) ?: throw MissingAttachmentsException(listOf(id))
                (a as? AbstractAttachment)?.attachmentData ?: a.open().use { it.readBytes() }
            }
        }
    }

    protected val attachmentData: ByteArray by lazy(dataLoader)
    override fun open(): InputStream = attachmentData.inputStream()
    override fun equals(other: Any?) = other === this || other is Attachment && other.id == this.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "${javaClass.simpleName}(id=$id)"
}

@Throws(IOException::class)
fun JarInputStream.extractFile(path: String, outputTo: OutputStream) {
    val p = path.toLowerCase().split('\\', '/')
    while (true) {
        val e = nextJarEntry ?: break
        if (!e.isDirectory && e.name.toLowerCase().split('\\', '/') == p) {
            copyTo(outputTo)
            return
        }
        closeEntry()
    }
    throw FileNotFoundException(path)
}
