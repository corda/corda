package net.corda.core.internal

import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ComponentGroupEnum.ATTACHMENTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.ATTACHMENTS_V2_GROUP
import net.corda.core.contracts.ComponentGroupEnum.COMMANDS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.INPUTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.NOTARY_GROUP
import net.corda.core.contracts.ComponentGroupEnum.OUTPUTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.PARAMETERS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.REFERENCES_GROUP
import net.corda.core.contracts.ComponentGroupEnum.SIGNERS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.TIMEWINDOW_GROUP
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.algorithm
import net.corda.core.crypto.internal.DigestAlgorithmFactory
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.MissingAttachmentsRuntimeException
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.BaseTransaction
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.FilteredComponentGroup
import net.corda.core.transactions.FullTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import kotlin.reflect.KClass

/** Constructs a [NotaryChangeWireTransaction]. */
class NotaryChangeTransactionBuilder(val inputs: List<StateRef>,
                                     val notary: Party,
                                     val newNotary: Party,
                                     val networkParametersHash: SecureHash,
                                     val digestService: DigestService = DigestService.sha2_256) {

    fun build(): NotaryChangeWireTransaction {
        val components = listOf(inputs, notary, newNotary, networkParametersHash).map { it.serialize() }
        return NotaryChangeWireTransaction(components, digestService)
    }
}

/** Constructs a [ContractUpgradeWireTransaction]. */
class ContractUpgradeTransactionBuilder(
        val inputs: List<StateRef>,
        val notary: Party,
        val legacyContractAttachmentId: SecureHash,
        val upgradedContractClassName: ContractClassName,
        val upgradedContractAttachmentId: SecureHash,
        privacySalt: PrivacySalt = PrivacySalt(),
        val networkParametersHash: SecureHash,
        val digestService: DigestService = DigestService.sha2_256) {
    var privacySalt: PrivacySalt = privacySalt
        private set

    fun build(): ContractUpgradeWireTransaction {
        val components = listOf(inputs, notary, legacyContractAttachmentId, upgradedContractClassName, upgradedContractAttachmentId, networkParametersHash).map { it.serialize() }
        return ContractUpgradeWireTransaction(components, privacySalt, digestService)
    }
}

/** Concatenates the hash components into a single [ByteArray] and returns its hash. */
fun combinedHash(components: Iterable<SecureHash>, digestService: DigestService): SecureHash {
    val stream = ByteArrayOutputStream()
    components.forEach {
        stream.write(it.bytes)
    }
    return digestService.hash(stream.toByteArray())
}

/**
 * This function knows how to deserialize a transaction component group.
 *
 * In case the [componentGroups] is an instance of [LazyMappedList], this function will just use the original deserialized version, and avoid an unnecessary deserialization.
 * The [forceDeserialize] will force deserialization. In can be used in case the SerializationContext changes.
 */
fun <T : Any> deserialiseComponentGroup(componentGroups: List<ComponentGroup>,
                                        clazz: KClass<T>,
                                        groupEnum: ComponentGroupEnum,
                                        forceDeserialize: Boolean = false,
                                        factory: SerializationFactory = SerializationFactory.defaultFactory,
                                        context: SerializationContext = factory.defaultContext): List<T> {
    val group = componentGroups.getGroup(groupEnum)
    if (group == null || group.components.isEmpty()) {
        return emptyList()
    }

    // If the componentGroup is a [LazyMappedList] it means that the original deserialized version is already available.
    val components = group.components
    if (!forceDeserialize && components is LazyMappedList<*, OpaqueBytes>) {
        return uncheckedCast(components.originalList)
    }

    return components.lazyMapped { component, internalIndex ->
        try {
            factory.deserialize(component, clazz.java, context)
        } catch (e: MissingAttachmentsException) {
            /**
             * `ServiceHub.signInitialTransaction` forgets to declare that
             * it may throw any checked exceptions. Wrap this one inside
             * an unchecked version to avoid breaking Java CorDapps.
             */
            throw MissingAttachmentsRuntimeException(e.ids, e.message, e)
        } catch (e: Exception) {
            throw TransactionDeserialisationException(groupEnum, internalIndex, e)
        }
    }
}

fun <T : ComponentGroup> List<T>.getGroup(type: ComponentGroupEnum): T? = firstOrNull { it.groupIndex == type.ordinal }

fun <T : ComponentGroup> List<T>.getRequiredGroup(type: ComponentGroupEnum): T {
    return requireNotNull(getGroup(type)) { "Missing component group $type" }
}

/**x
 * Exception raised if an error was encountered while attempting to deserialise a component group in a transaction.
 */
class TransactionDeserialisationException(groupEnum: ComponentGroupEnum, index: Int, cause: Exception):
        RuntimeException("Failed to deserialise group $groupEnum at index $index in transaction: ${cause.message}", cause)

