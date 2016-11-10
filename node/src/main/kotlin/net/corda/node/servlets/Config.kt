package net.corda.node.servlets

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.node.ServiceHub
import net.corda.node.utilities.JsonSupport
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

/**
 * Primary purpose is to install Kotlin extensions for Jackson ObjectMapper so data classes work
 * and to organise serializers / deserializers for java.time.* classes as necessary.
 */
@Provider
class Config(val services: ServiceHub) : ContextResolver<ObjectMapper> {
    val defaultObjectMapper = JsonSupport.createDefaultMapper(services.identityService)
    override fun getContext(type: Class<*>) = defaultObjectMapper
}
