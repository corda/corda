package core

import java.security.PublicKey
import java.security.Timestamp

/**
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions. States are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state.
 */
interface ContractState {
    /**
     * Refers to a bytecode program that has previously been published to the network. This contract program
     * will be executed any time this state is used in an input. It must accept in order for the
     * transaction to proceed.
     */
    val programRef: SecureHash
}

/**
 * A stateref is a pointer to a state, normally a hash but this version is opaque.
 */
class ContractStateRef(private val hash: SecureHash.SHA256)

/**
 * A transition groups one or more transactions together, and combines them with a signed timestamp. A transaction
 * may not stand independent of a transition and all transactions are applied or reverted together as a unit.
 *
 * Consider the following attack: a malicious actor extracts a single transaction like "pay $X to me" from a transition
 * and then broadcasts it with a fresh timestamp. This cannot work because the original transition will always take
 * priority over the later attempt as it has an earlier timestamp. As long as both are visible, the first transition
 * will always win.
 */
data class Transition(
    val tx: LedgerTransaction,

    /** Timestamp of the serialised transaction as fetched from a timestamping authority (RFC 3161) */
    val signedTimestamp: Timestamp
)

class Institution(
    val name: String,
    val owningKey: PublicKey
) {
    override fun toString() = name
}

interface Command

/** Provided as an input to a contract; converted to a [VerifiedSignedCommand] by the platform before execution. */
data class SignedCommand(
    /** Signatures over this object to prove who it came from: this is fetched off the end of the transaction wire format. */
    val commandDataSignatures: List<DigitalSignature.WithKey>,

    /** Command data, deserialized to an implementation of [Command] */
    val serialized: OpaqueBytes,
    /** Identifies what command the serialized data contains (hash of bytecode?) */
    val classID: SecureHash
)

/** Obtained from a [SignedCommand], deserialised and signature checked */
data class VerifiedSigned<out T : Command>(
    val signers: List<PublicKey>,
    /** If any public keys were recognised, the looked up institutions are available here */
    val signingInstitutions: List<Institution>,
    val value: T
)

/**
 * Implemented by a program that implements business logic on the shared ledger. All participants run this code for
 * every [LedgerTransaction] they see on the network, for every input state. All input states must accept the transaction
 * for it to be accepted: failure of any aborts the entire thing. The time is taken from a trusted timestamp attached
 * to the transaction itself i.e. it is NOT necessarily the current time.
 */
interface Contract {
    /**
     * Takes an object that represents a state transition, and ensures the inputs/outputs/commands make sense.
     * Must throw an exception if there's a problem that should prevent state transition. Takes a single object
     * rather than an argument so that additional data can be added without breaking binary compatibility with
     * existing contract code.
     */
    fun verify(tx: TransactionForVerification)

    // TODO: This should probably be a hash of a document, rather than a URL to it.
    /** Unparsed reference to the natural language contract that this code is supposed to express (usually a URL). */
    val legalContractReference: String
}

/**
 * Reference to something being stored or issued by an institution e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the institution.
 */
data class InstitutionReference(val institution: Institution, val reference: OpaqueBytes) {
    override fun toString() = "${institution.name}$reference"
}