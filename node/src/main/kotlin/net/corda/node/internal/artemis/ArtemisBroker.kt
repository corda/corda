package net.corda.node.internal.artemis

import io.netty.channel.unix.Errors
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.LifecycleSupport
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl
import java.lang.IllegalStateException
import java.net.BindException

interface ArtemisBroker : LifecycleSupport, AutoCloseable {
    val addresses: BrokerAddresses

    val serverControl: ActiveMQServerControl

    override fun close() = stop()
}

data class BrokerAddresses(val primary: NetworkHostAndPort, private val adminArg: NetworkHostAndPort?) {
    val admin = adminArg ?: primary
}

fun Throwable.isBindingError(): Boolean {
    val addressAlreadyUsedMsg = "Address already in use"
    return this is BindException ||
            this is Errors.NativeIoException && message?.contains(addressAlreadyUsedMsg) == true ||
            this is IllegalStateException && this.cause is Errors.NativeIoException && this.cause!!.message?.contains(addressAlreadyUsedMsg) == true
}