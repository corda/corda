package net.corda.node.internal.cordapp

import net.corda.core.flows.FlowLogic
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import java.net.URL

/**
 * Defines a CorDapp
 *
 * @property contractClassNames List of contracts
 * @property initiatedFlows List of initiatable flow classes
 * @property rpcFlows List of RPC initiable flows classes
 * @property servies List of RPC services
 * @property plugins List of Corda plugin registries
 * @property jarPath The path to the JAR for this CorDapp
 */
data class Cordapp(
        val contractClassNames: List<String>,
        val initiatedFlows: List<Class<out FlowLogic<*>>>,
        val rpcFlows: List<Class<out FlowLogic<*>>>,
        val services: List<Class<out SerializeAsToken>>,
        val plugins: List<CordaPluginRegistry>,
        val jarPath: URL) {
    companion object {
        private val logger = loggerFor<Cordapp>()
    }

    fun filterEnabledServices(info: NodeInfo): List<Class<out SerializeAsToken>> {
        return services.filter {
            val serviceType = getServiceType(it)
            if (serviceType != null && info.serviceIdentities(serviceType).isEmpty()) {
                logger.debug {
                    "Ignoring ${it.name} as a Corda service since $serviceType is not one of our " +
                            "advertised services"
                }
                false
            } else {
                true
            }
        }
    }

    private fun getServiceType(clazz: Class<*>): ServiceType? {
        return try {
            clazz.getField("type").get(null) as ServiceType
        } catch (e: NoSuchFieldException) {
            logger.warn("${clazz.name} does not have a type field, optimistically proceeding with install.")
            null
        }
    }
}