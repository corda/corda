package net.corda.node.internal.cordapp

import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.internal.cordapp.ContractAttachmentWithLegacy
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappProviderInternal
import net.corda.core.internal.groupByMultipleKeys
import net.corda.core.internal.verification.AttachmentFixups
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.persistence.AttachmentStorageInternal
import net.corda.nodeapi.internal.cordapp.CordappLoader
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream

/**
 * Cordapp provider and store. For querying CorDapps for their attachment and vice versa.
 */
open class CordappProviderImpl(private val cordappLoader: CordappLoader,
                               private val cordappConfigProvider: CordappConfigProvider,
                               private val attachmentStorage: AttachmentStorageInternal) : SingletonSerializeAsToken(), CordappProviderInternal {
    private val contextCache = ConcurrentHashMap<Cordapp, CordappContext>()
    private lateinit var flowToCordapp: Map<Class<out FlowLogic<*>>, CordappImpl>

    override val attachmentFixups = AttachmentFixups()

    override val appClassLoader: ClassLoader get() = cordappLoader.appClassLoader

    /**
     * Current known CorDapps loaded on this node
     */
    override val cordapps: List<CordappImpl> get() = cordappLoader.cordapps

    fun start() {
        loadContractsIntoAttachmentStore(cordappLoader.cordapps)
        loadContractsIntoAttachmentStore(cordappLoader.legacyContractCordapps)
        flowToCordapp = makeFlowToCordapp()
        // Load the fix-ups after uploading any new contracts into attachment storage.
        attachmentFixups.load(cordappLoader.appClassLoader)
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
        return cordappLoader.cordapps.findCordapp(contractClassName)
    }

    override fun getContractAttachments(contractClassName: ContractClassName): ContractAttachmentWithLegacy? {
        val currentAttachmentId = getContractAttachmentID(contractClassName) ?: return null
        val legacyAttachmentId = cordappLoader.legacyContractCordapps.findCordapp(contractClassName)
        return ContractAttachmentWithLegacy(getContractAttachment(currentAttachmentId), legacyAttachmentId?.let(::getContractAttachment))
    }

    private fun List<CordappImpl>.findCordapp(contractClassName: ContractClassName): AttachmentId? {
        // loadContractsIntoAttachmentStore makes sure the jarHash is the attachment ID
        return find { contractClassName in it.contractClassNames }?.jarHash
    }

    private fun loadContractsIntoAttachmentStore(cordapps: List<CordappImpl>) {
        for (cordapp in cordapps) {
            if (cordapp.contractClassNames.isEmpty()) continue
            val attachmentId = cordapp.jarFile.inputStream().use { stream ->
                attachmentStorage.privilegedImportOrGetAttachment(stream, DEPLOYED_CORDAPP_UPLOADER, cordapp.info.shortName)
            }
            // TODO We could remove this check if we had an import method for CorDapps, since it wouldn't need to hash the InputStream.
            //  As it stands, we just have to double-check the hashes match, which should be the case (see NodeAttachmentService).
            check(attachmentId == cordapp.jarHash) {
                "Something has gone wrong. SHA-256 hash of ${cordapp.jarFile} (${cordapp.jarHash}) does not match attachment ID ($attachmentId)"
            }
        }
    }

    private fun getContractAttachment(id: AttachmentId): ContractAttachment {
        return checkNotNull(attachmentStorage.openAttachment(id) as? ContractAttachment) { "Contract attachment $id has gone missing!" }
    }

    private fun makeFlowToCordapp(): Map<Class<out FlowLogic<*>>, CordappImpl> {
        return cordappLoader.cordapps.groupByMultipleKeys(CordappImpl::allFlows) { flowClass, _, _ ->
            val overlappingCordapps = cordappLoader.cordapps.filter { flowClass in it.allFlows }
            throw MultipleCordappsForFlowException("There are multiple CorDapp JARs on the classpath for flow ${flowClass.name}: " +
                    "[ ${overlappingCordapps.joinToString { it.jarPath.toString() }} ].",
                    flowClass.name,
                    overlappingCordapps.joinToString { it.jarFile.absolutePathString() }
            )
        }
    }

    /**
     * Get the current cordapp context for the given CorDapp
     *
     * @param cordapp The cordapp to get the context for
     * @return A cordapp context for the given CorDapp
     */
    fun getAppContext(cordapp: Cordapp): CordappContext {
        return contextCache.computeIfAbsent(cordapp) {
            CordappContext.create(
                    cordapp,
                    cordapp.jarHash.takeIf(attachmentStorage::hasAttachment),  // Not all CorDapps are attachments
                    cordappLoader.appClassLoader,
                    TypesafeCordappConfig(cordappConfigProvider.getConfigByName(cordapp.name))
            )
        }
    }

    /**
     * Resolves a cordapp for the provided class or null if there isn't one
     *
     * @param className The class name
     * @return cordapp A cordapp or null if no cordapp has the given class loaded
     */
    fun getCordappForClass(className: String): CordappImpl? = cordapps.find { it.cordappClasses.contains(className) }

    override fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp? = flowToCordapp[flowLogic.javaClass]
}
