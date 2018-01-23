package net.corda.node.internal.artemis

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.LifecycleSupport
import net.corda.node.internal.stopBlocking
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl

interface ArtemisBroker : LifecycleSupport, AutoCloseable {
    val addresses: BrokerAddresses

    val serverControl: ActiveMQServerControl

    override fun close() {
        stopBlocking()
    }
}

data class BrokerAddresses(val public: NetworkHostAndPort, private val adminArg: NetworkHostAndPort?) {
    val admin = adminArg ?: public
}