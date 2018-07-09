package net.corda.node.internal.artemis

import io.netty.channel.unix.Errors
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.LifecycleSupport
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import java.net.BindException

interface ArtemisBroker : LifecycleSupport, AutoCloseable {
    val addresses: BrokerAddresses

    val serverControl: ActiveMQServerControl

    override fun close() = stop()
}

data class BrokerAddresses(val primary: NetworkHostAndPort, private val adminArg: NetworkHostAndPort?) {
    val admin = adminArg ?: primary
}

fun java.io.IOException.isBindingError() = this is BindException || this is Errors.NativeIoException && message?.contains("Address already in use") == true