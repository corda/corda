package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.componentHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.FilteredComponentGroup
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.lazyMapped
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import kotlin.reflect.KClass

/** Constructs a [NotaryChangeWireTransaction]. */
class NotaryChangeTransactionBuilder(val inputs: List<StateRef>,
                                     val notary: Party,
                                     val newNotary: Party) {
    fun build(): NotaryChangeWireTransaction {
        val components = listOf(inputs, notary, newNotary).map { it.serialize() }
        return NotaryChangeWireTransaction(components)
    }
}

/** Constructs a [ContractUpgradeWireTransaction]. */
class ContractUpgradeTransactionBuilder(
        val inputs: List<StateRef>,
        val notary: Party,
        val legacyContractAttachmentId: SecureHash,
        val upgradedContractClassName: ContractClassName,
        val upgradedContractAttachmentId: SecureHash,
        val privacySalt: PrivacySalt = PrivacySalt()) {
    fun build(): ContractUpgradeWireTransaction {
        val components = listOf(inputs, notary, legacyContractAttachmentId, upgradedContractClassName, upgradedContractAttachmentId).map { it.serialize() }
        return ContractUpgradeWireTransaction(components, privacySalt)
    }
}

/** Concatenates the hash components into a single [ByteArray] and returns its hash. */
fun combinedHash(components: Iterable<SecureHash>): SecureHash {
    val stream = ByteArrayOutputStream()
    components.forEach {
        stream.write(it.bytes)
    }
    return stream.toByteArray().sha256()
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
    val group = componentGroups.firstOrNull { it.groupIndex == groupEnum.ordinal }

    if (group == null || group.components.isEmpty()) {
        return emptyList()
    }

    // If the componentGroup is a [LazyMappedList] it means that the original deserialized version is already available.
    val components = group.components
    if (!forceDeserialize && components is LazyMappedList<*, OpaqueBytes>) {
        return components.originalList as List<T>
    }

    return components.lazyMapped { component, internalIndex ->
        try {
            factory.deserialize(component, clazz.java, context)
        } catch (e: MissingAttachmentsException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Malformed transaction, $groupEnum at index $internalIndex cannot be deserialised", e)
        }
    }
}

/**
 * Method to deserialise Commands from its two groups:
 *  * COMMANDS_GROUP which contains the CommandData part
 *  * and SIGNERS_GROUP which contains the Signers part.
 *
 *  This method used the [deserialiseComponentGroup] method.
 */
fun deserialiseCommands(componentGroups: List<ComponentGroup>,
                        forceDeserialize: Boolean = false,
                        factory: SerializationFactory = SerializationFactory.defaultFactory,
                        context: SerializationContext = factory.defaultContext): List<Command<*>> {
    // TODO: we could avoid deserialising unrelated signers.
    //      However, current approach ensures the transaction is not malformed
    //      and it will throw if any of the signers objects is not List of public keys).
    val signersList: List<List<PublicKey>> = uncheckedCast(deserialiseComponentGroup(componentGroups, List::class, ComponentGroupEnum.SIGNERS_GROUP, forceDeserialize))
    val commandDataList: List<CommandData> = deserialiseComponentGroup(componentGroups, CommandData::class, ComponentGroupEnum.COMMANDS_GROUP, forceDeserialize)
    val group = componentGroups.firstOrNull { it.groupIndex == ComponentGroupEnum.COMMANDS_GROUP.ordinal }
    return if (group is FilteredComponentGroup) {
        check(commandDataList.size <= signersList.size) {
            "Invalid Transaction. Less Signers (${signersList.size}) than CommandData (${commandDataList.size}) objects"
        }
        val componentHashes = group.components.mapIndexed { index, component -> componentHash(group.nonces[index], component) }
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