/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notaryhealthcheck

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.User
import net.corda.node.services.Permissions
import net.corda.notaryhealthcheck.flows.HealthCheckFlow
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val addresses = listOf(NetworkHostAndPort("localhost", 10003))
    val notaryDemoUser = User("demou", "demop", setOf(Permissions.all()))

    addresses.parallelStream().forEach {
       val c = CordaRPCClient(it).start(notaryDemoUser.username, notaryDemoUser.password)
       healthCheck(c.proxy)
    }
    println("Health check complete.")
}

fun healthCheck(rpc: CordaRPCOps) {
    val notary = rpc.notaryIdentities().first()
    print("Running health check for notary cluster ${notary.name}... ")
    rpc.startFlow(::HealthCheckFlow, notary, true).returnValue.get(30, TimeUnit.SECONDS)
    println("Done.")
}
