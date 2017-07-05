package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.security.PublicKey
import java.security.SignatureException

interface TransactionBase : NamedByHash {
    /** The inputs of this transaction. Note that in BaseTransaction subclasses the type of this list may change! */
    val inputs: List<*>

    /**
     * If present, the notary for this transaction. If absent then the transaction is not notarised at all.
     * This is intended for issuance/genesis transactions that don't consume any other states and thus can't
     * double spend anything.
     */
    val notary: Party?
}

abstract class AbstractCoreTransaction : TransactionBase {
    override val id: SecureHash get() = merkleTree.hash

    val merkleTree: MerkleTree by lazy { MerkleTree.getMerkleTree(availableComponentHashes) }

    /**
     * Calculate the hashes of the sub-components of the transaction, that are used to build its Merkle tree.
     * The root of the tree is the transaction identifier. The tree structure is helpful for privacy, please
     * see the user-guide section "Transaction tear-offs" to learn more about this topic.
     */
    private val availableComponentHashes: List<SecureHash> get() = availableComponents.map { serializedHash(it) }

    protected abstract val availableComponents: List<Any>

    abstract fun resolveFullTransaction(services: ServiceHub, sigs: List<DigitalSignature.WithKey>): AbstractFullTransaction
}

abstract class AbstractFullTransaction : TransactionBase {
    protected abstract var signatures: List<DigitalSignature.WithKey>
    abstract val requiredSigningKeys: Set<PublicKey>
    abstract val outputs: List<TransactionState<ContractState>>

    abstract fun verify()

    protected abstract val coreTransaction: AbstractCoreTransaction

    override val id: SecureHash get() = coreTransaction.id

    val sigs: List<DigitalSignature.WithKey> get() = signatures

    fun addSignature(vararg signature: DigitalSignature.WithKey) {
        signatures += signature
    }

    fun verifySignatures(vararg allowedToBeMissing: PublicKey) {
        checkSignaturesAreValid()

        val missing = getMissingSignatures()
        if (missing.isNotEmpty()) {
            val allowed = allowedToBeMissing.toSet()
            val needed = missing - allowed
            if (needed.isNotEmpty())
                throw SignaturesMissingException(needed, needed.map { it.toString() }, id)
        }
    }

    fun toTransactionAndSignatures(): TransactionForStorage {
        return TransactionForStorage(coreTransaction.serialize(), sigs)
    }

    /**
     * Mathematically validates the signatures that are present on this transaction. This does not imply that
     * the signatures are by the right keys, or that there are sufficient signatures, just that they aren't
     * corrupt. If you use this function directly you'll need to do the other checks yourself. Probably you
     * want [verifySignatures] instead.
     *
     * @throws SignatureException if a signature fails to verify.
     */
    @Throws(SignatureException::class)
    fun checkSignaturesAreValid() {
        for (sig in sigs) {
            sig.verify(id.bytes)
        }
    }

    private fun getMissingSignatures(): Set<PublicKey> {
        val sigKeys = sigs.map { it.by }.toSet()
        // TODO Problem is that we can get single PublicKey wrapped as CompositeKey in allowedToBeMissing/mustSign
        //  equals on CompositeKey won't catch this case (do we want to single PublicKey be equal to the same key wrapped in CompositeKey with threshold 1?)
        val missing = requiredSigningKeys.filter { !it.isFulfilledBy(sigKeys) }.toSet()
        return missing
    }

    @CordaSerializable
    class SignaturesMissingException(val missing: Set<PublicKey>, val descriptions: List<String>, override val id: SecureHash) : NamedByHash, SignatureException() {
        override fun toString(): String {
            return "Missing signatures for $descriptions on transaction ${id.prefixChars()} for ${missing.joinToString()}"
        }
    }
}

data class NotaryChangeCoreTransaction(
        override val inputs: List<StateRef>,
        override val notary: Party,
        val newNotary: Party) : AbstractCoreTransaction() {

    override fun resolveFullTransaction(services: ServiceHub, sigs: List<DigitalSignature.WithKey>): NotaryChangeFullTransaction {
        val resolveStateRef = { state: StateRef -> services.loadState(state) }
        val resolvedInputs = inputs.map { ref ->
            resolveStateRef(ref).let { net.corda.core.contracts.StateAndRef(it, ref) }
        }
        return NotaryChangeFullTransaction(resolvedInputs, notary, newNotary, sigs)
    }

    override val availableComponents: List<Any>
        get() = mutableListOf(inputs).flatten() + listOf(notary, newNotary)
}


data class NotaryChangeFullTransaction(
        override val inputs: List<StateAndRef<*>>,
        override val notary: Party,
        val newNotary: Party,
        override var signatures: List<DigitalSignature.WithKey>
) : AbstractFullTransaction() {

    override val outputs: List<TransactionState<ContractState>>
        get() = inputs.map { it.state.copy(notary = newNotary) }

    override val coreTransaction: AbstractCoreTransaction
        get() {
            return NotaryChangeCoreTransaction(
                    inputs.map { it.ref },
                    notary,
                    newNotary
            )
        }

    override val requiredSigningKeys: Set<PublicKey>
        get() = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet()

    override fun verify() {}

}

