/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver.internal

import net.corda.core.contracts.ContractState
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.webserver.api.APIServer
import java.time.LocalDateTime
import java.time.ZoneId
import javax.ws.rs.core.Response

class APIServerImpl(val rpcOps: CordaRPCOps) : APIServer {
    override fun serverTime(): LocalDateTime {
        return LocalDateTime.ofInstant(rpcOps.currentNodeTime(), ZoneId.of("UTC"))
    }

    /**
     * This endpoint is for polling if the webserver is serving. It will always return 200.
     */
    override fun status(): Response {
        return Response.ok("started").build()
    }

    override fun addresses() = rpcOps.nodeInfo().addresses

    override fun identities() = rpcOps.nodeInfo().legalIdentities

    override fun platformVersion() = rpcOps.nodeInfo().platformVersion

    override fun peers() = rpcOps.networkMapSnapshot().flatMap { it.legalIdentities }

    override fun notaries() = rpcOps.notaryIdentities()

    override fun flows() = rpcOps.registeredFlows()

    override fun states() = rpcOps.vaultQueryBy<ContractState>().states
}
