package net.corda.node.migration

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.deserialiseComponentGroup
import net.corda.core.internal.div
import net.corda.core.internal.readObject
import net.corda.core.internal.services.StateResolutionSupport
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.AttachmentsClassLoaderBuilder
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.serialization.internal.AttachmentsClassLoaderCacheImpl
import net.corda.core.transactions.BaseTransaction
import net.corda.core.transactions.ContractUpgradeLedgerTransaction
import net.corda.core.transactions.NotaryChangeLedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.services.attachments.NodeAttachmentTrustCalculator
import net.corda.node.services.persistence.AttachmentStorageInternal
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.SchemaMigration
import java.nio.file.Paths
import java.time.Clock
import java.time.Duration
import java.util.Comparator.comparingInt

@Suppress("TooGenericExceptionCaught")
class MigrationResolution(
        val attachments: AttachmentStorageInternal,
        private val transactions: TransactionStorage,
        private val cordaDB: CordaPersistence,
        cacheFactory: MigrationNamedCacheFactory
): StateResolutionSupport {
    companion object {
        val logger = contextLogger()
    }

    private val cordappLoader = SchemaMigration.loader.get()

    private val attachmentTrustCalculator = NodeAttachmentTrustCalculator(
        attachments,
        cacheFactory
    )

    private val attachmentsClassLoaderCache: AttachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(cacheFactory)

    override val appClassLoader: ClassLoader get() = cordappLoader.appClassLoader

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

    private fun getNetworkParametersFromFile(): SignedNetworkParameters? {
        return try {
            val dir = System.getProperty(SchemaMigration.NODE_BASE_DIR_KEY)
            val path = Paths.get(dir) / NETWORK_PARAMS_FILE_NAME
            path.readObject()
        } catch (e: Exception) {
            logger.info("Couldn't find network parameters file: ${e.message}. This is expected if the node is starting for the first time.")
            null
        }
    }

    private val networkParametersService: NetworkParametersService = object : NetworkParametersService {

        private val storage = DBNetworkParametersStorage.createParametersMap(cacheFactory)

        private val filedParams = getNetworkParametersFromFile()

        override val defaultHash: SecureHash = filedParams?.raw?.hash ?: SecureHash.getZeroHash()
        override val currentHash: SecureHash = cordaDB.transaction {
            storage.allPersisted.use {
                it.max(comparingInt { it.second.verified().epoch }).map { it.first }.orElse(defaultHash)
            }
        }

        override fun lookup(hash: SecureHash): NetworkParameters? {
            // Note that the parameters in any file shouldn't be put into the database - this will be done by the node on startup.
            return if (hash == filedParams?.raw?.hash) {
                filedParams.raw.deserialize()
            } else {
                cordaDB.transaction { storage[hash]?.verified() }
            }
        }
    }

    private val networkParameters: NetworkParameters = networkParametersService.lookup(networkParametersService.currentHash)
            ?: getNetworkParametersFromFile()?.raw?.deserialize()
            ?: defaultNetworkParameters()

    private fun extractStateFromTx(tx: WireTransaction, stateIndices: Collection<Int>): List<TransactionState<ContractState>> {
        return try {
            val txAttachments = tx.attachments.mapNotNull { attachments.openAttachment(it)}
            val states = AttachmentsClassLoaderBuilder.withAttachmentsClassloaderContext(
                    txAttachments,
                    networkParameters,
                    tx.id,
                    attachmentTrustCalculator::calculate,
                    cordappLoader.appClassLoader,
                    attachmentsClassLoaderCache) {
                deserialiseComponentGroup(tx.componentGroups, TransactionState::class, ComponentGroupEnum.OUTPUTS_GROUP, forceDeserialize = true)
            }
            states.filterIndexed {index, _ -> stateIndices.contains(index)}.toList()
        } catch (e: Exception) {
            // If there is no attachment that allows the state class to be deserialised correctly, then carpent a state class anyway. It
            // might still be possible to access the participants depending on how the state class was serialised.
            logger.debug { "Could not use attachments to deserialise transaction output states for transaction ${tx.id}" }
            tx.outputs.filterIndexed { index, _ -> stateIndices.contains(index)}
        }
    }

    override fun getSignedTransaction(id: SecureHash): SignedTransaction? = transactions.getTransaction(id)

    override fun getNetworkParameters(id: SecureHash?): NetworkParameters? {
        return networkParametersService.lookup(id ?: networkParametersService.defaultHash)
    }

    override fun getAttachment(id: SecureHash): Attachment? = attachments.openAttachment(id)

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = transactions.getTransaction(stateRef.txhash)
                ?: throw MigrationException("Could not get transaction with hash ${stateRef.txhash} out of vault")
        val baseTx = BaseTransaction.resolve(stx, this)
        return when (baseTx) {
            is NotaryChangeLedgerTransaction -> baseTx.outputs[stateRef.index]
            is ContractUpgradeLedgerTransaction -> baseTx.outputs[stateRef.index]
            is WireTransaction -> extractStateFromTx(baseTx, listOf(stateRef.index)).first()
            else -> throw MigrationException("Unknown transaction type ${baseTx::class.qualifiedName} found when loading a state")
        }
    }
}
