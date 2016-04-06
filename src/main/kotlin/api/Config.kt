package api

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import core.BusinessCalendar
import core.Party
import core.crypto.SecureHash
import core.node.services.ServiceHub
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

/**
 * Primary purpose is to install Kotlin extensions for Jackson ObjectMapper so data classes work
 * and to organise serializers / deserializers for java.time.* classes as necessary
 */
@Provider
class Config(val services: ServiceHub): ContextResolver<ObjectMapper> {

    val defaultObjectMapper = createDefaultMapper(services)

    override fun getContext(type: java.lang.Class<*>): ObjectMapper {
        return defaultObjectMapper
    }

    class ServiceHubObjectMapper(var serviceHub: ServiceHub): ObjectMapper() {

    }

    companion object {
        private fun createDefaultMapper(services: ServiceHub): ObjectMapper {
            val mapper = ServiceHubObjectMapper(services)
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

            val timeModule = SimpleModule("java.time")
            timeModule.addSerializer(LocalDate::class.java, ToStringSerializer)
            timeModule.addDeserializer(LocalDate::class.java, LocalDateDeserializer)
            timeModule.addKeyDeserializer(LocalDate::class.java, LocalDateKeyDeserializer)
            timeModule.addSerializer(LocalDateTime::class.java, ToStringSerializer)

            val cordaModule = SimpleModule("core")
            cordaModule.addSerializer(Party::class.java, PartySerializer)
            cordaModule.addDeserializer(Party::class.java, PartyDeserializer)
            cordaModule.addSerializer(BigDecimal::class.java, ToStringSerializer)
            cordaModule.addDeserializer(BigDecimal::class.java, NumberDeserializers.BigDecimalDeserializer())
            cordaModule.addSerializer(SecureHash::class.java, SecureHashSerializer)
            // It's slightly remarkable, but apparently Jackson works out that this is the only possibility
            // for a SecureHash at the moment and tries to use SHA256 directly even though we only give it SecureHash
            cordaModule.addDeserializer(SecureHash.SHA256::class.java, SecureHashDeserializer())
            cordaModule.addDeserializer(BusinessCalendar::class.java, CalendarDeserializer)

            mapper.registerModule(timeModule)
            mapper.registerModule(cordaModule)
            mapper.registerModule(KotlinModule())
            return mapper
        }
    }

    object ToStringSerializer: JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    object LocalDateDeserializer: JsonDeserializer<LocalDate>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): LocalDate {
            return try {
                LocalDate.parse(parser.text)
            } catch (e: Exception) {
                throw JsonParseException("Invalid LocalDate ${parser.text}: ${e.message}", parser.currentLocation)
            }
        }
    }

    object LocalDateKeyDeserializer: KeyDeserializer() {
        override fun deserializeKey(text: String, p1: DeserializationContext): Any? {
            return LocalDate.parse(text)
        }

    }

    object PartySerializer: JsonSerializer<Party>() {
        override fun serialize(obj: Party, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.name)
        }
    }

    object PartyDeserializer: JsonDeserializer<Party>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Party {
            if(parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            val mapper = parser.codec as ServiceHubObjectMapper
            // TODO this needs to use some industry identifier(s) not just these human readable names
            val nodeForPartyName = mapper.serviceHub.networkMapCache.nodeForPartyName(parser.text) ?: throw JsonParseException("Could not find a Party with name: ${parser.text}", parser.currentLocation)
            return nodeForPartyName.identity
        }
    }

    object SecureHashSerializer: JsonSerializer<SecureHash>() {
        override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    /**
     * Implemented as a class so that we can instantiate for T
     */
    class SecureHashDeserializer<T : SecureHash>: JsonDeserializer<T>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): T {
            if(parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            return try {
                return SecureHash.parse(parser.text) as T
            } catch (e: Exception) {
                throw JsonParseException("Invalid hash ${parser.text}: ${e.message}", parser.currentLocation)
            }
        }
    }

    object CalendarDeserializer: JsonDeserializer<BusinessCalendar>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): BusinessCalendar {
            return try {
                val array = StringArrayDeserializer.instance.deserialize(parser, context)
                BusinessCalendar.getInstance(*array)
            } catch (e: Exception) {
                throw JsonParseException("Invalid calendar(s) ${parser.text}: ${e.message}", parser.currentLocation)
            }
        }
    }
}