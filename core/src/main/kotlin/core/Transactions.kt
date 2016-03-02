/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import co.paralleluniverse.fibers.Suspendable
import core.crypto.DigitalSignature
import core.crypto.SecureHash
import core.crypto.signWithECDSA
import core.crypto.toStringShort
import core.node.TimestampingError
import core.serialization.SerializedBytes
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.Emoji
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Views of a transaction as it progresses through the pipeline, from bytes loaded from disk/network to the object
 * tree passed into a contract.
 *
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more ECDSA signatures, each one from
 * a public key that is mentioned inside a transaction command.
 *
 * WireTransaction is a transaction in a form ready to be serialised/unserialised. A WireTransaction can be hashed
 * in various ways to calculate a *signature hash* (or sighash), this is the hash that is signed by the various involved
 * keypairs. Note that a sighash is not the same thing as a *transaction id*, which is the hash of the entire
 * WireTransaction i.e. the outermost serialised form with everything included.
 *
 * A TransactionBuilder is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands or modifying the existing set.
 * Then once the states and commands are right, this class can be used as a holding bucket to gather signatures from
 * multiple parties.
 *
 * LedgerTransaction is derived from WireTransaction. It is the result of doing some basic key lookups on WireCommand
 * to see if any keys are from a recognised party, thus converting the WireCommand objects into
 * AuthenticatedObject<Command>. Currently we just assume a hard coded pubkey->party map. In future it'd make more
 * sense to use a certificate scheme and so that logic would get more complex.
 *
 * All the above refer to inputs using a (txhash, output index) pair.
 *
 * TransactionForVerification is the same as LedgerTransaction but with the input states looked up from a local
 * database and replaced with the real objects. Likewise, attachments are fully resolved at this point.
 * TFV is the form that is finally fed into the contracts.
 */

/** Transaction ready for serialisation, without any signatures attached. */
data class WireTransaction(val inputs: List<StateRef>,
                           val attachments: List<SecureHash>,
                           val outputs: List<ContractState>,
                           val commands: List<Command>) : NamedByHash {

    // Cache the serialised form of the transaction and its hash to give us fast access to it.
    @Volatile @Transient private var cachedBits: SerializedBytes<WireTransaction>? = null
    val serialized: SerializedBytes<WireTransaction> get() = cachedBits ?: serialize().apply { cachedBits = this }
    override val id: SecureHash get() = serialized.hash

    companion object {
        fun deserialize(bits: SerializedBytes<WireTransaction>): WireTransaction {
            val wtx = bits.bits.deserialize<WireTransaction>()
            wtx.cachedBits = bits
            return wtx
        }
    }

    fun toLedgerTransaction(identityService: IdentityService): LedgerTransaction {
        val authenticatedArgs = commands.map {
            val institutions = it.pubkeys.mapNotNull { pk -> identityService.partyFromKey(pk) }
            AuthenticatedObject(it.pubkeys, institutions, it.data)
        }
        return LedgerTransaction(inputs, attachments, outputs, authenticatedArgs, id)
    }

    /** Serialises and returns this transaction as a [SignedTransaction] with no signatures attached. */
    fun toSignedTransaction(withSigs: List<DigitalSignature.WithKey>): SignedTransaction {
        return SignedTransaction(serialized, withSigs)
    }

    /** Returns a [StateAndRef] for the given output index. */
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int): StateAndRef<T> {
        require(index >= 0 && index < outputs.size)
        return StateAndRef(outputs[index] as T, StateRef(id, index))
    }

    /** Returns a [StateAndRef] for the requested output state, or throws [IllegalArgumentException] if not found. */
    fun <T : ContractState> outRef(state: ContractState): StateAndRef<T> = outRef(outputs.indexOfOrThrow(state))

    override fun toString(): String {
        val buf = StringBuilder()
        buf.appendln("Transaction:")
        for (input in inputs) buf.appendln("${Emoji.rightArrow}INPUT:      $input")
        for (output in outputs) buf.appendln("${Emoji.leftArrow}OUTPUT:     $output")
        for (command in commands) buf.appendln("${Emoji.diamond}COMMAND:    $command")
        for (attachment in attachments) buf.appendln("${Emoji.paperclip}ATTACHMENT: $attachment")
        return buf.toString()
    }
}

