package net.corda.attestation

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class ExceptionHandler : ExceptionMapper<WebApplicationException> {
    private companion object {
        @JvmStatic
        private val log: Logger = LoggerFactory.getLogger(ExceptionHandler::class.java)
    }

    override fun toResponse(e: WebApplicationException): Response {
        log.error("HTTP Status: {}: {}", e.response.status, e.message)
        return e.response
    }
}
