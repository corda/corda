/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

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
 * WireTransaction is a transaction in a form ready to be serialised/unserialised. A WireTransaction can be hashed
 * in various ways to calculate a *signature hash* (or sighash), this is the hash that is signed by the various involved
 * keypairs. Note that a sighash is not the same thing as a *transaction id*, which is the hash of a
 * TimestampedWireTransaction i.e. the outermost serialised form with everything included.
 *
 * A PartialTransaction is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands or modifying the existing set.
 * Then once the states and commands are right, this class can be used as a holding bucket to gather signatures from
 * multiple parties.
 *
 * LedgerTransaction is derived from WireTransaction and TimestampedWireTransaction together. It is the result of
 * doing some basic key lookups on WireCommand to see if any keys are from a recognised party, thus converting
 * the WireCommand objects into AuthenticatedObject<Command>. Currently we just assume a hard coded pubkey->party
 * map. In future it'd make more sense to use a certificate scheme and so that logic would get more complex.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * TransactionForVerification is the same as LedgerTransaction but with the input states looked up from a local
 * database and replaced with the real objects. TFV is the form that is finally fed into the contracts.
 */

/** Serialized command plus pubkey pair: the signature is stored at the end of the serialized bytes */
data class WireCommand(val command: Command, val pubkeys: List<PublicKey>) : SerializeableWithKryo {
    constructor(command: Command, key: PublicKey) : this(command, listOf(key))
}

/** Transaction ready for serialisation, without any signatures attached. */
data class WireTransaction(val inputStates: List<ContractStateRef>,
                           val outputStates: List<ContractState>,
                           val commands: List<WireCommand>) : SerializeableWithKryo {
    fun serializeForSignature(): ByteArray = serialize()

    fun toLedgerTransaction(timestamp: Instant?, partyKeyMap: Map<PublicKey, Party>, originalHash: SecureHash): LedgerTransaction {
        val authenticatedArgs = commands.map {
            val institutions = it.pubkeys.mapNotNull { pk -> partyKeyMap[pk] }
            AuthenticatedObject(it.pubkeys, institutions, it.command)
        }
        return LedgerTransaction(inputStates, outputStates, authenticatedArgs, timestamp, originalHash)
    }
}

/** A mutable transaction that's in the process of being built, before all signatures are present. */
class PartialTransaction(private val inputStates: MutableList<ContractStateRef> = arrayListOf(),
                         private val outputStates: MutableList<ContractState> = arrayListOf(),
                         private val commands: MutableList<WireCommand> = arrayListOf()) {

    /**  A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    constructor(vararg items: Any) : this() {
        addItems(*items)
    }

    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    public fun addItems(vararg items: Any) {
        for (t in items) {
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
        val bits = toWireTransaction().serializeForSignature()
        currentSigs.add(key.private.signWithECDSA(bits, key.public))
    }

    fun toWireTransaction() = WireTransaction(inputStates, outputStates, commands)

    fun toSignedTransaction(checkSufficientSignatures: Boolean = true): SignedWireTransaction {
        if (checkSufficientSignatures) {
            val requiredKeys = commands.flatMap { it.pubkeys }.toSet()
            val gotKeys = currentSigs.map { it.by }.toSet()
            check(gotKeys == requiredKeys) { "The set of required signatures isn't equal to the signatures we've got" }
        }
        return SignedWireTransaction(toWireTransaction().serialize().opaque(), ArrayList(currentSigs))
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

        // We should probably merge the lists of pubkeys for identical commands here.
        commands.add(arg)
    }

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<ContractStateRef> = ArrayList(inputStates)
    fun outputStates(): List<ContractState> = ArrayList(outputStates)
    fun commands(): List<WireCommand> = ArrayList(commands)
}

/**
 * Simple interface (for testing) to an abstract timestamping service, in the style of RFC 3161. Note that this is not
 * 'timestamping' in the block chain sense, but rather, implies a semi-trusted third party taking a reading of the
 * current time, typically from an atomic clock, and then digitally signing (current time, hash) to produce a timestamp
 * triple (signature, time, hash). The purpose of these timestamps is to locate a transaction in the timeline, which is
 * important in the absence of blocks. Here we model the timestamp as an opaque byte array.
 */
interface TimestamperService {
    fun timestamp(hash: SecureHash): ByteArray
    fun verifyTimestamp(hash: SecureHash, signedTimestamp: ByteArray): Instant
}

data class SignedWireTransaction(val txBits: OpaqueBytes, val sigs: List<DigitalSignature.WithKey>) : SerializeableWithKryo {
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
            sig.verifyWithECDSA(txBits.bits)
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

    /** Uses the given timestamper service to calculate a signed timestamp and then returns a wrapper for both */
    fun toTimestampedTransaction(timestamper: TimestamperService): TimestampedWireTransaction {
        val bits = serialize()
        return TimestampedWireTransaction(bits.opaque(), timestamper.timestamp(bits.sha256()).opaque())
    }

    /** Returns a [TimestampedWireTransaction] with an empty byte array as the timestamp: this means, no time was provided. */
    fun toTimestampedTransactionWithoutTime() = TimestampedWireTransaction(serialize().opaque(), null)
}

/**
 * A TimestampedWireTransaction is the outermost, final form that a transaction takes. The hash of this structure is
 * how transactions are identified on the network and in the ledger.
 */
data class TimestampedWireTransaction(
    /** A serialised SignedWireTransaction */
    val signedWireTX: OpaqueBytes,

    /** Signature from a timestamping authority. For instance using RFC 3161 */
    val timestamp: OpaqueBytes?
) : SerializeableWithKryo {
    val transactionID: SecureHash = serialize().sha256()

    fun verifyToLedgerTransaction(timestamper: TimestamperService, partyKeyMap: Map<PublicKey, Party>): LedgerTransaction {
        val stx: SignedWireTransaction = signedWireTX.deserialize()
        val wtx: WireTransaction = stx.verify()
        val instant: Instant? = if (timestamp != null) timestamper.verifyTimestamp(signedWireTX.sha256(), timestamp.bits) else null
        return wtx.toLedgerTransaction(instant, partyKeyMap, transactionID)
    }
}

/**
 * A LedgerTransaction wraps the data needed to calculate one or more successor states from a set of input states.
 * It is the first step after extraction from a WireTransaction. The signatures at this point have been lined up
 * with the commands from the wire, and verified/looked up.
 */
data class LedgerTransaction(
    /** The input states which will be consumed/invalidated by the execution of this transaction. */
    val inStateRefs: List<ContractStateRef>,
    /** The states that will be generated by the execution of this transaction. */
    val outStates: List<ContractState>,
    /** Arbitrary data passed to the program of each input state. */
    val commands: List<AuthenticatedObject<Command>>,
    /** The moment the transaction was timestamped for, if a timestamp was present. */
    val time: Instant?,
    /** The hash of the original serialised TimestampedWireTransaction or SignedTransaction */
    val hash: SecureHash
    // TODO: nLockTime equivalent?
) {
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int) = StateAndRef(outStates[index] as T, ContractStateRef(hash, index))

    fun <T : ContractState> outRef(state: T): StateAndRef<T> {
        val i = outStates.indexOf(state)
        if (i == -1)
            throw IllegalArgumentException("State not found in this transaction")
        return outRef(i)
    }
}

