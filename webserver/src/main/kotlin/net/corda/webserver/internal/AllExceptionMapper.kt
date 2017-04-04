package net.corda.webserver.internal

import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

// Provides basic exception logging to all APIs
@Provider
class AllExceptionMapper: ExceptionMapper<Exception> {
    override fun toResponse(exception: Exception?): Response {
        APIServerImpl.logger.error("Unhandled exception in API", exception)
        return Response.status(500).build()
    }
}