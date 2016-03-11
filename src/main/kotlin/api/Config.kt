package api

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.time.LocalDate
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

/**
 * Primary purpose is to install Kotlin extensions for Jackson ObjectMapper so data classes work
 * and to organise serializers / deserializers for java.time.* classes as necessary
 */
@Provider
class Config: ContextResolver<ObjectMapper> {

    val defaultObjectMapper = createDefaultMapper()

    override fun getContext(type: java.lang.Class<*>): ObjectMapper {
        return defaultObjectMapper
    }

    companion object {
        private fun createDefaultMapper(): ObjectMapper {
            val mapper = ObjectMapper()
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); // Although we shouldn't really use java.util.* but instead java.time.*
            val timeModule = SimpleModule("java.time")
            timeModule.addSerializer(LocalDate::class.java, ToStringSerializer)
            timeModule.addDeserializer(LocalDate::class.java, LocalDateDeserializer)
            mapper.registerModule(timeModule)
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
            return LocalDate.parse(parser.text)
        }
    }
}