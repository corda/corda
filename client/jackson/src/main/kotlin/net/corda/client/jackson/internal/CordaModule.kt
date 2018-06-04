@file:Suppress("DEPRECATION")

package net.corda.client.jackson.internal

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.google.common.primitives.Booleans
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.crypto.PartialMerkleTree.PartialTree
import net.corda.core.identity.*
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.kotlinObjectInstance
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.*
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.parseAsHex
import net.corda.core.utilities.toHexString
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.constructorForDeserialization
import net.corda.serialization.internal.amqp.hasCordaSerializable
import net.corda.serialization.internal.amqp.propertiesForSerialization
import java.security.PublicKey
import java.security.cert.CertPath
import java.time.Instant

class CordaModule : SimpleModule("corda-core") {
    override fun setupModule(context: SetupContext) {
        super.setupModule(context)

        context.addBeanSerializerModifier(CordaSerializableBeanSerializerModifier())

        context.setMixInAnnotations(PartyAndCertificate::class.java, PartyAndCertificateMixin::class.java)
        context.setMixInAnnotations(NetworkHostAndPort::class.java, NetworkHostAndPortMixin::class.java)
        context.setMixInAnnotations(CordaX500Name::class.java, CordaX500NameMixin::class.java)
        context.setMixInAnnotations(Amount::class.java, AmountMixin::class.java)
        context.setMixInAnnotations(AbstractParty::class.java, AbstractPartyMixin::class.java)
        context.setMixInAnnotations(AnonymousParty::class.java, AnonymousPartyMixin::class.java)
        context.setMixInAnnotations(Party::class.java, PartyMixin::class.java)
        context.setMixInAnnotations(PublicKey::class.java, PublicKeyMixin::class.java)
        context.setMixInAnnotations(ByteSequence::class.java, ByteSequenceMixin::class.java)
        context.setMixInAnnotations(SecureHash.SHA256::class.java, SecureHashSHA256Mixin::class.java)
        context.setMixInAnnotations(SecureHash::class.java, SecureHashSHA256Mixin::class.java)
        context.setMixInAnnotations(SerializedBytes::class.java, SerializedBytesMixin::class.java)
        context.setMixInAnnotations(DigitalSignature.WithKey::class.java, ByteSequenceWithPropertiesMixin::class.java)
        context.setMixInAnnotations(DigitalSignatureWithCert::class.java, ByteSequenceWithPropertiesMixin::class.java)
        context.setMixInAnnotations(TransactionSignature::class.java, ByteSequenceWithPropertiesMixin::class.java)
        context.setMixInAnnotations(SignedTransaction::class.java, SignedTransactionMixin::class.java)
        context.setMixInAnnotations(WireTransaction::class.java, WireTransactionMixin::class.java)
        context.setMixInAnnotations(TransactionState::class.java, TransactionStateMixin::class.java)
        context.setMixInAnnotations(Command::class.java, CommandMixin::class.java)
        context.setMixInAnnotations(TimeWindow::class.java, TimeWindowMixin::class.java)
        context.setMixInAnnotations(PrivacySalt::class.java, PrivacySaltMixin::class.java)
        context.setMixInAnnotations(SignatureScheme::class.java, SignatureSchemeMixin::class.java)
        context.setMixInAnnotations(SignatureMetadata::class.java, SignatureMetadataMixin::class.java)
        context.setMixInAnnotations(PartialTree::class.java, PartialTreeMixin::class.java)
        context.setMixInAnnotations(NodeInfo::class.java, NodeInfoMixin::class.java)
    }
}

/**
 * Use the same properties that AMQP serialization uses if the POJO is @CordaSerializable
 */
private class CordaSerializableBeanSerializerModifier : BeanSerializerModifier() {
    // We need to pass in a SerializerFactory when scanning for properties, but don't actually do any serialisation so any will do.
    private val serializerFactory = SerializerFactory(AllWhitelist, javaClass.classLoader)

