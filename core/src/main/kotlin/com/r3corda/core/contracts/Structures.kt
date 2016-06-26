package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.serialization.serialize
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
interface BilateralNettableState<T: BilateralNettableState<T>> {
    /**
     * Returns an object used to determine if two states can be subject to close-out netting. If two states return
     * equal objects, they can be close out netted together.
     */
    val bilateralNetState: Any

    /**
     * Perform bilateral netting of this state with another state. The two states must be compatible (as in
     * bilateralNetState objects are equal).
     */
    fun net(other: T): T
}

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
     * The classfiles inside define not only [Contract] implementations but also the classes that define the states.
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
     * for this state ([TransactionType.NotaryChange]), every participants has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess, since someone consumed that state during the notary change process.
     *
     * The participants list should normally be derived from the contents of the state. E.g. for [Cash] the participants
     * list should just contain the owner.
     */
    val participants: List<PublicKey>
}

/**
 * A wrapper for [ContractState] containing additional platform-level state information.
 * This is the definitive state that is stored on the ledger and used in transaction outputs.
 */
data class TransactionState<out T : ContractState>(
        /** The custom contract state */
        val data: T,
        /** Identity of the notary that ensures the state is not used as an input to a transaction more than once */
        val notary: Party) {
    /**
     * Copies the underlying state, replacing the notary field with the new value.
     * To replace the notary, we need an approval (signature) from _all_ participants of the [ContractState]
     */
    fun withNewNotary(newNotary: Party) = TransactionState(this.data, newNotary)
}

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
data class Issued<P>(
        val issuer: PartyAndReference,
        val product: P
)

/**
 * A contract state that can have a single owner.
 */
interface OwnableState : ContractState {
    /** There must be a MoveCommand signed by this key to claim the amount */
    val owner: PublicKey

    /** Copies the underlying data structure, replacing the owner field with this new value and leaving the rest alone */
    fun withNewOwner(newOwner: PublicKey): Pair<CommandData, OwnableState>
}

/**
 * A state that evolves by superseding itself, all of which share the common "thread"
 *
 * This simplifies the job of tracking the current version of certain types of state in e.g. a wallet
 */
interface LinearState : ContractState {
    /** Unique thread id within the wallets of all parties */
    val thread: SecureHash

    /** true if this should be tracked by our wallet(s) */
    fun isRelevant(ourKeys: Set<PublicKey>): Boolean
}

/**
 * Interface representing an agreement that exposes various attributes that are common. Implementing it simplifies
 * implementation of general protocols that manipulate many agreement types.
 */
interface DealState : LinearState {

    /** Human readable well known reference (e.g. trade reference) */
    val ref: String

    /** Exposes the Parties involved in a generic way */
    val parties: Array<Party>

    // TODO: This works by editing the keys used by a Party which is invalid.
    fun withPublicKey(before: Party, after: PublicKey): DealState

    /**
     * Generate a partial transaction representing an agreement (command) to this deal, allowing a general
     * deal/agreement protocol to generate the necessary transaction for potential implementations
     *
     * TODO: Currently this is the "inception" transaction but in future an offer of some description might be an input state ref
     *
     * TODO: This should more likely be a method on the Contract (on a common interface) and the changes to reference a
     * Contract instance from a ContractState are imminent, at which point we can move this out of here
     */
    fun generateAgreement(notary: Party): TransactionBuilder
}

/**
 * Interface adding fixing specific methods
 */
interface FixableDealState : DealState {
    /**
     * When is the next fixing and what is the fixing for?
     *
     * TODO: In future we would use this to register for an event to trigger a/the fixing protocol
     */
    fun nextFixingOf(): FixOf?

    /**
     * Generate a fixing command for this deal and fix
     *
     * TODO: This would also likely move to methods on the Contract once the changes to reference
     * the Contract from the ContractState are in
     */
    fun generateFix(ptx: TransactionBuilder, oldState: StateAndRef<*>, fix: Fix)
}

/** Returns the SHA-256 hash of the serialised contents of this state (not cached!) */
fun ContractState.hash(): SecureHash = SecureHash.sha256(serialize().bits)

/**
 * A stateref is a pointer (reference) to a state, this is an equivalent of an "outpoint" in Bitcoin. It records which
 * transaction defined the state and where in that transaction it was.
 */
data class StateRef(val txhash: SecureHash, val index: Int) {
    override fun toString() = "$txhash($index)"
}

/** A StateAndRef is simply a (state, ref) pair. For instance, a wallet (which holds available assets) contains these. */
data class StateAndRef<out T : ContractState>(val state: TransactionState<T>, val ref: StateRef)

/** Filters a list of [StateAndRef] objects according to the type of the states */
inline fun <reified T : ContractState> List<StateAndRef<ContractState>>.filterStatesOfType(): List<StateAndRef<T>> {
    return mapNotNull { if (it.state.data is T) StateAndRef(TransactionState(it.state.data, it.state.notary), it.ref) else null }
}

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
data class PartyAndReference(val party: Party, val reference: OpaqueBytes) {
    override fun toString() = "${party.name}$reference"
}

/** Marker interface for classes that represent commands */
interface CommandData

/** Commands that inherit from this are intended to have no data items: it's only their presence that matters. */
abstract class TypeOnlyCommandData : CommandData {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
}

/** Command data/content plus pubkey pair: the signature is stored at the end of the serialized bytes */
data class Command(val value: CommandData, val signers: List<PublicKey>) {
    init {
        require(signers.isNotEmpty())
    }

    constructor(data: CommandData, key: PublicKey) : this(data, listOf(key))

    private fun commandDataToString() = value.toString().let { if (it.contains("@")) it.replace('$', '.').split("@")[0] else it }
    override fun toString() = "${commandDataToString()} with pubkeys ${signers.map { it.toStringShort() }}"
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

/** Wraps an object that was signed by a public key, which may be a well known/recognised institutional key. */
data class AuthenticatedObject<out T : Any>(
        val signers: List<PublicKey>,
        /** If any public keys were recognised, the looked up institutions are available here */
        val signingParties: List<Party>,
        val value: T
)

/**
 * If present in a transaction, contains a time that was verified by the timestamping authority/authorities whose
 * public keys are identified in the containing [Command] object. The true time must be between (after, before)
 */
data class TimestampCommand(val after: Instant?, val before: Instant?) : CommandData {
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
        val p = path.toLowerCase()
        openAsJAR().use { jar ->
            while (true) {
                val e = jar.nextJarEntry ?: break
                // TODO: Normalise path separators here for more platform independence, as zip doesn't mandate a type.
                if (e.name.toLowerCase() == p) {
                    jar.copyTo(outputTo)
                    return
                }
                jar.closeEntry()
            }
        }
        throw FileNotFoundException()
    }
}
