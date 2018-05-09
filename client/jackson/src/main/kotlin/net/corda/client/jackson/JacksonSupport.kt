package net.corda.client.jackson

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.convertValue
import net.corda.client.jackson.internal.addSerAndDeser
import net.corda.client.jackson.internal.jsonObject
import net.corda.client.jackson.internal.readValueAs
import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.*
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.parsePublicKeyBase58
import net.corda.core.utilities.toBase58String
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/**
 * Utilities and serialisers for working with JSON representations of basic types. This adds Jackson support for
 * the java.time API, some core types, and Kotlin data classes.
 *
 * Note that Jackson can also be used to serialise/deserialise other formats such as Yaml and XML.
 */
@Suppress("DEPRECATION")
object JacksonSupport {
    // If you change this API please update the docs in the docsite (json.rst)

    @DoNotImplement
    interface PartyObjectMapper {
        fun wellKnownPartyFromX500Name(name: CordaX500Name): Party?
        fun partyFromKey(owningKey: PublicKey): Party?
        fun partiesFromName(query: String): Set<Party>
        fun nodeInfoFromParty(party: AbstractParty): NodeInfo?
    }

    @Deprecated("This is an internal class, do not use", replaceWith = ReplaceWith("JacksonSupport.createDefaultMapper"))
    class RpcObjectMapper(val rpc: CordaRPCOps,
                          factory: JsonFactory,
                          val fuzzyIdentityMatch: Boolean) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = rpc.wellKnownPartyFromX500Name(name)
        override fun partyFromKey(owningKey: PublicKey): Party? = rpc.partyFromKey(owningKey)
        override fun partiesFromName(query: String) = rpc.partiesFromName(query, fuzzyIdentityMatch)
        override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? = rpc.nodeInfoFromParty(party)
    }

    @Deprecated("This is an internal class, do not use")
    class IdentityObjectMapper(val identityService: IdentityService,
                               factory: JsonFactory,
                               val fuzzyIdentityMatch: Boolean) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = identityService.wellKnownPartyFromX500Name(name)
        override fun partyFromKey(owningKey: PublicKey): Party? = identityService.partyFromKey(owningKey)
        override fun partiesFromName(query: String) = identityService.partiesFromName(query, fuzzyIdentityMatch)
        override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? = null
    }

    @Deprecated("This is an internal class, do not use", replaceWith = ReplaceWith("JacksonSupport.createNonRpcMapper"))
    class NoPartyObjectMapper(factory: JsonFactory) : PartyObjectMapper, ObjectMapper(factory) {
        override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = null
        override fun partyFromKey(owningKey: PublicKey): Party? = null
        override fun partiesFromName(query: String): Set<Party> = emptySet()
        override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? = null
    }

    val cordaModule: Module by lazy {
        SimpleModule("core").apply {
            addSerAndDeser(AnonymousPartySerializer, AnonymousPartyDeserializer)
            addSerAndDeser(PartySerializer, PartyDeserializer)
            addDeserializer(AbstractParty::class.java, PartyDeserializer)
            addSerAndDeser<BigDecimal>(toStringSerializer, NumberDeserializers.BigDecimalDeserializer())
            addSerAndDeser<SecureHash.SHA256>(toStringSerializer, SecureHashDeserializer())
            addSerAndDeser(toStringSerializer, AmountDeserializer)
            addSerAndDeser(OpaqueBytesSerializer, OpaqueBytesDeserializer)
            addSerAndDeser(toStringSerializer, CordaX500NameDeserializer)
            addSerAndDeser(PublicKeySerializer, PublicKeyDeserializer)
            addDeserializer(CompositeKey::class.java, CompositeKeyDeseriaizer)
            addSerAndDeser(toStringSerializer, NetworkHostAndPortDeserializer)
            // TODO Add deserialization which follows the same lookup logic as Party
            addSerializer(PartyAndCertificate::class.java, PartyAndCertificateSerializer)
            addDeserializer(NodeInfo::class.java, NodeInfoDeserializer)

            listOf(TransactionSignatureSerde, SignedTransactionSerde).forEach { serde -> serde.applyTo(this) }

            // Using mixins to fine-tune the default serialised output
            setMixInAnnotation(WireTransaction::class.java, WireTransactionMixin::class.java)
            setMixInAnnotation(NodeInfo::class.java, NodeInfoMixin::class.java)
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
    fun createDefaultMapper(rpc: CordaRPCOps,
                            factory: JsonFactory = JsonFactory(),
                            fuzzyIdentityMatch: Boolean = false): ObjectMapper {
        return configureMapper(RpcObjectMapper(rpc, factory, fuzzyIdentityMatch))
    }

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
    @Deprecated("This is an internal method, do not use")
    @JvmStatic
    @JvmOverloads
    fun createInMemoryMapper(identityService: IdentityService,
                             factory: JsonFactory = JsonFactory(),
                             fuzzyIdentityMatch: Boolean = false): ObjectMapper {
        return configureMapper(IdentityObjectMapper(identityService, factory, fuzzyIdentityMatch))
    }

    @CordaInternal
    @VisibleForTesting
    internal fun createPartyObjectMapper(partyObjectMapper: PartyObjectMapper, factory: JsonFactory = JsonFactory()): ObjectMapper {
        val mapper = object : ObjectMapper(factory), PartyObjectMapper by partyObjectMapper {}
        return configureMapper(mapper)
    }

    private fun configureMapper(mapper: ObjectMapper): ObjectMapper {
        return mapper.apply {
            enable(SerializationFeature.INDENT_OUTPUT)
            enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            registerModule(JavaTimeModule().apply {
                addSerializer(Date::class.java, DateSerializer)
            })
            registerModule(cordaModule)
            registerModule(KotlinModule())
        }
    }

    private val toStringSerializer = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance

    private object DateSerializer : JsonSerializer<Date>() {
        override fun serialize(value: Date, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeObject(value.toInstant())
        }
    }

    private object NetworkHostAndPortDeserializer : JsonDeserializer<NetworkHostAndPort>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): NetworkHostAndPort {
            return NetworkHostAndPort.parse(parser.text)
        }
    }

    private object CompositeKeyDeseriaizer : JsonDeserializer<CompositeKey>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): CompositeKey {
            val publicKey = parser.readValueAs<PublicKey>()
            return publicKey as? CompositeKey ?: throw JsonParseException(parser, "Not a CompositeKey: $publicKey")
        }
    }

    private object PartyAndCertificateSerializer : JsonSerializer<PartyAndCertificate>() {
        override fun serialize(value: PartyAndCertificate, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.jsonObject {
                writeObjectField("name", value.name)
                writeObjectField("owningKey", value.owningKey)
                // TODO Add configurable option to output the certPath
            }
        }
    }

    @Suppress("unused")
    private interface NodeInfoMixin {
        @get:JsonIgnore val legalIdentities: Any  // This is already covered by legalIdentitiesAndCerts
    }

    private interface JsonSerde<TYPE> {
        val type: Class<TYPE>
        val serializer: JsonSerializer<TYPE>
        val deserializer: JsonDeserializer<TYPE>

        fun applyTo(module: SimpleModule) {
            with(module) {
                addSerializer(type, serializer)
                addDeserializer(type, deserializer)
            }
        }
    }

    private inline fun <reified RESULT> JsonNode.get(fieldName: String, condition: (JsonNode) -> Boolean, mapper: ObjectMapper, parser: JsonParser): RESULT {
        if (get(fieldName)?.let(condition) != true) {
            JsonParseException(parser, "Missing required object field \"$fieldName\".")
        }
        return mapper.treeToValue(get(fieldName), RESULT::class.java)
    }

    private object TransactionSignatureSerde : JsonSerde<TransactionSignature> {
        override val type: Class<TransactionSignature> = TransactionSignature::class.java

        override val serializer = object : StdSerializer<TransactionSignature>(type) {
            override fun serialize(value: TransactionSignature, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.jsonObject {
                    writeObjectField("by", value.by)
                    writeObjectField("signatureMetadata", value.signatureMetadata)
                    writeObjectField("bytes", value.bytes)
                    writeObjectField("partialMerkleTree", value.partialMerkleTree)
                }
            }
        }

        override val deserializer = object : StdDeserializer<TransactionSignature>(type) {
            override fun deserialize(parser: JsonParser, context: DeserializationContext): TransactionSignature {
                val mapper = parser.codec as ObjectMapper
                val json = mapper.readTree<JsonNode>(parser)
                val by = mapper.convertValue<PublicKey>(json["by"])
                val signatureMetadata = json.get<SignatureMetadata>("signatureMetadata", JsonNode::isObject, mapper, parser)
                val bytes = json.get<ByteArray>("bytes", JsonNode::isObject, mapper, parser)
                val partialMerkleTree = json.get<PartialMerkleTree>("partialMerkleTree", JsonNode::isObject, mapper, parser)

                return TransactionSignature(bytes, by, signatureMetadata, partialMerkleTree)
            }
        }
    }

    private object SignedTransactionSerde : JsonSerde<SignedTransaction> {
        override val type: Class<SignedTransaction> = SignedTransaction::class.java

        override val serializer = object : StdSerializer<SignedTransaction>(type) {
            override fun serialize(value: SignedTransaction, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.jsonObject {
                    writeObjectField("txBits", value.txBits.bytes)
                    writeObjectField("signatures", value.sigs)
                }
            }
        }

        override val deserializer = object : StdDeserializer<SignedTransaction>(type) {
            override fun deserialize(parser: JsonParser, context: DeserializationContext): SignedTransaction {
                val mapper = parser.codec as ObjectMapper
                val json = mapper.readTree<JsonNode>(parser)

                val txBits = json.get<ByteArray>("txBits", JsonNode::isTextual, mapper, parser)
                val signatures = json.get<TransactionSignatures>("signatures", JsonNode::isArray, mapper, parser)

                return SignedTransaction(SerializedBytes(txBits), signatures)
            }
        }

        private class TransactionSignatures : ArrayList<TransactionSignature>()
    }



    //
    // The following should not have been made public and are thus deprecated with warnings.
    //

    @Deprecated("No longer used as jackson already has a toString serializer",
            replaceWith = ReplaceWith("com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance"))
    object ToStringSerializer : JsonSerializer<Any>() {
        override fun serialize(obj: Any, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    @Deprecated("This is an internal class, do not use")
    object AnonymousPartySerializer : JsonSerializer<AnonymousParty>() {
        override fun serialize(value: AnonymousParty, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeObject(value.owningKey)
        }
    }

    @Deprecated("This is an internal class, do not use")
    object AnonymousPartyDeserializer : JsonDeserializer<AnonymousParty>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): AnonymousParty {
            return AnonymousParty(parser.readValueAs(PublicKey::class.java))
        }
    }

    @Deprecated("This is an internal class, do not use")
    object PartySerializer : JsonSerializer<Party>() {
        override fun serialize(value: Party, generator: JsonGenerator, provider: SerializerProvider) {
            // TODO Add configurable option to output this as an object which includes the owningKey
            generator.writeObject(value.name)
        }
    }

    @Deprecated("This is an internal class, do not use")
    object PartyDeserializer : JsonDeserializer<Party>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Party {
            val mapper = parser.codec as PartyObjectMapper
            // The comma character is invalid in Base58, and required as a separator for X.500 names. As Corda
            // X.500 names all involve at least three attributes (organisation, locality, country), they must
            // include a comma. As such we can use it as a distinguisher between the two types.
            return if ("," in parser.text) {
                val principal = CordaX500Name.parse(parser.text)
                mapper.wellKnownPartyFromX500Name(principal) ?: throw JsonParseException(parser, "Could not find a Party with name $principal")
            } else {
                val nameMatches = mapper.partiesFromName(parser.text)
                if (nameMatches.isEmpty()) {
                    val publicKey = parser.readValueAs<PublicKey>()
                    mapper.partyFromKey(publicKey)
                            ?: throw JsonParseException(parser, "Could not find a Party with key ${publicKey.toStringShort()}")
                } else if (nameMatches.size == 1) {
                    nameMatches.first()
                } else {
                    throw JsonParseException(parser, "Ambiguous name match '${parser.text}': could be any of " +
                            nameMatches.map { it.name }.joinToString(" ... or ... "))
                }
            }
        }
    }

    @Deprecated("This is an internal class, do not use")
    // This is no longer used
    object CordaX500NameSerializer : JsonSerializer<CordaX500Name>() {
        override fun serialize(obj: CordaX500Name, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    @Deprecated("This is an internal class, do not use")
    object CordaX500NameDeserializer : JsonDeserializer<CordaX500Name>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): CordaX500Name {
            return try {
                CordaX500Name.parse(parser.text)
            } catch (e: IllegalArgumentException) {
                throw JsonParseException(parser, "Invalid Corda X.500 name ${parser.text}: ${e.message}", e)
            }
        }
    }

    @Deprecated("This is an internal class, do not use")
    // This is no longer used
    object NodeInfoSerializer : JsonSerializer<NodeInfo>() {
        override fun serialize(value: NodeInfo, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(Base58.encode(value.serialize().bytes))
        }
    }

    @Deprecated("This is an internal class, do not use")
    object NodeInfoDeserializer : JsonDeserializer<NodeInfo>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): NodeInfo {
            val mapper = parser.codec as PartyObjectMapper
            val party = parser.readValueAs<AbstractParty>()
            return mapper.nodeInfoFromParty(party) ?: throw JsonParseException(parser, "Cannot find node with $party")
        }
    }

    @Deprecated("This is an internal class, do not use")
    // This is no longer used
    object SecureHashSerializer : JsonSerializer<SecureHash>() {
        override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(obj.toString())
        }
    }

    /**
     * Implemented as a class so that we can instantiate for T.
     */
    @Deprecated("This is an internal class, do not use")
    class SecureHashDeserializer<T : SecureHash> : JsonDeserializer<T>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): T {
            try {
                return uncheckedCast(SecureHash.parse(parser.text))
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid hash ${parser.text}: ${e.message}")
            }
        }
    }

    @Deprecated("This is an internal class, do not use")
    object PublicKeySerializer : JsonSerializer<PublicKey>() {
        override fun serialize(value: PublicKey, generator: JsonGenerator, provider: SerializerProvider) {
            generator.writeString(value.toBase58String())
        }
    }

    @Deprecated("This is an internal class, do not use")
    object PublicKeyDeserializer : JsonDeserializer<PublicKey>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): PublicKey {
            return try {
                parsePublicKeyBase58(parser.text)
            } catch (e: Exception) {
                throw JsonParseException(parser, "Invalid public key ${parser.text}: ${e.message}")
            }
        }
    }

    @Deprecated("This is an internal class, do not use")
    // This is no longer used
    object AmountSerializer : JsonSerializer<Amount<*>>() {
        override fun serialize(value: Amount<*>, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    @Deprecated("This is an internal class, do not use")
    object AmountDeserializer : JsonDeserializer<Amount<*>>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): Amount<*> {
            return if (parser.currentToken == JsonToken.VALUE_STRING) {
                Amount.parseCurrency(parser.text)
            } else {
                try {
                    val tree = parser.readValueAsTree<ObjectNode>()
                    val quantity = tree["quantity"].apply { require(canConvertToLong()) }
                    val token = tree["token"]
                    // Attempt parsing as a currency token. TODO: This needs thought about how to extend to other token types.
                    val currency = (parser.codec as ObjectMapper).convertValue<Currency>(token)
                    Amount(quantity.longValue(), currency)
                } catch (e: Exception) {
                    throw JsonParseException(parser, "Invalid amount", e)
                }
            }
        }
    }

    @Deprecated("This is an internal class, do not use")
    object OpaqueBytesDeserializer : JsonDeserializer<OpaqueBytes>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): OpaqueBytes {
            return OpaqueBytes(parser.binaryValue)
        }
    }

    @Deprecated("This is an internal class, do not use")
    object OpaqueBytesSerializer : JsonSerializer<OpaqueBytes>() {
        override fun serialize(value: OpaqueBytes, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeBinary(value.bytes)
        }
    }

    @Deprecated("This is an internal class, do not use")
    @Suppress("unused")
    abstract class SignedTransactionMixin {
        @JsonIgnore abstract fun getTxBits(): SerializedBytes<CoreTransaction>
        @JsonProperty("signatures") protected abstract fun getSigs(): List<TransactionSignature>
        @JsonProperty protected abstract fun getTransaction(): CoreTransaction  // TODO It seems this should be coreTransaction
        @JsonIgnore abstract fun getTx(): WireTransaction
        @JsonIgnore abstract fun getNotaryChangeTx(): NotaryChangeWireTransaction
        @JsonIgnore abstract fun getInputs(): List<StateRef>
        @JsonIgnore abstract fun getNotary(): Party?
        @JsonIgnore abstract fun getId(): SecureHash
        @JsonIgnore abstract fun getRequiredSigningKeys(): Set<PublicKey>
    }

    @Deprecated("This is an internal class, do not use")
    @Suppress("unused")
    abstract class WireTransactionMixin {
        @JsonIgnore abstract fun getMerkleTree(): MerkleTree
        @JsonIgnore abstract fun getAvailableComponents(): List<Any>
        @JsonIgnore abstract fun getAvailableComponentHashes(): List<SecureHash>
        @JsonIgnore abstract fun getOutputStates(): List<ContractState>
    }
}