    override fun changeProperties(config: SerializationConfig,
                                  beanDesc: BeanDescription,
                                  beanProperties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        val beanClass = beanDesc.beanClass
        if (hasCordaSerializable(beanClass) && beanClass.kotlinObjectInstance == null) {
            val ctor = constructorForDeserialization(beanClass)
            val amqpProperties = propertiesForSerialization(ctor, beanClass, serializerFactory)
                    .serializationOrder
                    .map { it.serializer.name }
            val propertyRenames = beanDesc.findProperties().associateBy({ it.name }, { it.internalName })
            (amqpProperties - propertyRenames.values).let {
                check(it.isEmpty()) { "Jackson didn't provide serialisers for $it" }
            }
            beanProperties.removeIf { propertyRenames[it.name] !in amqpProperties }
        }
        return beanProperties
    }
}

@ToStringSerialize
@JsonDeserialize(using = NetworkHostAndPortDeserializer::class)
private interface NetworkHostAndPortMixin

private class NetworkHostAndPortDeserializer : SimpleDeserializer<NetworkHostAndPort>({ NetworkHostAndPort.parse(text) })

@JsonSerialize(using = PartyAndCertificateSerializer::class)
// TODO Add deserialization which follows the same lookup logic as Party
private interface PartyAndCertificateMixin

private class PartyAndCertificateSerializer : JsonSerializer<PartyAndCertificate>() {
    override fun serialize(value: PartyAndCertificate, gen: JsonGenerator, serializers: SerializerProvider) {
        val mapper = gen.codec as JacksonSupport.PartyObjectMapper
        if (mapper.isFullParties) {
            gen.writeObject(PartyAndCertificateJson(value.name, value.certPath))
        } else {
            gen.writeObject(value.party)
        }
    }
}

private class PartyAndCertificateJson(val name: CordaX500Name, val certPath: CertPath)

@JsonSerialize(using = SignedTransactionSerializer::class)
@JsonDeserialize(using = SignedTransactionDeserializer::class)
private interface SignedTransactionMixin

private class SignedTransactionSerializer : JsonSerializer<SignedTransaction>() {
    override fun serialize(value: SignedTransaction, gen: JsonGenerator, serializers: SerializerProvider) {
        val core = value.coreTransaction
        val stxJson = when (core) {
            is WireTransaction -> StxJson(wire = core, signatures = value.sigs)
            is FilteredTransaction -> StxJson(filtered = core, signatures = value.sigs)
            is NotaryChangeWireTransaction -> StxJson(notaryChangeWire = core, signatures = value.sigs)
            is ContractUpgradeWireTransaction -> StxJson(contractUpgradeWire = core, signatures = value.sigs)
            is ContractUpgradeFilteredTransaction -> StxJson(contractUpgradeFiltered = core, signatures = value.sigs)
            else -> throw IllegalArgumentException("Don't know about ${core.javaClass}")
        }
        gen.writeObject(stxJson)
    }
}

private class SignedTransactionDeserializer : JsonDeserializer<SignedTransaction>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SignedTransaction {
        val wrapper = parser.readValueAs<StxJson>()
        val core = wrapper.run { wire ?: filtered ?: notaryChangeWire ?: contractUpgradeWire ?: contractUpgradeFiltered!! }
        return SignedTransaction(core, wrapper.signatures)
    }
}

@JsonInclude(Include.NON_NULL)
private data class StxJson(
        val wire: WireTransaction? = null,
        val filtered: FilteredTransaction? = null,
        val notaryChangeWire: NotaryChangeWireTransaction? = null,
        val contractUpgradeWire: ContractUpgradeWireTransaction? = null,
        val contractUpgradeFiltered: ContractUpgradeFilteredTransaction? = null,
        val signatures: List<TransactionSignature>
) {
    init {
        val count = Booleans.countTrue(wire != null, filtered != null, notaryChangeWire != null, contractUpgradeWire != null, contractUpgradeFiltered != null)
        require(count == 1) { this }
    }
}

@JsonSerialize(using = WireTransactionSerializer::class)
@JsonDeserialize(using = WireTransactionDeserializer::class)
private interface WireTransactionMixin

private class WireTransactionSerializer : JsonSerializer<WireTransaction>() {
    override fun serialize(value: WireTransaction, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeObject(WireTransactionJson(
                value.id,
                value.notary,
                value.inputs,
                value.outputs,
                value.commands,
                value.timeWindow,
                value.attachments,
                value.privacySalt
        ))
    }
}

