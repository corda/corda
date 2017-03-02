package net.corda.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.deser.std.StringArrayDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.core.contracts.BusinessCalendar
import net.corda.core.crypto.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.math.BigDecimal

/**
 * Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
 * the java.time API, some core types, and Kotlin data classes.
 *
 * Note that Jackson can also be used to serialise/deserialise other formats such as Yaml and XML.
 */
object JacksonSupport {
    // If you change this API please update the docs in the docsite (json.rst)

    interface PartyObjectMapper {
        fun partyFromName(partyName: String): Party?
        fun partyFromKey(owningKey: CompositeKey): Party?
    }

    class RpcObjectMapper(val rpc: CordaRPCOps) : PartyObjectMapper, ObjectMapper() {
        override fun partyFromName(partyName: String): Party? = rpc.partyFromName(partyName)
        override fun partyFromKey(owningKey: CompositeKey): Party? = rpc.partyFromKey(owningKey)
    }
    class IdentityObjectMapper(val identityService: IdentityService) : PartyObjectMapper, ObjectMapper(){
        override fun partyFromName(partyName: String): Party? = identityService.partyFromName(partyName)
        override fun partyFromKey(owningKey: CompositeKey): Party? = identityService.partyFromKey(owningKey)
    }
    class NoPartyObjectMapper: PartyObjectMapper, ObjectMapper() {
        override fun partyFromName(partyName: String): Party? = throw UnsupportedOperationException()
        override fun partyFromKey(owningKey: CompositeKey): Party? = throw UnsupportedOperationException()
    }

    val cordaModule: Module by lazy {
        SimpleModule("core").apply {
            addSerializer(AnonymousParty::class.java, AnonymousPartySerializer)
            addDeserializer(AnonymousParty::class.java, AnonymousPartyDeserializer)
            addSerializer(Party::class.java, PartySerializer)
            addDeserializer(Party::class.java, PartyDeserializer)
            addSerializer(BigDecimal::class.java, ToStringSerializer)
            addDeserializer(BigDecimal::class.java, NumberDeserializers.BigDecimalDeserializer())
            addSerializer(SecureHash::class.java, SecureHashSerializer)
            addDeserializer(SecureHash::class.java, SecureHashDeserializer())
            addDeserializer(BusinessCalendar::class.java, CalendarDeserializer)

            // For ed25519 pubkeys
            addSerializer(EdDSAPublicKey::class.java, PublicKeySerializer)
            addDeserializer(EdDSAPublicKey::class.java, PublicKeyDeserializer)

            // For composite keys
            addSerializer(CompositeKey::class.java, CompositeKeySerializer)
            addDeserializer(CompositeKey::class.java, CompositeKeyDeserializer)

            // For NodeInfo
            // TODO this tunnels the Kryo representation as a Base58 encoded string. Replace when RPC supports this.
            addSerializer(NodeInfo::class.java, NodeInfoSerializer)
            addDeserializer(NodeInfo::class.java, NodeInfoDeserializer)
        }
    }

    /** Mapper requiring RPC support to deserialise parties from names */
    @JvmStatic
    fun createDefaultMapper(rpc: CordaRPCOps): ObjectMapper = configureMapper(RpcObjectMapper(rpc))

    /** For testing or situations where deserialising parties is not required */
    @JvmStatic
    fun createNonRpcMapper(): ObjectMapper = configureMapper(NoPartyObjectMapper())

    /** For testing with an in memory identity service */
    @JvmStatic
    fun createInMemoryMapper(identityService: IdentityService) = configureMapper(IdentityObjectMapper(identityService))

    private fun configureMapper(mapper: ObjectMapper): ObjectMapper = mapper.apply {
        enable(SerializationFeature.INDENT_OUTPUT)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

        registerModule(JavaTimeModule())
        registerModule(cordaModule)
        registerModule(KotlinModule())
    }

    object ToStringSerializer : JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    object AnonymousPartySerializer : JsonSerializer<AnonymousParty>() {
        override fun serialize(obj: AnonymousParty, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.owningKey.toBase58String())
        }
    }

    object AnonymousPartyDeserializer : JsonDeserializer<AnonymousParty>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): AnonymousParty {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }

            val mapper = parser.codec as PartyObjectMapper
            // TODO this needs to use some industry identifier(s) instead of these keys
            val key = CompositeKey.parseFromBase58(parser.text)
            return AnonymousParty(key)
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

            val mapper = parser.codec as PartyObjectMapper
            // TODO this needs to use some industry identifier(s) not just these human readable names
            return mapper.partyFromName(parser.text) ?: throw JsonParseException(parser, "Could not find a Party with name ${parser.text}")
        }
    }

    object NodeInfoSerializer : JsonSerializer<NodeInfo>() {
        override fun serialize(value: NodeInfo, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(Base58.encode(value.serialize().bytes))
        }
    }

    object NodeInfoDeserializer : JsonDeserializer<NodeInfo>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): NodeInfo {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }
            try {
                return Base58.decode(parser.text).deserialize<NodeInfo>()
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid NodeInfo ${parser.text}: ${e.message}")
            }
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

    object CompositeKeySerializer : JsonSerializer<CompositeKey>() {
        override fun serialize(obj: CompositeKey, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toBase58String())
        }
    }

    object CompositeKeyDeserializer : JsonDeserializer<CompositeKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): CompositeKey {
            return try {
                CompositeKey.parseFromBase58(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid composite key ${parser.text}: ${e.message}")
            }
        }
    }
}

