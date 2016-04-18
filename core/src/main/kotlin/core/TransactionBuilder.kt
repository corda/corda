package core

import co.paralleluniverse.fibers.Suspendable
import core.crypto.DigitalSignature
import core.crypto.SecureHash
import core.crypto.signWithECDSA
import core.node.services.TimestamperService
import core.node.services.TimestampingError
import core.serialization.serialize
import java.security.KeyPair
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * A TransactionBuilder is a transaction class that's mutable (unlike the others which are all immutable). It is
 * intended to be passed around contracts that may edit it by adding new states/commands or modifying the existing set.
 * Then once the states and commands are right, this class can be used as a holding bucket to gather signatures from
 * multiple parties.
 */
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
        addSignatureUnchecked(key.signWithECDSA(data.bits))
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx, then
     * adds it.
     *
     * @throws SignatureException if the signature didn't match the transaction contents
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkAndAddSignature(sig: DigitalSignature.WithKey) {
        checkSignature(sig)
        addSignatureUnchecked(sig)
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx.
     *
     * @throws SignatureException if the signature didn't match the transaction contents
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkSignature(sig: DigitalSignature.WithKey) {
        require(commands.count { it.pubkeys.contains(sig.by) } > 0) { "Signature key doesn't match any command" }
        sig.verifyWithECDSA(toWireTransaction().serialized)
    }

    /** Adds the signature directly to the transaction, without checking it for validity. */
    fun addSignatureUnchecked(sig: DigitalSignature.WithKey) {
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
        addSignatureUnchecked(sig)
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