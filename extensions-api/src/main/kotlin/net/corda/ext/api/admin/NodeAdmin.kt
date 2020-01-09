package net.corda.ext.api.admin

import net.corda.core.CordaInternal
import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.SerializeAsToken
import java.util.function.Consumer

@CordaInternal
interface NodeAdmin {
    val corDapps: List<Cordapp>
    val corDappClassLoader: ClassLoader

    /**
     * Special services which upon serialisation will be represented in the stream by a special token. On the remote side
     * during deserialization token will be read and corresponding instance found and wired as necessary.
     */
    val tokenizableServices: List<SerializeAsToken>

    /**
     * Is a DB object which controls the state of the node, e.g. flow draining mode. Not to be confused with Node Configuration.
     */
    val propertiesStore: NodePropertiesStore

    /**
     * Used to notify the owner about intention to perform graceful shutdown.
     */
    val nodeShutdownTrigger: Consumer<Any?>
}