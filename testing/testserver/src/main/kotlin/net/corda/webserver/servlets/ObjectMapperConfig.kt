package net.corda.webserver.servlets

import com.fasterxml.jackson.databind.ObjectMapper
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

/**
 * Primary purpose is to install Kotlin extensions for Jackson ObjectMapper so data classes work
 * and to organise serializers / deserializers for java.time.* classes as necessary.
 */
@Provider
class ObjectMapperConfig(val defaultObjectMapper: ObjectMapper) : ContextResolver<ObjectMapper> {
    override fun getContext(type: Class<*>) = defaultObjectMapper
}
