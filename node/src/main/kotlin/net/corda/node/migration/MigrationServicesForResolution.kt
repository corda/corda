package net.corda.node.migration

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.deserialiseComponentGroup
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.transactions.ContractUpgradeLedgerTransaction
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.time.Clock
import java.time.Duration

class MigrationServicesForResolution(
        override val identityService: IdentityService,
        override val attachments: AttachmentStorage,
        private val transactions: TransactionStorage,
        private val cordaDB: CordaPersistence,
        cacheFactory: MigrationNamedCacheFactory
): ServicesForResolution {

    companion object {
        val logger = contextLogger()
    }
    override val cordappProvider: CordappProvider
        get() = throw NotImplementedError()

    private fun defaultNetworkParameters(): NetworkParameters {
        logger.warn("Using a dummy set of network parameters for migration.")
        val clock = Clock.systemUTC()
        return NetworkParameters(
                1,
                listOf(),
                1,
                1,
                clock.instant(),
                1,
                mapOf(),
                Duration.ZERO,
                mapOf()
        )
    }

    override val networkParametersService: NetworkParametersService = object : NetworkParametersService {

        private val storage = DBNetworkParametersStorage.createParametersMap(cacheFactory)

        override val defaultHash: SecureHash = SecureHash.getZeroHash()
        override val currentHash: SecureHash =  cordaDB.transaction {
            storage.allPersisted().maxBy { it.second.verified().epoch }?.first ?: defaultHash
        }

        override fun lookup(hash: SecureHash): NetworkParameters? {
            return cordaDB.transaction { storage[hash]?.verified() }
        }
    }

    override val networkParameters: NetworkParameters = networkParametersService.lookup(networkParametersService.currentHash)
            ?: defaultNetworkParameters()

    private fun extractStateFromTx(tx: WireTransaction, stateIndices: Collection<Int>): List<TransactionState<ContractState>> {
        return try {
            val attachments = tx.attachments.mapNotNull { attachments.openAttachment(it)}
            val states = AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(attachments, networkParameters, tx.id) {
                deserialiseComponentGroup(tx.componentGroups, TransactionState::class, ComponentGroupEnum.OUTPUTS_GROUP, forceDeserialize = true)
            }
            states.filterIndexed {index, _ -> stateIndices.contains(index)}.toList()
        } catch (e: Exception) {
            // If there is no attachment that allows the state class to be deserialised correctly, then carpent a state class anyway. It
            // might still be possible to access the participants depending on how the state class was serialised.
            logger.debug("Could not use attachments to deserialise transaction output states for transaction ${tx.id}")
            tx.outputs.filterIndexed { index, _ -> stateIndices.contains(index)}
        }
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = transactions.getTransaction(stateRef.txhash)
                ?: throw MigrationException("Could not get transaction with hash ${stateRef.txhash} out of vault")
        val baseTx = stx.resolveBaseTransaction(this)
        return when (baseTx) {
            is NotaryChangeLedgerTransaction -> baseTx.outputs[stateRef.index]
            is ContractUpgradeLedgerTransaction -> baseTx.outputs[stateRef.index]
            is WireTransaction -> extractStateFromTx(baseTx, listOf(stateRef.index)).first()
            else -> throw MigrationException("Unknown transaction type ${baseTx::class.qualifiedName} found when loading a state")
        }
    }

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return stateRefs.groupBy { it.txhash }.flatMap {
            val stx = transactions.getTransaction(it.key)
                    ?: throw MigrationException("Could not get transaction with hash ${it.key} out of vault")
            val baseTx = stx.resolveBaseTransaction(this)
            val stateList = when (baseTx) {
                is NotaryChangeLedgerTransaction -> it.value.map { stateRef -> StateAndRef(baseTx.outputs[stateRef.index], stateRef) }
                is ContractUpgradeLedgerTransaction -> it.value.map { stateRef -> StateAndRef(baseTx.outputs[stateRef.index], stateRef) }
                is WireTransaction -> extractStateFromTx(baseTx, it.value.map { stateRef -> stateRef.index })
                        .mapIndexed {index, state -> StateAndRef(state, StateRef(baseTx.id, index)) }
                else -> throw MigrationException("Unknown transaction type ${baseTx::class.qualifiedName} found when loading a state")
            }
            stateList
        }.toSet()
    }

    override fun loadContractAttachment(stateRef: StateRef): Attachment {
        throw NotImplementedError()
    }
}