private class WireTransactionDeserializer : JsonDeserializer<WireTransaction>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): WireTransaction {
        val wrapper = parser.readValueAs<WireTransactionJson>()
        val componentGroups = WireTransaction.createComponentGroups(
                wrapper.inputs,
                wrapper.outputs,
                wrapper.commands,
                wrapper.attachments,
                wrapper.notary,
                wrapper.timeWindow
        )
        return WireTransaction(componentGroups, wrapper.privacySalt)
    }
}

private class WireTransactionJson(val id: SecureHash,
                                  val notary: Party?,
                                  val inputs: List<StateRef>,
                                  val outputs: List<TransactionState<*>>,
                                  val commands: List<Command<*>>,
                                  val timeWindow: TimeWindow?,
                                  val attachments: List<SecureHash>,
                                  val privacySalt: PrivacySalt)

private interface TransactionStateMixin {
    @get:JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    val data: ContractState
    @get:JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    val constraint: AttachmentConstraint
}

private interface CommandMixin {
    @get:JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    val value: CommandData
}

@JsonDeserialize(using = TimeWindowDeserializer::class)
private interface TimeWindowMixin

private class TimeWindowDeserializer : JsonDeserializer<TimeWindow>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): TimeWindow {
        return parser.readValueAs<TimeWindowJson>().run {
            when {
                fromTime != null && untilTime != null -> TimeWindow.between(fromTime, untilTime)
                fromTime != null -> TimeWindow.fromOnly(fromTime)
                untilTime != null -> TimeWindow.untilOnly(untilTime)
                else -> throw JsonParseException(parser, "Neither fromTime nor untilTime exists for TimeWindow")
            }
        }
    }
}

private data class TimeWindowJson(val fromTime: Instant?, val untilTime: Instant?)

@JsonSerialize(using = PrivacySaltSerializer::class)
@JsonDeserialize(using = PrivacySaltDeserializer::class)
private interface PrivacySaltMixin

private class PrivacySaltSerializer : JsonSerializer<PrivacySalt>() {
    override fun serialize(value: PrivacySalt, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.bytes.toHexString())
    }
}

private class PrivacySaltDeserializer : SimpleDeserializer<PrivacySalt>({ PrivacySalt(text.parseAsHex()) })

// TODO Add a lookup function by number ID in Crypto
private val signatureSchemesByNumberID = Crypto.supportedSignatureSchemes().associateBy { it.schemeNumberID }

@JsonSerialize(using = SignatureMetadataSerializer::class)
@JsonDeserialize(using = SignatureMetadataDeserializer::class)
private interface SignatureMetadataMixin

private class SignatureMetadataSerializer : JsonSerializer<SignatureMetadata>() {
    override fun serialize(value: SignatureMetadata, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.jsonObject {
            writeNumberField("platformVersion", value.platformVersion)
            writeObjectField("scheme", value.schemeNumberID.let { signatureSchemesByNumberID[it] ?: it })
        }
    }
}

private class SignatureMetadataDeserializer : JsonDeserializer<SignatureMetadata>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SignatureMetadata {
        val json = parser.readValueAsTree<ObjectNode>()
        val scheme = json["scheme"]
        val schemeNumberID = if (scheme is IntNode) {
            scheme.intValue()
        } else {
            Crypto.findSignatureScheme(scheme.textValue()).schemeNumberID
        }
        return SignatureMetadata(json["platformVersion"].intValue(), schemeNumberID)
    }
}

@JsonSerialize(using = PartialTreeSerializer::class)
@JsonDeserialize(using = PartialTreeDeserializer::class)
private interface PartialTreeMixin

private class PartialTreeSerializer : JsonSerializer<PartialTree>() {
    override fun serialize(value: PartialTree, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeObject(convert(value))
    }

    private fun convert(tree: PartialTree): PartialTreeJson {
        return when (tree) {
            is PartialTree.IncludedLeaf -> PartialTreeJson(includedLeaf = tree.hash)
            is PartialTree.Leaf -> PartialTreeJson(leaf = tree.hash)
            is PartialTree.Node -> PartialTreeJson(left = convert(tree.left), right = convert(tree.right))
            else -> throw IllegalArgumentException("Don't know how to serialize $tree")
        }
    }
}

