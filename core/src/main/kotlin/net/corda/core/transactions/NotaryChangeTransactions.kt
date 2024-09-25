package net.corda.core.transactions

import net.corda.core.CordaInternal
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.internal.getRequiredSigningKeysInternal
import net.corda.core.internal.indexOfOrThrow
import net.corda.core.internal.verification.NodeVerificationSupport
import net.corda.core.internal.verification.VerificationResult
import net.corda.core.internal.verification.VerificationSupport
import net.corda.core.internal.verification.toVerifyingServiceHub
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.NotaryChangeWireTransaction.Component.INPUTS
import net.corda.core.transactions.NotaryChangeWireTransaction.Component.NEW_NOTARY
import net.corda.core.transactions.NotaryChangeWireTransaction.Component.NOTARY
import net.corda.core.transactions.NotaryChangeWireTransaction.Component.PARAMETERS_HASH
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.Try
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

/**
 * A special transaction for changing the notary of a state. It only needs specifying the state(s) as input(s),
 * old and new notaries. Output states can be computed by applying the notary modification to corresponding inputs
 * on the fly.
 */
@CordaSerializable
data class NotaryChangeWireTransaction(
        /**
         * Contains all of the transaction components in serialized form.
         * This is used for calculating the transaction id in a deterministic fashion, since re-serializing properties
         * may result in a different byte sequence depending on the serialization context.
         */
        val serializedComponents: List<OpaqueBytes>,
        val digestService: DigestService
) : CoreTransaction() {
    /**
     * Old version of [NotaryChangeWireTransaction] constructor for ABI compatibility.
     */
    @DeprecatedConstructorForDeserialization(1)
    constructor(serializedComponents: List<OpaqueBytes>) : this(serializedComponents, DigestService.sha2_256)

    /**
     * Old version of [NotaryChangeWireTransaction.copy] for ABI compatibility.
     */
    fun copy(serializedComponents: List<OpaqueBytes>): NotaryChangeWireTransaction {
        return NotaryChangeWireTransaction(serializedComponents, DigestService.sha2_256)
    }

    override val inputs: List<StateRef> = serializedComponents[INPUTS.ordinal].deserialize()
    override val references: List<StateRef> = emptyList()
    override val notary: Party = serializedComponents[NOTARY.ordinal].deserialize()

    /** Identity of the notary service to reassign the states to.*/
    val newNotary: Party = serializedComponents[NEW_NOTARY.ordinal].deserialize()

    override val networkParametersHash: SecureHash? by lazy {
        if (serializedComponents.size >= PARAMETERS_HASH.ordinal + 1) {
            serializedComponents[PARAMETERS_HASH.ordinal].deserialize<SecureHash>()
        } else null
    }

    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [NotaryChangeLedgerTransaction] and applying the notary modification to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = throw UnsupportedOperationException("NotaryChangeWireTransaction does not contain output states, " +
                "outputs can only be obtained from a resolved NotaryChangeLedgerTransaction")

    init {
        check(inputs.isNotEmpty()) { "A notary change transaction must have inputs" }
        check(notary != newNotary) { "The old and new notaries must be different – $newNotary" }
        checkBaseInvariants()
    }

    /**
     * A privacy salt is not really required in this case, because we already used nonces in normal transactions and
     * thus input state refs will always be unique. Also, filtering doesn't apply on this type of transactions.
     */
    override val id: SecureHash by lazy {
        serializedComponents.map { component ->
            digestService.hash(component.bytes)
        }.reduce { combinedHash, componentHash ->
            combinedHash.concatenate(componentHash)
        }
    }

    /** Resolves input states and network parameters and builds a [NotaryChangeLedgerTransaction]. */
    fun resolve(services: ServicesForResolution, sigs: List<TransactionSignature>): NotaryChangeLedgerTransaction {
        return NotaryChangeLedgerTransaction.resolve(services.toVerifyingServiceHub(), this, sigs)
    }

    /** Resolves input states and builds a [NotaryChangeLedgerTransaction]. */
    fun resolve(services: ServiceHub, sigs: List<TransactionSignature>): NotaryChangeLedgerTransaction {
        return resolve(services as ServicesForResolution, sigs)
    }

    @CordaInternal
    @JvmSynthetic
    internal fun tryVerify(verificationSupport: NodeVerificationSupport): VerificationResult.InProcess {
        return VerificationResult.InProcess(Try.on {
            // No contract code is run when verifying notary change transactions, it is sufficient to check invariants during initialisation.
            NotaryChangeLedgerTransaction.resolve(verificationSupport, this, emptyList())
            null
        })
    }

    enum class Component {
        INPUTS, NOTARY, NEW_NOTARY, PARAMETERS_HASH
    }

    @Deprecated("Required only for backwards compatibility purposes. This type of transaction should not be constructed outside Corda code.", ReplaceWith("NotaryChangeTransactionBuilder"), DeprecationLevel.WARNING)
    constructor(inputs: List<StateRef>, notary: Party, newNotary: Party) : this(listOf(inputs, notary, newNotary).map { it.serialize() })
}

