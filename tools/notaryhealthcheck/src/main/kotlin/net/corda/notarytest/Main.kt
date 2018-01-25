package net.corda.notarytest

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.User
import net.corda.node.services.Permissions
import net.corda.notarytest.flows.HealthCheckFlow

fun main(args: Array<String>) {
    val addresses = listOf(NetworkHostAndPort("localhost", 10003))

    val notaryDemoUser = User("demou", "demop", setOf(Permissions.all()))

    addresses.parallelStream().forEach {
       val c = CordaRPCClient(it).start(notaryDemoUser.username, notaryDemoUser.password)
       healthCheck(c.proxy)
    }
    println("ok")
}

fun healthCheck(rpc: CordaRPCOps) {
    val notary = rpc.notaryIdentities().first()
    rpc.startFlow(::HealthCheckFlow, notary).returnValue.get()
}
