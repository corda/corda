package net.corda.node.internal.admin

import net.corda.core.serialization.SerializeAsToken
import net.corda.ext.api.admin.NodeAdmin
import net.corda.ext.api.admin.NodePropertiesStore
import net.corda.nodeapi.internal.cordapp.CordappLoader
import java.util.function.Consumer

class NodeAdminImpl(cordappLoader: CordappLoader, override val propertiesStore: NodePropertiesStore,
                    override val nodeShutdownTrigger: Consumer<Any?>, override val tokenizableServices: List<SerializeAsToken>)
    : NodeAdmin {

    override val corDapps = cordappLoader.cordapps
    override val corDappClassLoader = cordappLoader.appClassLoader
}