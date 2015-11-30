package core

import core.serialization.SerializeableWithKryo
import core.serialization.serialize
import java.security.PublicKey

/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state.
 */
interface ContractState : SerializeableWithKryo {
    /**
     * Refers to a bytecode program that has previously been published to the network. This contract program
     * will be executed any time this state is used in an input. It must accept in order for the
     * transaction to proceed.
     */
    val programRef: SecureHash
}

/** Returns the SHA-256 hash of the serialised contents of this state (not cached!) */
fun ContractState.hash(): SecureHash = SecureHash.sha256((serialize()))

/**
 * A stateref is a pointer to a state, this is an equivalent of an "outpoint" in Bitcoin. It records which transaction
 * defined the state and where in that transaction it was.
 */
data class ContractStateRef(val txhash: SecureHash, val index: Int) : SerializeableWithKryo

/** A StateAndRef is simply a (state, ref) pair. For instance, a wallet (which holds available assets) contains these. */
data class StateAndRef<out T : ContractState>(val state: T, val ref: ContractStateRef)

/** An Institution is well known (name, pubkey) pair. In a real system this would probably be an X.509 certificate. */
data class Institution(val name: String, val owningKey: PublicKey) : SerializeableWithKryo {
    override fun toString() = name

    fun ref(bytes: OpaqueBytes) = InstitutionReference(this, bytes)
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}

/**
 * Reference to something being stored or issued by an institution e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the institution.
 */
data class InstitutionReference(val institution: Institution, val reference: OpaqueBytes) : SerializeableWithKryo {
    override fun toString() = "${institution.name}$reference"
}

/** Marker interface for classes that represent commands */
interface Command : SerializeableWithKryo

/** Commands that inherit from this are intended to have no data items: it's only their presence that matters. */
abstract class TypeOnlyCommand : Command {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
}

/** Wraps an object that was signed by a public key, which may be a well known/recognised institutional key. */
data class AuthenticatedObject<out T : Any>(
    val signers: List<PublicKey>,
    /** If any public keys were recognised, the looked up institutions are available here */
    val signingInstitutions: List<Institution>,
    val value: T
)

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
    fun verify(tx: TransactionForVerification)

    /**
     * Unparsed reference to the natural language contract that this code is supposed to express (usually a hash of
     * the contract's contents).
     */
    val legalContractReference: SecureHash
}