/**
 * Method to deserialise Commands from its two groups:
 *  * COMMANDS_GROUP which contains the CommandData part
 *  * and SIGNERS_GROUP which contains the Signers part.
 *
 *  This method used the [deserialiseComponentGroup] method.
 */
fun deserialiseCommands(
        componentGroups: List<ComponentGroup>,
        forceDeserialize: Boolean = false,
        factory: SerializationFactory = SerializationFactory.defaultFactory,
        context: SerializationContext = factory.defaultContext,
        digestService: DigestService = DigestService.sha2_256
): List<Command<*>> {
    // TODO: we could avoid deserialising unrelated signers.
    //      However, current approach ensures the transaction is not malformed
    //      and it will throw if any of the signers objects is not List of public keys).
    val signersList: List<List<PublicKey>> = uncheckedCast(deserialiseComponentGroup(componentGroups, List::class, SIGNERS_GROUP, forceDeserialize, factory, context))
    val commandDataList: List<CommandData> = deserialiseComponentGroup(componentGroups, CommandData::class, COMMANDS_GROUP, forceDeserialize, factory, context)
    val group = componentGroups.getGroup(COMMANDS_GROUP)
    return if (group is FilteredComponentGroup) {
        check(commandDataList.size <= signersList.size) {
            "Invalid Transaction. Less Signers (${signersList.size}) than CommandData (${commandDataList.size}) objects"
        }
        val componentHashes = group.components.mapIndexed { index, component -> digestService.componentHash(group.nonces[index], component) }
        val leafIndices = componentHashes.map { group.partialMerkleTree.leafIndex(it) }
        if (leafIndices.isNotEmpty())
            check(leafIndices.max()!! < signersList.size) { "Invalid Transaction. A command with no corresponding signer detected" }
        commandDataList.lazyMapped { commandData, index -> Command(commandData, signersList[leafIndices[index]]) }
    } else {
        // It is a WireTransaction
        // or a FilteredTransaction with no Commands (in which case group is null).
        check(commandDataList.size == signersList.size) {
            "Invalid Transaction. Sizes of CommandData (${commandDataList.size}) and Signers (${signersList.size}) do not match"
        }
        commandDataList.lazyMapped { commandData, index -> Command(commandData, signersList[index]) }
    }
}

@Suppress("LongParameterList")
fun createComponentGroups(inputs: List<StateRef>,
                          outputs: List<TransactionState<ContractState>>,
                          commands: List<Command<*>>,
                          attachments: List<SecureHash>,
                          notary: Party?,
                          timeWindow: TimeWindow?,
                          references: List<StateRef>,
                          networkParametersHash: SecureHash?,
                          // The old attachments group is now only used to create transaction compatible with 4.11 (or earlier) nodes
                          legacyAttachments: List<SecureHash> = emptyList()): List<ComponentGroup> {
    val serializationFactory = SerializationFactory.defaultFactory
    val serializationContext = serializationFactory.defaultContext
    val serialize = { value: Any, _: Int -> value.serialize(serializationFactory, serializationContext) }
    val componentGroupMap: MutableList<ComponentGroup> = mutableListOf()
    componentGroupMap.addListGroup(INPUTS_GROUP, inputs, serialize)
    componentGroupMap.addListGroup(REFERENCES_GROUP, references, serialize)
    componentGroupMap.addListGroup(OUTPUTS_GROUP, outputs, serialize)
    // Adding commandData only to the commands group. Signers are added in their own group.
    componentGroupMap.addListGroup(COMMANDS_GROUP, commands.map { it.value }, serialize)
    // Attachments which can only be processed by 4.12 and later.
    componentGroupMap.addListGroup(ATTACHMENTS_V2_GROUP, attachments, serialize)
    // The original attachments group now only contains attachments which can be processed by 4.11 and earlier (and the external verifier).
    componentGroupMap.addListGroup(ATTACHMENTS_GROUP, legacyAttachments, serialize)
    if (notary != null) componentGroupMap.add(ComponentGroup(NOTARY_GROUP.ordinal, listOf(notary).lazyMapped(serialize)))
    if (timeWindow != null) componentGroupMap.add(ComponentGroup(TIMEWINDOW_GROUP.ordinal, listOf(timeWindow).lazyMapped(serialize)))
    // Adding signers to their own group. This is required for command visibility purposes: a party receiving
    // a FilteredTransaction can now verify it sees all the commands it should sign.
    componentGroupMap.addListGroup(SIGNERS_GROUP, commands.map { it.signers }, serialize)
    if (networkParametersHash != null) componentGroupMap.add(ComponentGroup(PARAMETERS_GROUP.ordinal, listOf(networkParametersHash.serialize())))
    return componentGroupMap
}

