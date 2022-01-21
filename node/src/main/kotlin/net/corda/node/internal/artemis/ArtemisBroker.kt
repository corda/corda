package net.corda.node.internal.artemis

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

fun Throwable.isBindingError(): Boolean {
    val addressAlreadyUsedMsg = "Address already in use"
    // This is not an exact science here.
    // Depending on the underlying OS it can be either [Errors.NativeIoException] on Linux or [BindException] on Windows
    // and of course this is dependent on the version of Artemis library used.
    return this is BindException ||
            this is IllegalStateException && cause.let { it is BindException || it?.message?.contains(addressAlreadyUsedMsg) == true }
}