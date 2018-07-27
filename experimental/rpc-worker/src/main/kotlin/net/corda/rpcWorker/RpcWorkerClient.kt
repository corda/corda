package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT

fun main(args: Array<String>) {
    AMQPClientSerializationScheme.initialiseSerialization()
    val client = RPCClient<SimplisticRpcOps>(ArtemisTcpTransport.rpcConnectorTcpTransport(NetworkHostAndPort("localhost", 20002), null),
            CordaRPCClientConfiguration.DEFAULT, serializationContext = AMQP_RPC_CLIENT_CONTEXT)
    val connection = client.start(SimplisticRpcOps::class.java, "user1", "test1")

    try {
        val rpcOps = connection.proxy
        println("Server hostname and PID: " + rpcOps.hostnameAndPid())
        println("Server timestamp: " + rpcOps.currentTimeStamp())
    } finally {
        connection.close()
    }
}