private class PartialTreeDeserializer : JsonDeserializer<PartialTree>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): PartialTree {
        return convert(parser.readValueAs(PartialTreeJson::class.java))
    }

    private fun convert(wrapper: PartialTreeJson): PartialTree {
        return wrapper.run {
            when {
                includedLeaf != null -> PartialTree.IncludedLeaf(includedLeaf)
                leaf != null -> PartialTree.Leaf(leaf)
                else -> PartialTree.Node(convert(left!!), convert(right!!))
            }
        }
    }
}

@JsonInclude(Include.NON_NULL)
private class PartialTreeJson(val includedLeaf: SecureHash? = null,
                              val leaf: SecureHash? = null,
                              val left: PartialTreeJson? = null,
                              val right: PartialTreeJson? = null) {
    init {
        if (includedLeaf != null) {
            require(leaf == null && left == null && right == null)
        } else if (leaf != null) {
            require(left == null && right == null)
        } else {
            require(left != null && right != null)
        }
    }
}

@JsonSerialize(using = SignatureSchemeSerializer::class)
@JsonDeserialize(using = SignatureSchemeDeserializer::class)
private interface SignatureSchemeMixin

private class SignatureSchemeSerializer : JsonSerializer<SignatureScheme>() {
    override fun serialize(value: SignatureScheme, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.schemeCodeName)
    }
}

private class SignatureSchemeDeserializer : JsonDeserializer<SignatureScheme>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SignatureScheme {
        return if (parser.currentToken == JsonToken.VALUE_NUMBER_INT) {
            signatureSchemesByNumberID[parser.intValue] ?: throw JsonParseException(parser, "Unable to find SignatureScheme ${parser.text}")
        } else {
            Crypto.findSignatureScheme(parser.text)
        }
    }
}

@JsonSerialize(using = SerializedBytesSerializer::class)
@JsonDeserialize(using = SerializedBytesDeserializer::class)
private class SerializedBytesMixin

private class SerializedBytesSerializer : JsonSerializer<SerializedBytes<*>>() {
    override fun serialize(value: SerializedBytes<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        val deserialized = value.deserialize<Any>()
        gen.jsonObject {
            writeStringField("class", deserialized.javaClass.name)
            writeObjectField("deserialized", deserialized)
        }
    }
}

private class SerializedBytesDeserializer : JsonDeserializer<SerializedBytes<*>>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): SerializedBytes<Any> {
        return if (parser.currentToken == JsonToken.START_OBJECT) {
            val mapper = parser.codec as ObjectMapper
            val json = parser.readValueAsTree<ObjectNode>()
            val clazz = context.findClass(json["class"].textValue())
            val pojo = mapper.convertValue(json["deserialized"], clazz)
            pojo.serialize()
        } else {
            SerializedBytes(parser.binaryValue)
        }
    }
}

@JsonDeserialize(using = JacksonSupport.PartyDeserializer::class)
private interface AbstractPartyMixin

@JsonSerialize(using = JacksonSupport.AnonymousPartySerializer::class)
@JsonDeserialize(using = JacksonSupport.AnonymousPartyDeserializer::class)
private interface AnonymousPartyMixin

@JsonSerialize(using = JacksonSupport.PartySerializer::class)
private interface PartyMixin

@ToStringSerialize
@JsonDeserialize(using = JacksonSupport.CordaX500NameDeserializer::class)
private interface CordaX500NameMixin

@JsonDeserialize(using = JacksonSupport.NodeInfoDeserializer::class)
private interface NodeInfoMixin

@ToStringSerialize
@JsonDeserialize(using = JacksonSupport.SecureHashDeserializer::class)
private interface SecureHashSHA256Mixin

@JsonSerialize(using = JacksonSupport.PublicKeySerializer::class)
@JsonDeserialize(using = JacksonSupport.PublicKeyDeserializer::class)
private interface PublicKeyMixin

@ToStringSerialize
@JsonDeserialize(using = JacksonSupport.AmountDeserializer::class)
private interface AmountMixin

@JsonDeserialize(using = JacksonSupport.OpaqueBytesDeserializer::class)
private interface ByteSequenceMixin {
    @Suppress("unused")
    @JsonValue
    fun copyBytes(): ByteArray
}

@JsonSerialize
@JsonDeserialize
private interface ByteSequenceWithPropertiesMixin {
    @Suppress("unused")
    @JsonValue(false)
    fun copyBytes(): ByteArray
}
