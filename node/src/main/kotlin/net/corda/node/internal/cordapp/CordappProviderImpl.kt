package net.corda.node.internal.cordapp

import com.google.common.collect.HashBiMap
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.createCordappContext
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(private val cordappLoader: CordappLoader,
                               private val cordappConfigProvider: CordappConfigProvider,
                               attachmentStorage: AttachmentStorage,
                               private val whitelistedContractImplementations: Map<String, List<AttachmentId>>) : SingletonSerializeAsToken(), CordappProviderInternal {

    companion object {
        private val log = loggerFor<CordappProviderImpl>()

        private data class CorDappLight(val contractClassNames: Set<String>, val jarPath: URL) {
            constructor(corDapp: Cordapp) : this(corDapp.contractClassNames.toSet(), corDapp.jarPath)
        }
    }

    private val contextCache = ConcurrentHashMap<Cordapp, CordappContext>()

    /**
     * Current known CorDapps loaded on this node
     */
    override val cordapps get() = cordappLoader.cordapps

    /**
     * cordappAttachments.size <= cordapps.size since not all CorDapps have contracts
     */
    private val cordappAttachments = HashBiMap.create(loadContractsIntoAttachmentStore(attachmentStorage))

    init {
        verifyInstalledCordapps(attachmentStorage)
    }

    private fun verifyInstalledCordapps(attachmentStorage: AttachmentStorage) {

        if (whitelistedContractImplementations.isEmpty()) {
            log.warn("The network parameters don't specify any whitelisted contract implementations. Please contact your zone operator. See https://docs.corda.net/network-map.html")
            return
        }

        // Verify that the installed contract classes correspond with the whitelist hash
        // And warn if node is not using latest CorDapp
        cordappAttachments.keys.map(attachmentStorage::openAttachment).mapNotNull { it as? ContractAttachment }.forEach { attch ->
            (attch.allContracts intersect whitelistedContractImplementations.keys).forEach { contractClassName ->
                when {
                    attch.id !in whitelistedContractImplementations[contractClassName]!! -> log.error("Contract $contractClassName found in attachment ${attch.id} is not whitelisted in the network parameters. If this is a production node contact your zone operator. See https://docs.corda.net/network-map.html")
                    attch.id != whitelistedContractImplementations[contractClassName]!!.last() -> log.warn("You are not using the latest CorDapp version for contract: $contractClassName. Please contact your zone operator.")
                }
            }
        }
    }

    override fun getAppContext(): CordappContext {
        // TODO: Use better supported APIs in Java 9
        Exception().stackTrace.forEach { stackFrame ->
            val cordapp = getCordappForClass(stackFrame.className)
            if (cordapp != null) {
                return getAppContext(cordapp)
            }
        }

        throw IllegalStateException("Not in an app context")
    }

    override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? {
        return cordappAttachments.entries.find { it.value.contractClassNames.contains(contractClassName) }?.key
    }

    /**
     * Gets the attachment ID of this CorDapp. Only CorDapps with contracts have an attachment ID
     *
     * @param cordapp The cordapp to get the attachment ID
     * @return An attachment ID if it exists, otherwise nothing
     */
    fun getCordappAttachmentId(cordapp: Cordapp): SecureHash? = cordappAttachments.inverse().get(CorDappLight(cordapp))

    private fun loadContractsIntoAttachmentStore(attachmentStorage: AttachmentStorage): Map<SecureHash, CorDappLight> =
            cordapps.filter { !it.contractClassNames.isEmpty() }.deDupeSameContractClassNames().map {
                it.jarPath.openStream().use { stream ->
                    try {
                        attachmentStorage.importAttachment(stream, DEPLOYED_CORDAPP_UPLOADER, null)
                    } catch (faee: java.nio.file.FileAlreadyExistsException) {
                        AttachmentId.parse(faee.message!!)
                    }
                } to it
            }.toMap()


    /**
     * There might be cases when there are CorDapps that have the same contract classes. E.g. during Gradle unit test run the following been observed:
     *
     *     1. contractClassNames=[net.corda.finance.contracts.asset.Cash, net.corda.finance.contracts.asset.CommodityContract, net.corda.finance.contracts.asset.Obligation, net.corda.finance.contracts.asset.OnLedgerAsset] ->
     *          jarPath=file:/Z:/corda/finance/build/tmp/generated-test-cordapps/net.corda.finance.contracts.asset-9562408e-b372-4194-8259-91dedaedc0ea.jar
     *
     *     2. contractClassNames=[net.corda.finance.contracts.asset.Cash, net.corda.finance.contracts.asset.CommodityContract, net.corda.finance.contracts.asset.Obligation, net.corda.finance.contracts.asset.OnLedgerAsset] ->
     *          jarPath=file:/Z:/corda/finance/build/libs/corda-finance-corda-4.0-snapshot.jar
     *
     *  These will be represented as distinct attachments with different hashes. Since underlying storage is `HashBiMap` when look-up is the ordering is not guaranteed and given contract e.g. `net.corda.finance.contracts.asset.Cash`
     *  can be found in either of the Jars which causes flakiness.
     *
     *  To mitigate that we de-dupe CorDapps based on the same `contractClassNames`.
     */
    private fun List<Cordapp>.deDupeSameContractClassNames(): List<CorDappLight> {

        val foldResult = this.map { CorDappLight(it) }.fold(Pair(HashMap<URL, CorDappLight>(), HashSet<String>())) { (acc, notedContracts), corDapp ->
            val existingEntry = acc[corDapp.jarPath]
            if(existingEntry != null) {
                // This URL path already been seen. Amend existing entry with contract list.
                acc[corDapp.jarPath] = existingEntry.copy(contractClassNames = existingEntry.contractClassNames + corDapp.contractClassNames)
                notedContracts += corDapp.contractClassNames
            } else if ((notedContracts - corDapp.contractClassNames).size == notedContracts.size) {
                // Complete disconnect or "notedContracts" is empty.
                acc += Pair(corDapp.jarPath, corDapp)
                notedContracts += corDapp.contractClassNames
            } else if (notedContracts.containsAll(corDapp.contractClassNames)) {
                log.warn("Skipping $corDapp as all of it is contracts already included")
            } else {
                val modifiedContractsList = corDapp.contractClassNames - notedContracts
                val modifiedCordapp = corDapp.copy(contractClassNames = modifiedContractsList)
                log.warn("Cordapp $corDapp has it contracts list modified to: $modifiedContractsList")
                acc += Pair(modifiedCordapp.jarPath, modifiedCordapp)
                notedContracts += modifiedContractsList
            }
            Pair(acc, notedContracts)
        }
        return foldResult.first.values.toList()
    }

    /**
     * Get the current cordapp context for the given CorDapp
     *
     * @param cordapp The cordapp to get the context for
     * @return A cordapp context for the given CorDapp
     */
    fun getAppContext(cordapp: Cordapp): CordappContext {
        return contextCache.computeIfAbsent(cordapp, {
            createCordappContext(
                    cordapp,
                    getCordappAttachmentId(cordapp),
                    cordappLoader.appClassLoader,
                    TypesafeCordappConfig(cordappConfigProvider.getConfigByName(cordapp.name))
            )
        })
    }

    /**
     * Resolves a cordapp for the provided class or null if there isn't one
     *
     * @param className The class name
     * @return cordapp A cordapp or null if no cordapp has the given class loaded
     */
    fun getCordappForClass(className: String): Cordapp? = cordapps.find { it.cordappClasses.contains(className) }
}