private fun MutableList<ComponentGroup>.addListGroup(type: ComponentGroupEnum, list: List<Any>, serialize: (Any, Int) -> SerializedBytes<Any>) {
    if (list.isNotEmpty()) {
        add(ComponentGroup(type.ordinal, list.lazyMapped(serialize)))
    }
}

typealias SerializedTransactionState = SerializedBytes<TransactionState<ContractState>>

/**
 * A SerializedStateAndRef is a pair (BinaryStateRepresentation, StateRef).
 * The [serializedState] is the actual component from the original wire transaction.
 */
data class SerializedStateAndRef(val serializedState: SerializedTransactionState, val ref: StateRef) {
    fun toStateAndRef(factory: SerializationFactory, context: SerializationContext) = StateAndRef(serializedState.deserialize(factory, context), ref)
    fun toStateAndRef(): StateAndRef<ContractState> {
        val factory = SerializationFactory.defaultFactory
        return toStateAndRef(factory, factory.defaultContext)
    }
}

/** Check that network parameters hash on this transaction is the current hash for the network. */
fun FlowLogic<*>.checkParameterHash(networkParametersHash: SecureHash?) {
    // Transactions created on Corda 3.x or below do not contain network parameters,
    // so no checking is done until the minimum platform version is at least 4.
    if (networkParametersHash == null) {
        if (serviceHub.networkParameters.minimumPlatformVersion < PlatformVersionSwitches.NETWORK_PARAMETERS_COMPONENT_GROUP) return
        else throw IllegalArgumentException("Transaction for notarisation doesn't contain network parameters hash.")
    } else {
        serviceHub.networkParametersService.lookup(networkParametersHash) ?: throw IllegalArgumentException("Transaction for notarisation contains unknown parameters hash: $networkParametersHash")
    }

    // TODO: [ENT-2666] Implement network parameters fuzzy checking. By design in Corda network we have propagation time delay.
    //       We will never end up in perfect synchronization with all the nodes. However, network parameters update process
    //       lets us predict what is the reasonable time window for changing parameters on most of the nodes.
    //       For now we don't check whether the attached network parameters match the current ones.
}

val SignedTransaction.dependencies: Set<SecureHash>
    get() = (inputs.asSequence() + references.asSequence()).map { it.txhash }.toSet()

class HashAgility {
    companion object {
        @Volatile
        internal var digestService = DigestService.sha2_256
            private set

        fun init(txHashAlgoName: String? = null, txHashAlgoClass: String? = null) {
            digestService = DigestService.sha2_256
            txHashAlgoName?.let {
                // Verify that algorithm exists.
                DigestAlgorithmFactory.create(it)
                digestService = DigestService(it)
            }
            txHashAlgoClass?.let {
                val algorithm = DigestAlgorithmFactory.registerClass(it)
                digestService = DigestService(algorithm)
            }
        }

        internal fun isAlgorithmSupported(algorithm: String): Boolean {
            return algorithm == SecureHash.SHA2_256 || algorithm == digestService.hashAlgorithm
        }
    }
}

/**
 * The configured instance of DigestService which is passed by default to instances of classes like TransactionBuilder
 * and as a parameter to MerkleTree.getMerkleTree(...) method. Default: SHA2_256.
 */
val ServicesForResolution.digestService get() = HashAgility.digestService

fun ServicesForResolution.requireSupportedHashType(hash: NamedByHash) {
    require(HashAgility.isAlgorithmSupported(hash.id.algorithm)) {
        "Tried to record a transaction with non-standard hash algorithm ${hash.id.algorithm} (experimental mode off)"
    }
}

internal fun BaseTransaction.checkSupportedHashType() {
    if (!HashAgility.isAlgorithmSupported(id.algorithm)) {
        throw TransactionVerificationException.UnsupportedHashTypeException(id)
    }
}

/** Make sure the assigned notary is part of the network parameter whitelist. */
internal fun checkNotaryWhitelisted(ftx: FullTransaction) {
    ftx.notary?.let { notaryParty ->
        // Network parameters will never be null if the transaction is resolved from a CoreTransaction rather than constructed directly.
        ftx.networkParameters?.let { parameters ->
            val notaryWhitelist = parameters.notaries.map { it.identity }
            // Transaction can combine different identities of the same notary after key rotation.
            // Each of these identities should be whitelisted.
            val notaries = setOf(notaryParty) + (ftx.inputs + ftx.references).map { it.state.notary }
            notaries.forEach {
                check(it in notaryWhitelist) {
                    "Notary [${it.description()}] specified by the transaction is not on the network parameter whitelist: " +
                            " [${notaryWhitelist.joinToString { party -> party.description() }}]"
                }
            }
        }
    }
}
