package net.corda.node.webserver.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.node.webserver.api.*
import java.time.LocalDateTime
import java.time.ZoneId
import javax.ws.rs.core.Response

class APIServerImpl(val rpcOps: CordaRPCOps) : APIServer {

    override fun serverTime(): LocalDateTime {
        return LocalDateTime.ofInstant(rpcOps.currentNodeTime(), ZoneId.of("UTC"))
    }

    override fun status(): Response {
        return if (rpcOps.ready()) {
            Response.ok("started").build()
        } else {
            Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("not started").build()
        }
    }

    override fun info() = rpcOps.nodeIdentity()
}
