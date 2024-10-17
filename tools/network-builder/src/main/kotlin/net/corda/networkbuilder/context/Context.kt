package net.corda.networkbuilder.context

import net.corda.core.identity.CordaX500Name
import net.corda.networkbuilder.Constants
import net.corda.networkbuilder.backends.Backend
import net.corda.networkbuilder.nodes.NodeInstanceRequest
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Context(val networkName: String, val backendType: Backend.BackendType, backendOptions: Map<String, String> = emptyMap()) {

    @Volatile
    var safeNetworkName: String = networkName.replace(Constants.ALPHA_NUMERIC_ONLY_REGEX, "").lowercase(Locale.getDefault())

    @Volatile
    var nodes: MutableMap<String, ConcurrentHashSet<PersistableNodeInstance>> = ConcurrentHashMap()

    @Volatile
    var networkInitiated: Boolean = false

    @Volatile
    var extraParams = ConcurrentHashMap<String, String>(backendOptions)

    private fun registerNode(name: String, nodeInstanceRequest: NodeInstanceRequest) {
        nodes.computeIfAbsent(name) { ConcurrentHashSet() }.add(nodeInstanceRequest.toPersistable())
    }

    fun registerNode(request: NodeInstanceRequest) {
        registerNode(request.name, request)
    }

    data class PersistableNodeInstance(
            val groupName: String,
            val groupX500: CordaX500Name?,
            val instanceName: String,
            val instanceX500: String,
            val localImageId: String?,
            val remoteImageName: String,
            val rpcPort: Int?,
            val fqdn: String,
            val rpcUser: String,
            val rpcPassword: String)

    companion object {
        fun fromInstanceRequest(nodeInstanceRequest: NodeInstanceRequest): PersistableNodeInstance {
            return PersistableNodeInstance(
                    nodeInstanceRequest.name,
                    nodeInstanceRequest.nodeConfig.myLegalName,
                    nodeInstanceRequest.nodeInstanceName,
                    nodeInstanceRequest.actualX500,
                    nodeInstanceRequest.localImageId,
                    nodeInstanceRequest.remoteImageName,
                    nodeInstanceRequest.nodeConfig.rpcOptions.address.port,
                    nodeInstanceRequest.expectedFqName,
                    "",
                    ""
            )
        }
    }

    private fun NodeInstanceRequest.toPersistable(): PersistableNodeInstance {
        return fromInstanceRequest(this)
    }
}