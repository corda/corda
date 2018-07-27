package net.corda.rpcWorker

import net.corda.core.messaging.RPCOps
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.time.ZonedDateTime

// TODO: This interface should really be residing in the "client" sub-module such that the JAR where this interface (but no the implementation)
// Is available to RPC clients
interface SimplisticRpcOps : RPCOps {
    fun currentTimeStamp(): String
    fun hostnameAndPid(): String
}

class SimplisticRpcOpsImpl : SimplisticRpcOps {

    override val protocolVersion: Int = 1

    override fun currentTimeStamp(): String {
        return ZonedDateTime.now().toString()
    }

    override fun hostnameAndPid(): String {
        val info = ManagementFactory.getRuntimeMXBean()
        val pid = info.name.split("@").firstOrNull()  // TODO Java 9 has better support for this
        val hostName: String = InetAddress.getLocalHost().hostName
        return "$hostName:$pid"
    }
}