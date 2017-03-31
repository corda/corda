package net.corda.webserver.internal

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import net.corda.webserver.api.*
import java.time.LocalDateTime
import java.time.ZoneId
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

class APIServerImpl(val rpcOps: CordaRPCOps) : APIServer {
    companion object {
        val logger = loggerFor<APIServerImpl>()
    }

    override fun serverTime(): LocalDateTime {
        return LocalDateTime.ofInstant(rpcOps.currentNodeTime(), ZoneId.of("UTC"))
    }

    /**
     * This endpoint is for polling if the webserver is serving. It will always return 200.
     */
    override fun status(): Response {
        return Response.ok("started").build()
    }

    override fun info() = rpcOps.nodeIdentity()

    @Provider
    class AllExceptionMapper: ExceptionMapper<Exception> {
        override fun toResponse(exception: Exception?): Response {
            logger.error("Unhandled exception in API", exception)
            return Response.status(500).build()
        }
    }
}