/**
 * A notary change transaction with fully resolved inputs and signatures. In contrast with a regular transaction,
 * signatures are checked against the signers specified by input states' *participants* fields, so full resolution is
 * needed for signature verification.
 */
class NotaryChangeLedgerTransaction
private constructor(
        override val inputs: List<StateAndRef<ContractState>>,
        override val notary: Party,
        val newNotary: Party,
        override val id: SecureHash,
        override val sigs: List<TransactionSignature>,
        override val networkParameters: NetworkParameters?
) : FullTransaction(), TransactionWithSignatures {
    companion object {
        @CordaInternal
        @JvmSynthetic
        fun resolve(verificationSupport: VerificationSupport,
                    wireTx: NotaryChangeWireTransaction,
                    sigs: List<TransactionSignature>): NotaryChangeLedgerTransaction {
            val inputs = wireTx.inputs.map(verificationSupport::getStateAndRef)
            val networkParameters = verificationSupport.getNetworkParameters(wireTx.networkParametersHash)
                    ?: throw TransactionResolutionException(wireTx.id)
            return NotaryChangeLedgerTransaction(inputs, wireTx.notary, wireTx.newNotary, wireTx.id, sigs, networkParameters)
        }

        @CordaInternal
        @JvmSynthetic
        internal inline fun computeOutput(input: StateAndRef<*>, newNotary: Party, inputs: () -> List<StateRef>): TransactionState<*> {
            val (state, ref) = input
            val newEncumbrance = state.encumbrance?.let {
                val encumbranceStateRef = ref.copy(index = state.encumbrance)
                inputs().indexOfOrThrow(encumbranceStateRef)
            }
            return state.copy(notary = newNotary, encumbrance = newEncumbrance)
        }
    }

    init {
        checkEncumbrances()
        checkNewNotaryWhitelisted()
    }

    /**
     * Check that the output notary is whitelisted.
     *
     * Note that for this transaction type we do not require the input notary to be whitelisted to support network merging.
     * For all other transaction types this is enforced.
     */
    private fun checkNewNotaryWhitelisted() {
        networkParameters?.let { parameters ->
            val notaryWhitelist = parameters.notaries.map { it.identity }
            check(newNotary in notaryWhitelist) {
                "The output notary ${newNotary.description()} is not whitelisted in the attached network parameters."
            }
        }
    }

    override val references: List<StateAndRef<ContractState>> = emptyList()

    /** We compute the outputs on demand by applying the notary field modification to the inputs. */
    override val outputs: List<TransactionState<ContractState>>
        get() = inputs.map { computeOutput(it, newNotary) { inputs.map(StateAndRef<ContractState>::ref) } }

    override val requiredSigningKeys: Set<PublicKey>
        get() = getRequiredSigningKeysInternal(inputs.asSequence(), notary)

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
        return keys.map { it.toBase58String() }
    }

    /**
     * Check that encumbrances have been included in the inputs.
     */
    private fun checkEncumbrances() {
        val encumberedStates = inputs.asSequence().filter { it.state.encumbrance != null }.associateBy { it.ref }
        if (encumberedStates.isNotEmpty()) {
            inputs.forEach { (state, ref) ->
                if (StateRef(ref.txhash, state.encumbrance!!) !in encumberedStates) {
                    throw TransactionVerificationException.TransactionMissingEncumbranceException(
                            id,
                            state.encumbrance,
                            TransactionVerificationException.Direction.INPUT)
                }
            }
        }
    }

    operator fun component1(): List<StateAndRef<ContractState>> = inputs
    operator fun component2(): Party = notary
    operator fun component3(): Party = newNotary
    operator fun component4(): SecureHash = id
    operator fun component5(): List<TransactionSignature> = sigs
    operator fun component6(): NetworkParameters? = networkParameters

    override fun equals(other: Any?): Boolean = this === other || other is NotaryChangeLedgerTransaction && this.id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return """NotaryChangeLedgerTransaction(
            |    id=$id
            |    inputs=$inputs
            |    notary=$notary
            |    newNotary=$newNotary
            |    sigs=$sigs
            |    networkParameters=$networkParameters
            |)""".trimMargin()
    }

    // Things that we can't remove after `data class` removal from this class, so it is deprecated instead.
    //
    @Deprecated("NotaryChangeLedgerTransaction should not be created directly, use NotaryChangeWireTransaction.resolve instead.")
    constructor(
            inputs: List<StateAndRef<ContractState>>,
            notary: Party,
            newNotary: Party,
            id: SecureHash,
            sigs: List<TransactionSignature>
    ) : this(inputs, notary, newNotary, id, sigs, null)

    @Deprecated("NotaryChangeLedgerTransaction should not be created directly, use NotaryChangeWireTransaction.resolve instead.")
    fun copy(inputs: List<StateAndRef<ContractState>> = this.inputs,
             notary: Party = this.notary,
             newNotary: Party = this.newNotary,
             id: SecureHash = this.id,
             sigs: List<TransactionSignature> = this.sigs
    ): NotaryChangeLedgerTransaction {
        return NotaryChangeLedgerTransaction(
                inputs,
                notary,
                newNotary,
                id,
                sigs,
                this.networkParameters
        )
    }
}
