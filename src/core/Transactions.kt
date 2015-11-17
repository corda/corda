package core

import core.serialization.SerializeableWithKryo
import core.serialization.deserialize
import core.serialization.serialize
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
import java.time.Instant
import java.util.*

/**
 * Views of a transaction as it progresses through the pipeline, from bytes loaded from disk/network to the object
 * tree passed into a contract.
 *
 * TimestampedWireTransaction wraps a serialized SignedWireTransaction. The timestamp is a signature from a timestamping
 * authority and is what gives the contract a sense of time. This isn't used yet.
 *
 * SignedWireTransaction wraps a serialized WireTransaction. It contains one or more ECDSA signatures, each one from
 * a public key that is mentioned inside a transaction command.
 *
 * WireTransaction is a transaction in a form ready to be serialised/unserialised/hashed. This is the object from which
 * a transaction ID (hash) is calculated. It contains no signatures and no timestamp. That means, sending a transaction
 * to a timestamping authority does NOT change its hash (this may be an issue that leads to confusion and should be
 * examined more closely).
 *
 * A PartialTransaction is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands or modifying the existing set.
 * Then once the states and commands are right, this class can be used as a holding bucket to gather signatures from
 * multiple parties.
 *
 * LedgerTransaction is derived from WireTransaction and TimestampedWireTransaction together. It is the result of
 * doing some basic key lookups on WireCommand to see if any keys are from a recognised institution, thus converting
 * the WireCommand objects into AuthenticatedObject<Command>. Currently we just assume a hard coded pubkey->institution
 * map. In future it'd make more sense to use a certificate scheme and so that logic would get more complex.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * TransactionForVerification is the same as LedgerTransaction but with the input states looked up from a local
 * database and replaced with the real objects. TFV is the form that is finally fed into the contracts.
 */

/** Serialized command plus pubkey pair: the signature is stored at the end of the serialized bytes */
data class WireCommand(val command: Command, val pubkeys: List<PublicKey>) : SerializeableWithKryo

/** Transaction ready for serialisation, without any signatures attached. */
data class WireTransaction(val inputStates: List<ContractStateRef>,
                           val outputStates: List<ContractState>,
                           val commands: List<WireCommand>) : SerializeableWithKryo {
    val hash: SecureHash get() = SecureHash.sha256(serialize())

    fun toLedgerTransaction(timestamp: Instant, institutionKeyMap: Map<PublicKey, Institution>): LedgerTransaction {
        val authenticatedArgs = commands.map {
            val institutions = it.pubkeys.mapNotNull { pk -> institutionKeyMap[pk] }
            AuthenticatedObject(it.pubkeys, institutions, it.command)
        }
        return LedgerTransaction(inputStates, outputStates, authenticatedArgs, timestamp)
    }
}

