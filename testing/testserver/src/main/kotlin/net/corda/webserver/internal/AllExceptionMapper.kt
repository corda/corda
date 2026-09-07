package net.corda.webserver.internal

import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import net.corda.core.utilities.loggerFor

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