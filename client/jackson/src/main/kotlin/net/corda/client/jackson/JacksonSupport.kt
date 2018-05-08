package net.corda.client.jackson

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.AddressFormatException
import net.corda.core.crypto.Base58
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.base64ToByteArray
import net.corda.core.utilities.toBase64
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/**
 * Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
 * the java.time API, some core types, and Kotlin data classes.
 *
 * Note that Jackson can also be used to serialise/deserialise other formats such as Yaml and XML.
 */
object JacksonSupport {
    // TODO: This API could use some tidying up - there should really only need to be one kind of mapper.
    // If you change this API please update the docs in the docsite (json.rst)

    interface PartyObjectMapper {
        fun wellKnownPartyFromX500Name(name: CordaX500Name): Party?
        fun partyFromKey(owningKey: PublicKey): Party?
        fun partiesFromName(query: String): Set<Party>
    }

    class RpcObjectMapper(val rpc: CordaRPCOps, factory: JsonFactory, val fuzzyIdentityMatch: Boolean) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = rpc.wellKnownPartyFromX500Name(name)
        override fun partyFromKey(owningKey: PublicKey): Party? = rpc.partyFromKey(owningKey)
        override fun partiesFromName(query: String) = rpc.partiesFromName(query, fuzzyIdentityMatch)
    }

    class IdentityObjectMapper(val identityService: IdentityService, factory: JsonFactory, val fuzzyIdentityMatch: Boolean) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = identityService.wellKnownPartyFromX500Name(name)
        override fun partyFromKey(owningKey: PublicKey): Party? = identityService.partyFromKey(owningKey)
        override fun partiesFromName(query: String) = identityService.partiesFromName(query, fuzzyIdentityMatch)
    }

    class NoPartyObjectMapper(factory: JsonFactory) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = throw UnsupportedOperationException()
        override fun partyFromKey(owningKey: PublicKey): Party? = throw UnsupportedOperationException()
        override fun partiesFromName(query: String) = throw UnsupportedOperationException()
    }

    val cordaModule: Module by lazy {
        SimpleModule("core").apply {
            addSerializer(AnonymousParty::class.java, AnonymousPartySerializer)
            addDeserializer(AnonymousParty::class.java, AnonymousPartyDeserializer)
            addSerializer(Party::class.java, PartySerializer)
            addDeserializer(Party::class.java, PartyDeserializer)
            addDeserializer(AbstractParty::class.java, PartyDeserializer)
            addSerializer(BigDecimal::class.java, ToStringSerializer)
            addDeserializer(BigDecimal::class.java, NumberDeserializers.BigDecimalDeserializer())
            addSerializer(SecureHash::class.java, SecureHashSerializer)
            addSerializer(SecureHash.SHA256::class.java, SecureHashSerializer)
            addDeserializer(SecureHash::class.java, SecureHashDeserializer())
            addDeserializer(SecureHash.SHA256::class.java, SecureHashDeserializer())

            // Public key types
            addSerializer(PublicKey::class.java, PublicKeySerializer)
            addDeserializer(PublicKey::class.java, PublicKeyDeserializer)

            // For NodeInfo
            // TODO this tunnels the Kryo representation as a Base58 encoded string. Replace when RPC supports this.
            addSerializer(NodeInfo::class.java, NodeInfoSerializer)
            addDeserializer(NodeInfo::class.java, NodeInfoDeserializer)

            // For Amount
            addSerializer(Amount::class.java, AmountSerializer)
            addDeserializer(Amount::class.java, AmountDeserializer)

            // For OpaqueBytes
            addDeserializer(OpaqueBytes::class.java, OpaqueBytesDeserializer)
            addSerializer(OpaqueBytes::class.java, OpaqueBytesSerializer)

            addDeserializer(TransactionSignature::class.java, TransactionSignatureSerDe.Deserializer)
            addSerializer(TransactionSignature::class.java, TransactionSignatureSerDe.Serializer)

            // For X.500 distinguished names
            addDeserializer(CordaX500Name::class.java, CordaX500NameDeserializer)
            addSerializer(CordaX500Name::class.java, CordaX500NameSerializer)

            // Mixins for transaction types to prevent some properties from being serialized
            setMixInAnnotation(SignedTransaction::class.java, SignedTransactionMixin::class.java)
            setMixInAnnotation(WireTransaction::class.java, WireTransactionMixin::class.java)
        }
    }

    /**
     * Creates a Jackson ObjectMapper that uses RPC to deserialise parties from string names.
     *
     * If [fuzzyIdentityMatch] is false, fields mapped to [Party] objects must be in X.500 name form and precisely
     * match an identity known from the network map. If true, the name is matched more leniently but if the match
     * is ambiguous a [JsonParseException] is thrown.
     */
    @JvmStatic
    @JvmOverloads
    fun createDefaultMapper(rpc: CordaRPCOps, factory: JsonFactory = JsonFactory(),
                            fuzzyIdentityMatch: Boolean = false): ObjectMapper = configureMapper(RpcObjectMapper(rpc, factory, fuzzyIdentityMatch))

    /** For testing or situations where deserialising parties is not required */
    @JvmStatic
    @JvmOverloads
    fun createNonRpcMapper(factory: JsonFactory = JsonFactory()): ObjectMapper = configureMapper(NoPartyObjectMapper(factory))

    /**
     * Creates a Jackson ObjectMapper that uses an [IdentityService] directly inside the node to deserialise parties from string names.
     *
     * If [fuzzyIdentityMatch] is false, fields mapped to [Party] objects must be in X.500 name form and precisely
     * match an identity known from the network map. If true, the name is matched more leniently but if the match
     * is ambiguous a [JsonParseException] is thrown.
     */
    @JvmStatic
    @JvmOverloads
    fun createInMemoryMapper(identityService: IdentityService, factory: JsonFactory = JsonFactory(),
                             fuzzyIdentityMatch: Boolean = false) = configureMapper(IdentityObjectMapper(identityService, factory, fuzzyIdentityMatch))

    private fun configureMapper(mapper: ObjectMapper): ObjectMapper = mapper.apply {
        enable(SerializationFeature.INDENT_OUTPUT)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

        registerModule(JavaTimeModule())
        registerModule(cordaModule)
        registerModule(KotlinModule())
    }

    object TransactionSignatureSerDe {

        object Serializer: JsonSerializer<TransactionSignature>() {
            override fun serialize(value: TransactionSignature, json: JsonGenerator, serializers: SerializerProvider) {
                with(json) {
                    writeStartObject()
                    writeObjectField("by", value.by)
                    writeObjectField("signatureMetadata", value.signatureMetadata)
                    writeObjectField("bytes", value.bytes)
                    writeObjectField("partialMerkleTree", value.partialMerkleTree)
                    writeEndObject()
                }
            }
        }

        object Deserializer: JsonDeserializer<TransactionSignature>() {
            override fun deserialize(parser: JsonParser, context: DeserializationContext): TransactionSignature {
                TODO("sollecitom")
            }
        }
    }

    object ToStringSerializer : JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    object AnonymousPartySerializer : JsonSerializer<AnonymousParty>() {
        override fun serialize(obj: AnonymousParty, generator: JsonGenerator, provider: SerializerProvider) {
            PublicKeySerializer.serialize(obj.owningKey, generator, provider)
        }
    }

    object AnonymousPartyDeserializer : JsonDeserializer<AnonymousParty>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): AnonymousParty {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }

            val key = PublicKeyDeserializer.deserialize(parser, context)
            return AnonymousParty(key)
        }
    }

    object PartySerializer : JsonSerializer<Party>() {
        override fun serialize(obj: Party, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.name.toString())
        }
    }

    object PartyDeserializer : JsonDeserializer<Party>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Party {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }

            val mapper = parser.codec as PartyObjectMapper
            // The comma character is invalid in base64, and required as a separator for X.500 names. As Corda
            // X.500 names all involve at least three attributes (organisation, locality, country), they must
            // include a comma. As such we can use it as a distinguisher between the two types.
            return if (parser.text.contains(",")) {
                val principal = CordaX500Name.parse(parser.text)
                mapper.wellKnownPartyFromX500Name(principal) ?: throw JsonParseException(parser, "Could not find a Party with name $principal")
            } else {
                val nameMatches = mapper.partiesFromName(parser.text)
                if (nameMatches.isEmpty()) {
                    val derBytes = try {
                        parser.text.base64ToByteArray()
                    } catch (e: AddressFormatException) {
                        throw JsonParseException(parser, "Could not find a matching party for '${parser.text}' and is not a base64 encoded public key: " + e.message)
                    }
                    val key = try {
                        Crypto.decodePublicKey(derBytes)
                    } catch (e: Exception) {
                        throw JsonParseException(parser, "Could not find a matching party for '${parser.text}' and is not a valid public key: " + e.message)
                    }
                    mapper.partyFromKey(key) ?: throw JsonParseException(parser, "Could not find a Party with key ${key.toStringShort()}")
                } else if (nameMatches.size == 1) {
                    nameMatches.first()
                } else {
                    throw JsonParseException(parser, "Ambiguous name match '${parser.text}': could be any of " + nameMatches.map { it.name }.joinToString(" ... or ..."))
                }
            }
        }
    }

    object CordaX500NameSerializer : JsonSerializer<CordaX500Name>() {
        override fun serialize(obj: CordaX500Name, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    object CordaX500NameDeserializer : JsonDeserializer<CordaX500Name>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): CordaX500Name {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                parser.nextToken()
            }

            return try {
                CordaX500Name.parse(parser.text)
            } catch (ex: IllegalArgumentException) {
                throw JsonParseException(parser, "Invalid Corda X.500 name ${parser.text}: ${ex.message}", ex)
            }
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
                return uncheckedCast(SecureHash.parse(parser.text))
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid hash ${parser.text}: ${e.message}")
            }
        }
    }

    object PublicKeySerializer : JsonSerializer<PublicKey>() {
        override fun serialize(obj: PublicKey, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.encoded.toBase64())
        }
    }

    object PublicKeyDeserializer : JsonDeserializer<PublicKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): PublicKey {
            return try {
                val derBytes = parser.text.base64ToByteArray()
                Crypto.decodePublicKey(derBytes)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid public key ${parser.text}: ${e.message}")
            }
        }
    }

    object AmountSerializer : JsonSerializer<Amount<*>>() {
        override fun serialize(value: Amount<*>, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    object AmountDeserializer : JsonDeserializer<Amount<*>>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Amount<*> {
            try {
                return Amount.parseCurrency(parser.text)
            } catch (e: Exception) {
                try {
                    val tree = parser.readValueAsTree<JsonNode>()
                    require(tree["quantity"].canConvertToLong() && tree["token"].asText().isNotBlank())
                    val quantity = tree["quantity"].asLong()
                    val token = tree["token"].asText()
                    // Attempt parsing as a currency token. TODO: This needs thought about how to extend to other token types.
                    val currency = Currency.getInstance(token)
                    return Amount(quantity, currency)
                } catch (e2: Exception) {
                    throw JsonParseException(parser, "Invalid amount ${parser.text}", e2)
                }
            }
        }
    }

    object OpaqueBytesDeserializer : JsonDeserializer<OpaqueBytes>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): OpaqueBytes {
            return OpaqueBytes(parser.text.toByteArray())
        }
    }

    object OpaqueBytesSerializer : JsonSerializer<OpaqueBytes>() {
        override fun serialize(value: OpaqueBytes, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeBinary(value.bytes)
        }
    }

    abstract class SignedTransactionMixin {
        @JsonIgnore abstract fun getTxBits(): SerializedBytes<CoreTransaction>
        @JsonProperty("signatures") protected abstract fun getSigs(): List<TransactionSignature>
        @JsonProperty protected abstract fun getTransaction(): CoreTransaction
        @JsonIgnore abstract fun getTx(): WireTransaction
        @JsonIgnore abstract fun getNotaryChangeTx(): NotaryChangeWireTransaction
        @JsonIgnore abstract fun getInputs(): List<StateRef>
        @JsonIgnore abstract fun getNotary(): Party?
        @JsonIgnore abstract fun getId(): SecureHash
        @JsonIgnore abstract fun getRequiredSigningKeys(): Set<PublicKey>
    }

    abstract class WireTransactionMixin {
        @JsonIgnore abstract fun getMerkleTree(): MerkleTree
        @JsonIgnore abstract fun getAvailableComponents(): List<Any>
        @JsonIgnore abstract fun getAvailableComponentHashes(): List<SecureHash>
        @JsonIgnore abstract fun getOutputStates(): List<ContractState>
    }
}