/** A mutable transaction that's in the process of being built, before all signatures are present. */
class PartialTransaction(private val inputStates: MutableList<ContractStateRef> = arrayListOf(),
                         private val outputStates: MutableList<ContractState> = arrayListOf(),
                         private val commands: MutableList<WireCommand> = arrayListOf()) {

    /** A more convenient constructor that sorts things into the right lists for you */
    constructor(vararg things: Any) : this() {
        for (t in things) {
            when (t) {
                is ContractStateRef -> inputStates.add(t)
                is ContractState -> outputStates.add(t)
                is WireCommand -> commands.add(t)
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
    }

    /** The signatures that have been collected so far - might be incomplete! */
    private val currentSigs = arrayListOf<DigitalSignature.WithKey>()

    fun signWith(key: KeyPair) {
        check(currentSigs.none { it.by == key.public }) { "This partial transaction was already signed by ${key.public}" }
        check(commands.count { it.pubkeys.contains(key.public) } > 0) { "Trying to sign with a key that isn't in any command" }
        val bits = toWireTransaction().serialize()
        currentSigs.add(key.private.signWithECDSA(bits, key.public))
    }

    fun toWireTransaction() = WireTransaction(inputStates, outputStates, commands)

    fun toSignedTransaction(): SignedWireTransaction {
        val requiredKeys = commands.flatMap { it.pubkeys }.toSet()
        val gotKeys = currentSigs.map { it.by }.toSet()
        check(gotKeys == requiredKeys) { "The set of required signatures isn't equal to the signatures we've got" }
        return SignedWireTransaction(toWireTransaction().serialize(), ArrayList(currentSigs))
    }

    fun addInputState(ref: ContractStateRef) {
        check(currentSigs.isEmpty())
        inputStates.add(ref)
    }

    fun addOutputState(state: ContractState) {
        check(currentSigs.isEmpty())
        outputStates.add(state)
    }

    fun addArg(arg: WireCommand) {
        check(currentSigs.isEmpty())
        commands.add(arg)
    }

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<ContractStateRef> = ArrayList(inputStates)
    fun outputStates(): List<ContractState> = ArrayList(outputStates)
    fun commands(): List<WireCommand> = ArrayList(commands)
}


data class SignedWireTransaction(val txBits: ByteArray, val sigs: List<DigitalSignature.WithKey>) : SerializeableWithKryo {
    init {
        check(sigs.isNotEmpty())
    }

    /**
     * Verifies the given signatures against the serialized transaction data. Does NOT deserialise or check the contents
     * to ensure there are no missing signatures: use verify() to do that. This weaker version can be useful for
     * checking a partially signed transaction being prepared by multiple co-operating parties.
     *
     * @throws SignatureException if the signature is invalid or does not match.
     */
    fun verifySignatures() {
        for (sig in sigs)
            sig.verifyWithECDSA(txBits)
    }

    /**
     * Verify the signatures, deserialise the wire transaction and then check that the set of signatures found matches
     * the set of pubkeys in the commands.
     *
     * @throws SignatureException if the signature is invalid or does not match.
     */
    fun verify(): WireTransaction {
        verifySignatures()
        val wtx = txBits.deserialize<WireTransaction>()
        // Verify that every command key was in the set that we just verified: there should be no commands that were
        // unverified.
        val cmdKeys = wtx.commands.flatMap { it.pubkeys }.toSet()
        val sigKeys = sigs.map { it.by }.toSet()
        if (cmdKeys != sigKeys)
            throw SignatureException("Command keys don't match the signatures: $cmdKeys vs $sigKeys")
        return wtx
    }
}

// Not used yet.
data class TimestampedWireTransaction(
    /** A serialised SignedWireTransaction */
    val wireTX: ByteArray,

    /** Signature from a timestamping authority. For instance using RFC 3161 */
    val timestamp: ByteArray
) : SerializeableWithKryo

/**
 * A LedgerTransaction wraps the data needed to calculate one or more successor states from a set of input states.
 * It is the first step after extraction from a WireTransaction. The signatures at this point have been lined up
 * with the commands from the wire, and verified/looked up.
 *
 * Not used yet.
 *
 * TODO: When converting LedgerTransaction into TransactionForVerification, make sure to check for duped inputs.
 */
class LedgerTransaction(
    /** The input states which will be consumed/invalidated by the execution of this transaction. */
    val inputStates: List<ContractStateRef>,
    /** The states that will be generated by the execution of this transaction. */
    val outputStates: List<ContractState>,
    /** Arbitrary data passed to the program of each input state. */
    val commands: List<AuthenticatedObject<Command>>,
    /** The moment the transaction was timestamped for */
    val time: Instant
    // TODO: nLockTime equivalent?
)

/** A transaction in fully resolved and sig-checked form, ready for passing as input to a verification function. */
class TransactionForVerification(val inStates: List<ContractState>,
                                 val outStates: List<ContractState>,
                                 val commands: List<AuthenticatedObject<Command>>,
                                 val time: Instant) {

    fun verify(programMap: Map<SecureHash, Contract>) {
        // For each input and output state, locate the program to run. Then execute the verification function. If any
        // throws an exception, the entire transaction is invalid.
        val programHashes = (inStates.map { it.programRef } + outStates.map { it.programRef }).toSet()
        for (hash in programHashes) {
            val program = programMap[hash] ?: throw IllegalStateException("Unknown program hash $hash")
            program.verify(this)
        }
    }

}