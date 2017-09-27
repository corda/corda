package net.corda.webserver.internal

import net.corda.core.messaging.CordaRPCOps
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

    override fun info() = rpcOps.nodeInfo()
}
