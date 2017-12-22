package net.corda.attestation.host.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import javax.ws.rs.Consumes
import javax.ws.rs.Produces
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

@Consumes("application/*+json", "text/json")
@Produces("application/*+json", "text/json")
@Provider
class JacksonConfig : ContextResolver<ObjectMapper> {
    private val mapper = ObjectMapper().registerModule(JavaTimeModule())
    override fun getContext(type: Class<*>?): ObjectMapper = mapper
}