/** Container for a [WireTransaction] and attached signatures. */
data class SignedTransaction(val txBits: SerializedBytes<WireTransaction>,
                             val sigs: List<DigitalSignature.WithKey>) : NamedByHash {
    init { check(sigs.isNotEmpty()) }

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val tx: WireTransaction by lazy { WireTransaction.deserialize(txBits) }

    /** A transaction ID is the hash of the [WireTransaction]. Thus adding or removing a signature does not change it. */
    override val id: SecureHash get() = txBits.hash

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
     * the set of pubkeys in the commands. If any signatures are missing, either throws an exception (by default) or
     * returns the list of keys that have missing signatures, depending on the parameter.
     *
     * @throws SignatureException if a signature is invalid, does not match or if any signature is missing.
     */
    fun verify(throwIfSignaturesAreMissing: Boolean = true): Set<PublicKey> {
        verifySignatures()
        // Verify that every command key was in the set that we just verified: there should be no commands that were
        // unverified.
        val cmdKeys = tx.commands.flatMap { it.pubkeys }.toSet()
        val sigKeys = sigs.map { it.by }.toSet()
        if (sigKeys == cmdKeys)
            return emptySet()

        val missing = cmdKeys - sigKeys
        if (throwIfSignaturesAreMissing)
            throw SignatureException("Missing signatures on transaction ${id.prefixChars()} for: ${missing.map { it.toStringShort() }}")
        else
            return missing
    }

    /**
     * Calls [verify] to check all required signatures are present, and then uses the passed [IdentityService] to call
     * [WireTransaction.toLedgerTransaction] to look up well known identities from pubkeys.
     */
    fun verifyToLedgerTransaction(identityService: IdentityService): LedgerTransaction {
        verify()
        return tx.toLedgerTransaction(identityService)
    }

    /** Returns the same transaction but with an additional (unchecked) signature */
    fun withAdditionalSignature(sig: DigitalSignature.WithKey) = copy(sigs = sigs + sig)

    /** Alias for [withAdditionalSignature] to let you use Kotlin operator overloading. */
    operator fun plus(sig: DigitalSignature.WithKey) = withAdditionalSignature(sig)
}

