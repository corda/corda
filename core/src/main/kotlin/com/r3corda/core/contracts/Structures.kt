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
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state.
 */
interface ContractState {
    /** Contract by which the state belongs */
    val contract: Contract

    /** List of public keys for each party that can consume this state in a valid transaction. */
    val participants: List<PublicKey>
}

/** A wrapper for [ContractState] containing additional platform-level state information. This is the state */
data class TransactionState<out T : ContractState>(
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
 * Command that has to be signed by all participants of the states in the transaction
 * in order to perform a notary change
 */
class ChangeNotary : TypeOnlyCommandData()

/** Command that indicates the requirement of a Notary signature for the input states */
class NotaryCommand : TypeOnlyCommandData()

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
