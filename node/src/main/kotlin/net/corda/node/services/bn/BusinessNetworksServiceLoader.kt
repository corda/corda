package net.corda.node.services.bn

import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.BusinessNetworksServiceType

/**
 * Handles loading of the concrete [BusinessNetworksService] implementation based on node configuration's Business Network related fields.
 */
class BusinessNetworksServiceLoader(val vaultService: VaultService) : SingletonSerializeAsToken() {

    /**
     * Loads concrete implementation of [BusinessNetworksService] based on [serviceType].
     *
     * @param classLoader CorDapp class loader.
     * @param serviceType Type of the concrete [BusinessNetworksService] to be loaded.
     * @param serviceClassName Name of the class to be loaded.
     */
    fun load(classLoader: ClassLoader, serviceType: BusinessNetworksServiceType, serviceClassName: String): BusinessNetworksService {
        val serviceClass = classLoader.loadClass(serviceClassName)
        return when (serviceType) {
            BusinessNetworksServiceType.VAULT -> uncheckedCast(serviceClass.getConstructor(VaultService::class.java).newInstance(vaultService))
        }
    }
}