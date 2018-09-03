package net.corda.webserver.internal

import net.corda.core.utilities.loggerFor
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

// Provides basic exception logging to all APIs
@Provider
class AllExceptionMapper : ExceptionMapper<Exception> {
    companion object {
        private val logger = loggerFor<APIServerImpl>() // XXX: Really?
    }

    override fun toResponse(exception: Exception?): Response {
        logger.error("Unhandled exception in API", exception)
        return Response.status(500).build()
    }
}