data class GeneralCoreTransaction(
        override val inputs: List<StateRef>,
        /** Hashes of the ZIP/JAR files that are needed to interpret the contents of this wire transaction. */
        val attachments: List<SecureHash>,
        val outputs: List<TransactionState<ContractState>>,
        /** Ordered list of ([CommandData], [PublicKey]) pairs that instruct the contracts what to do. */
        val commands: List<Command>,
        val timeWindow: TimeWindow?,
        override val notary: Party?) : AbstractCoreTransaction() {

    override fun resolveFullTransaction(services: ServiceHub, sigs: List<DigitalSignature.WithKey>): FullTransaction {
        val resolveIdentity = { key: PublicKey -> services.identityService.partyFromKey(key) }
        val resolveAttachment = { id: SecureHash -> services.attachments.openAttachment(id) }
        val resolveStateRef = { state: StateRef -> services.loadState(state) }

        // Look up public keys to authenticated identities. This is just a stub placeholder and will all change in future.
        val authenticatedArgs = commands.map {
            val parties = it.signers.mapNotNull { pk -> resolveIdentity(pk) }
            net.corda.core.contracts.AuthenticatedObject(it.signers, parties, it.value)
        }
        // Open attachments specified in this transaction. If we haven't downloaded them, we fail.
        val attachments = attachments.map { resolveAttachment(it) ?: throw net.corda.core.contracts.AttachmentResolutionException(it) }
        val resolvedInputs = inputs.map { ref ->
            resolveStateRef(ref).let { net.corda.core.contracts.StateAndRef(it, ref) }
        }
        return FullTransaction(resolvedInputs, outputs, authenticatedArgs, attachments, timeWindow, notary, sigs)
    }


    override val availableComponents: List<Any>
        get() = mutableListOf(inputs, outputs, attachments, commands).flatten() + listOf(notary, timeWindow).filterNotNull()
}


@CordaSerializable
data class FullTransaction(
        /** The resolved input states which will be consumed/invalidated by the execution of this transaction. */
        override val inputs: List<StateAndRef<*>>,
        override val outputs: List<TransactionState<ContractState>>,

        /** Arbitrary data passed to the program of each input state. */
        val commands: List<AuthenticatedObject<CommandData>>,
        /** A list of [Attachment] objects identified by the transaction that are needed for this transaction to verify. */
        val attachments: List<Attachment>,
        val timeWindow: TimeWindow?,
        override val notary: Party?,
        override var signatures: List<DigitalSignature.WithKey>) : AbstractFullTransaction() {
    init {
        verifyNoNotaryChange()
        verifyEncumbrances()
    }

    override val coreTransaction: GeneralCoreTransaction get() {
        return GeneralCoreTransaction(
                inputs.map { it.ref },
                attachments.map { it.id },
                outputs,
                commands.map { Command(it.value, it.signers) },
                timeWindow,
                notary)
    }

    /** Public keys that need to be fulfilled by signatures in order for the transaction to be valid. */
    override val requiredSigningKeys: Set<PublicKey> get() {
        val commandKeys = commands.flatMap { it.signers }.toSet()
        // TODO: prevent notary field from being set if there are no inputs and no timestamp
        return if (notary != null && (inputs.isNotEmpty() || timeWindow != null)) {
            (commandKeys + notary.owningKey).filterNotNull().toSet()
        } else {
            commandKeys
        }
    }

    override fun verify() {
        verifyEncumbrances()
        verifyNoNotaryChange()
        verifyContracts()
    }

    fun verifyContracts() {
        fun toTransactionForContract(): TransactionForContract {
            return TransactionForContract(inputs.map { it.state.data }, outputs.map { it.data }, attachments, commands, id,
                    inputs.map { it.state.notary }.singleOrNull(), timeWindow)
        }

        val ctx = toTransactionForContract()
        // TODO: This will all be replaced in future once the sandbox and contract constraints work is done.
        val contracts = (ctx.inputs.map { it.contract } + ctx.outputs.map { it.contract }).toSet()
        for (contract in contracts) {
            try {
                contract.verify(ctx)
            } catch(e: Throwable) {
                throw TransactionVerificationException.ContractRejection(id, contract, e)
            }
        }
    }


    /**
     * Make sure the notary has stayed the same. As we can't tell how inputs and outputs connect, if there
     * are any inputs, all outputs must have the same notary.
     *
     * TODO: Is that the correct set of restrictions? May need to come back to this, see if we can be more
     *       flexible on output notaries.
     */
    private fun verifyNoNotaryChange() {
        if (notary != null && inputs.isNotEmpty()) {
            outputs.forEach {
                if (it.notary != notary) {
                    throw TransactionVerificationException.NotaryChangeInWrongTransactionType(id, notary, it.notary)
                }
            }
        }
    }

    private fun verifyEncumbrances() {
        // Validate that all encumbrances exist within the set of input states.
        val encumberedInputs = inputs.filter { it.state.encumbrance != null }
        encumberedInputs.forEach { (state, ref) ->
            val encumbranceStateExists = inputs.any {
                it.ref.txhash == ref.txhash && it.ref.index == state.encumbrance
            }
            if (!encumbranceStateExists) {
                throw TransactionVerificationException.TransactionMissingEncumbranceException(
                        id,
                        state.encumbrance!!,
                        TransactionVerificationException.Direction.INPUT
                )
            }
        }

        // Check that, in the outputs, an encumbered state does not refer to itself as the encumbrance,
        // and that the number of outputs can contain the encumbrance.
        for ((i, output) in outputs.withIndex()) {
            val encumbranceIndex = output.encumbrance ?: continue
            if (encumbranceIndex == i || encumbranceIndex >= outputs.size) {
                throw TransactionVerificationException.TransactionMissingEncumbranceException(
                        id,
                        encumbranceIndex,
                        TransactionVerificationException.Direction.OUTPUT)
            }
        }
    }
}

data class TransactionForStorage(private val txBits: SerializedBytes<AbstractCoreTransaction>,
                                 private val sigs: List<DigitalSignature.WithKey>) {
    fun resolveToFullTransaction(services: ServiceHub) = txBits.deserialize().resolveFullTransaction(services, sigs)
}

