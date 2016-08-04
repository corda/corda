package com.r3corda.node.utilities

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.r3corda.core.contracts.BusinessCalendar
import com.r3corda.core.crypto.*
import com.r3corda.core.node.services.IdentityService
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
 * the java.time API, some core types, and Kotlin data classes.
 */
object JsonSupport {

    fun createDefaultMapper(identities: IdentityService): ObjectMapper {
        val mapper = ServiceHubObjectMapper(identities)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
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

        // For ed25519 pubkeys
        cordaModule.addSerializer(EdDSAPublicKey::class.java, PublicKeySerializer)
        cordaModule.addDeserializer(EdDSAPublicKey::class.java, PublicKeyDeserializer)

        mapper.registerModule(timeModule)
        mapper.registerModule(cordaModule)
        mapper.registerModule(KotlinModule())
        return mapper
    }

    class ServiceHubObjectMapper(val identities: IdentityService) : ObjectMapper()

    object ToStringSerializer : JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    object LocalDateDeserializer : JsonDeserializer<LocalDate>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): LocalDate {
            return try {
                LocalDate.parse(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid LocalDate ${parser.text}: ${e.message}")
            }
        }
    }

    object LocalDateKeyDeserializer : KeyDeserializer() {
        override fun deserializeKey(text: String, p1: DeserializationContext): Any? {
            return LocalDate.parse(text)
        }

    }

    object PartySerializer : JsonSerializer<Party>() {
        override fun serialize(obj: Party, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.name)
        }
    }

    object PartyDeserializer : JsonDeserializer<Party>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Party {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            val mapper = parser.codec as ServiceHubObjectMapper
            // TODO this needs to use some industry identifier(s) not just these human readable names
            return mapper.identities.partyFromName(parser.text) ?: throw JsonParseException(parser, "Could not find a Party with name: ${parser.text}")
        }
    }

    object SecureHashSerializer : JsonSerializer<SecureHash>() {
        override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    /**
     * Implemented as a class so that we can instantiate for T.
     */
    class SecureHashDeserializer<T : SecureHash> : JsonDeserializer<T>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): T {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            try {
                @Suppress("UNCHECKED_CAST")
                return SecureHash.parse(parser.text) as T
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid hash ${parser.text}: ${e.message}")
            }
        }
    }

    object CalendarDeserializer : JsonDeserializer<BusinessCalendar>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): BusinessCalendar {
            return try {
                val array = StringArrayDeserializer.instance.deserialize(parser, context)
                BusinessCalendar.getInstance(*array)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid calendar(s) ${parser.text}: ${e.message}")
            }
        }
    }

    object PublicKeySerializer : JsonSerializer<EdDSAPublicKey>() {
       override fun serialize(obj: EdDSAPublicKey, generator: JsonGenerator, provider: SerializerProvider) {
           check(obj.params == ed25519Curve)
           generator.writeString(obj.toBase58String())
       }
    }

    object PublicKeyDeserializer : JsonDeserializer<EdDSAPublicKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): EdDSAPublicKey {
            return try {
                parsePublicKeyBase58(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid public key ${parser.text}: ${e.message}")
            }
        }
    }
}
