package net.corda.node.webserver.servlets

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.node.utilities.JsonSupport
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

/**
 * Primary purpose is to install Kotlin extensions for Jackson ObjectMapper so data classes work
 * and to organise serializers / deserializers for java.time.* classes as necessary.
 */
@Provider
class ObjectMapperConfig(rpc: CordaRPCOps) : ContextResolver<ObjectMapper> {
    val defaultObjectMapper = JsonSupport.createDefaultMapper(rpc)
    override fun getContext(type: Class<*>) = defaultObjectMapper
}