/** A mutable transaction that's in the process of being built, before all signatures are present. */
class TransactionBuilder(private val inputs: MutableList<StateRef> = arrayListOf(),
                         private val attachments: MutableList<SecureHash> = arrayListOf(),
                         private val outputs: MutableList<ContractState> = arrayListOf(),
                         private val commands: MutableList<Command> = arrayListOf()) {

    val time: TimestampCommand? get() = commands.mapNotNull { it.data as? TimestampCommand }.singleOrNull()

    /**
     * Places a [TimestampCommand] in this transaction, removing any existing command if there is one.
     * To get the right signature from the timestamping service, use the [timestamp] method after building is
     * finished, or run use the [TimestampingProtocol] yourself.
     *
     * The window of time in which the final timestamp may lie is defined as [time] +/- [timeTolerance].
     * If you want a non-symmetrical time window you must add the command via [addCommand] yourself. The tolerance
     * should be chosen such that your code can finish building the transaction and sending it to the TSA within that
     * window of time, taking into account factors such as network latency. Transactions being built by a group of
     * collaborating parties may therefore require a higher time tolerance than a transaction being built by a single
     * node.
     */
    fun setTime(time: Instant, authenticatedBy: Party, timeTolerance: Duration) {
        check(currentSigs.isEmpty()) { "Cannot change timestamp after signing" }
        commands.removeAll { it.data is TimestampCommand }
        addCommand(TimestampCommand(time, timeTolerance), authenticatedBy.owningKey)
    }

    /** A more convenient way to add items to this transaction that calls the add* methods for you based on type */
    fun withItems(vararg items: Any): TransactionBuilder {
        for (t in items) {
            when (t) {
                is StateRef -> inputs.add(t)
                is ContractState -> outputs.add(t)
                is Command -> commands.add(t)
                else -> throw IllegalArgumentException("Wrong argument type: ${t.javaClass}")
            }
        }
        return this
    }

    /** The signatures that have been collected so far - might be incomplete! */
    private val currentSigs = arrayListOf<DigitalSignature.WithKey>()

    fun signWith(key: KeyPair) {
        check(currentSigs.none { it.by == key.public }) { "This partial transaction was already signed by ${key.public}" }
        check(commands.count { it.pubkeys.contains(key.public) } > 0) { "Trying to sign with a key that isn't in any command" }
        val data = toWireTransaction().serialize()
        currentSigs.add(key.signWithECDSA(data.bits))
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx, then
     * adds it.
     *
     * @throws SignatureException if the signature didn't match the transaction contents
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkAndAddSignature(sig: DigitalSignature.WithKey) {
        require(commands.count { it.pubkeys.contains(sig.by) } > 0) { "Signature key doesn't match any command" }
        sig.verifyWithECDSA(toWireTransaction().serialize())
        currentSigs.add(sig)
    }

    /**
     * Uses the given timestamper service to request a signature over the WireTransaction be added. There must always be
     * at least one such signature, but others may be added as well. You may want to have multiple redundant timestamps
     * in the following cases:
     *
     * - Cross border contracts where local law says that only local timestamping authorities are acceptable.
     * - Backup in case a TSA's signing key is compromised.
     *
     * The signature of the trusted timestamper merely asserts that the time field of this transaction is valid.
     */
    @Suspendable
    fun timestamp(timestamper: TimestamperService, clock: Clock = Clock.systemUTC()) {
        val t = time ?: throw IllegalStateException("Timestamping requested but no time was inserted into the transaction")

        // Obviously this is just a hard-coded dummy value for now.
        val maxExpectedLatency = 5.seconds
        if (Duration.between(clock.instant(), t.before) > maxExpectedLatency)
            throw TimestampingError.NotOnTimeException()

        // The timestamper may also throw NotOnTimeException if our clocks are desynchronised or if we are right on the
        // boundary of t.notAfter and network latency pushes us over the edge. By "synchronised" here we mean relative
        // to GPS time i.e. the United States Naval Observatory.
        val sig = timestamper.timestamp(toWireTransaction().serialize())
        currentSigs.add(sig)
    }

    fun toWireTransaction() = WireTransaction(ArrayList(inputs), ArrayList(attachments),
            ArrayList(outputs), ArrayList(commands))

    fun toSignedTransaction(checkSufficientSignatures: Boolean = true): SignedTransaction {
        if (checkSufficientSignatures) {
            val gotKeys = currentSigs.map { it.by }.toSet()
            for (command in commands) {
                if (!gotKeys.containsAll(command.pubkeys))
                    throw IllegalStateException("Missing signatures on the transaction for a ${command.data.javaClass.canonicalName} command")
            }
        }
        return SignedTransaction(toWireTransaction().serialize(), ArrayList(currentSigs))
    }

    fun addInputState(ref: StateRef) {
        check(currentSigs.isEmpty())
        inputs.add(ref)
    }

    fun addAttachment(attachment: Attachment) {
        check(currentSigs.isEmpty())
        attachments.add(attachment.id)
    }

    fun addOutputState(state: ContractState) {
        check(currentSigs.isEmpty())
        outputs.add(state)
    }

    fun addCommand(arg: Command) {
        check(currentSigs.isEmpty())

        // We should probably merge the lists of pubkeys for identical commands here.
        commands.add(arg)
    }

    fun addCommand(data: CommandData, vararg keys: PublicKey) = addCommand(Command(data, listOf(*keys)))
    fun addCommand(data: CommandData, keys: List<PublicKey>) = addCommand(Command(data, keys))

    // Accessors that yield immutable snapshots.
    fun inputStates(): List<StateRef> = ArrayList(inputs)
    fun outputStates(): List<ContractState> = ArrayList(outputs)
    fun commands(): List<Command> = ArrayList(commands)
    fun attachments(): List<SecureHash> = ArrayList(attachments)
}

/**
 * A LedgerTransaction wraps the data needed to calculate one or more successor states from a set of input states.
 * It is the first step after extraction from a WireTransaction. The signatures at this point have been lined up
 * with the commands from the wire, and verified/looked up.
 */
data class LedgerTransaction(
        /** The input states which will be consumed/invalidated by the execution of this transaction. */
        val inputs: List<StateRef>,
        /** A list of [Attachment] ids that need to be available for this transaction to verify. */
        val attachments: List<SecureHash>,
        /** The states that will be generated by the execution of this transaction. */
        val outputs: List<ContractState>,
        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** The hash of the original serialised WireTransaction */
        val hash: SecureHash
) {
    @Suppress("UNCHECKED_CAST")
    fun <T : ContractState> outRef(index: Int) = StateAndRef(outputs[index] as T, StateRef(hash, index))

    fun toWireTransaction(): WireTransaction {
        val wtx = WireTransaction(inputs, attachments, outputs, commands.map { Command(it.value, it.signers) })
        check(wtx.serialize().hash == hash)
        return wtx
    }

    /**
     * Converts this transaction to [SignedTransaction] form, optionally using the provided keys to sign. There is
     * no requirement that [andSignWithKeys] include all required keys.
     *
     * @throws IllegalArgumentException if a key is provided that isn't listed in any command and [allowUnusedKeys]
     *                                  is false.
     */
    fun toSignedTransaction(andSignWithKeys: List<KeyPair> = emptyList(), allowUnusedKeys: Boolean = false): SignedTransaction {
        val allPubKeys = commands.flatMap { it.signers }.toSet()
        val wtx = toWireTransaction()
        val bits = wtx.serialize()
        val sigs = ArrayList<DigitalSignature.WithKey>()
        for (key in andSignWithKeys) {
            if (!allPubKeys.contains(key.public) && !allowUnusedKeys)
                throw IllegalArgumentException("Key provided that is not listed by any command")
            sigs += key.signWithECDSA(bits)
        }
        return wtx.toSignedTransaction(sigs)
    }
}