/** A transaction in fully resolved and sig-checked form, ready for passing as input to a verification function. */
data class TransactionForVerification(val inStates: List<ContractState>,
                                      val outStates: List<ContractState>,
                                      val commands: List<AuthenticatedObject<Command>>,
                                      val time: Instant?,
                                      val origHash: SecureHash) {
    override fun hashCode() = origHash.hashCode()
    override fun equals(other: Any?) = other is TransactionForVerification && other.origHash == origHash

    /**
     * @throws TransactionVerificationException if a contract throws an exception, the original is in the cause field
     * @throws IllegalStateException if a state refers to an unknown contract.
     */
    @Throws(TransactionVerificationException::class, IllegalStateException::class)
    fun verify(programMap: Map<SecureHash, Contract>) {
        // For each input and output state, locate the program to run. Then execute the verification function. If any
        // throws an exception, the entire transaction is invalid.
        val programHashes = (inStates.map { it.programRef } + outStates.map { it.programRef }).toSet()
        for (hash in programHashes) {
            val program = programMap[hash] ?: throw IllegalStateException("Unknown program hash $hash")
            try {
                program.verify(this)
            } catch(e: Throwable) {
                throw TransactionVerificationException(this, program, e)
            }
        }
    }

    /**
     * Utilities for contract writers to incorporate into their logic.
     */

    data class InOutGroup<T : ContractState>(val inputs: List<T>, val outputs: List<T>)

    // For Java users.
    fun <T : ContractState> groupStates(ofType: Class<T>, selector: (T) -> Any): List<InOutGroup<T>> {
        val inputs = inStates.filterIsInstance(ofType)
        val outputs = outStates.filterIsInstance(ofType)

        val inGroups = inputs.groupBy(selector)
        val outGroups = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    // For Kotlin users: this version has nicer syntax and avoids reflection/object creation for the lambda.
    inline fun <reified T : ContractState> groupStates(selector: (T) -> Any): List<InOutGroup<T>> {
        val inputs = inStates.filterIsInstance<T>()
        val outputs = outStates.filterIsInstance<T>()

        val inGroups = inputs.groupBy(selector)
        val outGroups = outputs.groupBy(selector)

        @Suppress("DEPRECATION")
        return groupStatesInternal(inGroups, outGroups)
    }

    @Deprecated("Do not use this directly: exposed as public only due to function inlining")
    fun <T : ContractState> groupStatesInternal(inGroups: Map<Any, List<T>>, outGroups: Map<Any, List<T>>): List<InOutGroup<T>> {
        val result = ArrayList<InOutGroup<T>>()

        for ((k, v) in inGroups.entries)
            result.add(InOutGroup(v, outGroups[k] ?: emptyList()))
        for ((k, v) in outGroups.entries) {
            if (inGroups[k] == null)
                result.add(InOutGroup(emptyList(), v))
        }

        return result
    }
}

/** Thrown if a verification fails due to a contract rejection. */
class TransactionVerificationException(val tx: TransactionForVerification, val contract: Contract, cause: Throwable?) : Exception(cause)