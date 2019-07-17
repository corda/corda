package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Stack
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.google.common.primitives.Booleans
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.internal.jsonObject
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.*
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.NodeStartup
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.*
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.serialization.SerializeAsTokenContextImpl
import net.corda.nodeapi.internal.serialization.withTokenContext
import java.lang.reflect.Field
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CheckpointDumper(private val checkpointStorage: CheckpointStorage, private val database: CordaPersistence, private val serviceHub: ServiceHubInternal) {
    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(UTC)
        private val log = contextLogger()
    }

    private val lock = AtomicInteger(0)

    private lateinit var checkpointSerializationContext: SerializeAsTokenContextImpl
    private lateinit var writer: ObjectWriter

    fun start(tokenizableServices: List<Any>) {
        checkpointSerializationContext = SerializeAsTokenContextImpl(
                tokenizableServices,
                SerializationDefaults.SERIALIZATION_FACTORY,
                SerializationDefaults.CHECKPOINT_CONTEXT,
                serviceHub
        )

        val mapper = JacksonSupport.createNonRpcMapper()
        mapper.registerModule(SimpleModule().apply {
            setSerializerModifier(CheckpointDumperBeanModifier)
            addSerializer(FlowSessionImplSerializer)
            addSerializer(MapSerializer)
            addSerializer(AttachmentSerializer)
            setMixInAnnotation(FlowLogic::class.java, FlowLogicMixin::class.java)
            setMixInAnnotation(SessionId::class.java, SessionIdMixin::class.java)
            setMixInAnnotation(SignedTransaction::class.java, SignedTransactionMixin::class.java)
            setMixInAnnotation(WireTransaction::class.java, WireTransactionMixin::class.java)
            setMixInAnnotation(FlowSessionInternal::class.java, FlowSessionInternalMixin::class.java)
        })
        val prettyPrinter = DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        }
        writer = mapper.writer(prettyPrinter)
    }

    fun dump() {
        val now = serviceHub.clock.instant()
        val file = serviceHub.configuration.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME / "checkpoints_dump-${TIME_FORMATTER.format(now)}.zip"
        try {
            if (lock.getAndIncrement() == 0 && !file.exists()) {
                database.transaction {
                    checkpointStorage.getAllCheckpoints().use { stream ->
                        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                            stream.forEach { (runId, serialisedCheckpoint) ->
                                val checkpoint = serialisedCheckpoint.serializedFiber.deserialize(
                                        context = SerializationDefaults.CHECKPOINT_CONTEXT.withTokenContext(checkpointSerializationContext)
                                )
                                val json = checkpoint.toJson(runId, now)
                                try {
                                    val jsonBytes = writer.writeValueAsBytes(json)
                                    zip.putNextEntry(ZipEntry("${json.topLevelFlowClass.simpleName}-$runId.json"))
                                    zip.write(jsonBytes)
                                    zip.closeEntry()
                                } catch (e: Exception) {
                                    log.info("Failed to output json checkpoint dump for ${json.topLevelFlowClass.simpleName}-$runId", e)
                                }
                            }
                        }
                    }
                }
            } else {
                log.info("Flow dump already in progress, skipping current call")
            }
        } finally {
            lock.decrementAndGet()
        }
    }

    private fun FlowStateMachineImpl<*>.toJson(id: String, now: Instant): CheckpointJson {
        val (fiber, flowLogic) = this to this.logic
        // Poke into Quasar's stack and find the object references to the sub-flows so that we can correctly get the current progress
        // step for each sub-call.
        val stackObjects = fiber.declaredField<Stack>("stack").value
                .declaredField<Array<*>>("dataObject").value
                .filterIsInstance<FlowLogic<*>>()
                .toSet()
                .map {
                    FlowCall(
                            it.javaClass,
                            if (it.progressTracker?.currentStep == ProgressTracker.UNSTARTED) null else it.progressTracker?.currentStep?.label,
                            it
                    )
                }

        val activeSessions = openSessions.mapNotNull { (key, session) ->
            ActiveSession(
                    key.second,
                    session.ourSessionId,
                    session.receivedMessages.toList(),
                    (session.state as? FlowSessionState.Initiated)?.peerSessionId
            )
        }

        return CheckpointJson(
                id,
                flowLogic.javaClass,
                stackObjects,
                fiber.waitingForResponse?.toSuspendedOn(suspendedTimestamp(), now),
                this.context.origin.toOrigin(),
                ourIdentity,
                activeSessions
        )
    }

    private fun FlowStateMachineImpl<*>.suspendedTimestamp(): Instant = context.trace.invocationId.timestamp

    @Suppress("unused")
    private class FlowCall(val flowClass: Class<*>, val progressStep: String?, val flowLogic: FlowLogic<*>)

    @Suppress("unused")
    @JsonInclude(Include.NON_NULL)
    private class Origin(
            val rpc: String? = null,
            val peer: CordaX500Name? = null,
            val service: String? = null,
            val scheduled: ScheduledStateRef? = null,
            val shell: InvocationOrigin.Shell? = null
    )

    private fun InvocationOrigin.toOrigin(): Origin {
        return when (this) {
            is InvocationOrigin.RPC -> Origin(rpc = actor.id.value)
            is InvocationOrigin.Peer -> Origin(peer = party)
            is InvocationOrigin.Service -> Origin(service = serviceClassName)
            is InvocationOrigin.Scheduled -> Origin(scheduled = scheduledState)
            is InvocationOrigin.Shell -> Origin(shell = this)
        }
    }

    @Suppress("unused")
    private class CheckpointJson(
            val flowId: String,
            val topLevelFlowClass: Class<FlowLogic<*>>,
            val flowCallStack: List<FlowCall>,
            val suspendedOn: SuspendedOn?,
            val origin: Origin,
            val ourIdentity: Party,
            val activeSessions: List<ActiveSession>
    )

    @Suppress("unused")
    @JsonInclude(Include.NON_NULL)
    private class SuspendedOn(
            val receive: ReceiveOnly? = null,
            val sendAndReceive: SendAndReceiveJson? = null,
            val waitForLedgerCommit: SecureHash? = null,
            val receiveAll: ReceiveAll? = null
    ) {
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
        lateinit var suspendedTimestamp: Instant
        var secondsSpentWaiting: Long = 0
    }

    @Suppress("unused")
    private class SendAndReceiveJson(val session: FlowSession, val sentPayloadType: Class<*>?, val sentPayload: Any?, val receivedPayloadType: Class<*>?)

    private fun WaitingRequest.toSuspendedOn(suspendedTimestamp: Instant, now: Instant): SuspendedOn {
        fun SendAndReceive.toJson(): SendAndReceiveJson {
            val payload = when (this.message) {
                is ExistingSessionMessage -> (message.payload as? DataSessionMessage)?.payload?.deserialize()
                is InitialSessionMessage -> message.firstPayload?.deserialize()
            }
            return SendAndReceiveJson(session.flowSession, payload?.javaClass, payload, userReceiveType)
        }
        return when (this) {
            is ReceiveOnly -> SuspendedOn(receive = this)
            is SendAndReceive -> SuspendedOn(sendAndReceive = this.toJson())
            is WaitForLedgerCommit -> SuspendedOn(waitForLedgerCommit = hash)
            is ReceiveAll -> SuspendedOn(receiveAll = this)
            // should not be possible but the compiler wont let it through without this case
            else -> SuspendedOn()

        }.also {
            it.suspendedTimestamp = suspendedTimestamp
            it.secondsSpentWaiting = TimeUnit.MILLISECONDS.toSeconds(Duration.between(suspendedTimestamp, now).toMillis())
        }
    }

    @Suppress("unused")
    private class ActiveSession(
            val peer: Party,
            val ourSessionId: SessionId,
            val receivedMessages: List<ReceivedSessionMessage>,
            val peerSessionId: SessionId?
    )

    @Suppress("unused")
    private interface SessionIdMixin {
        @get:JsonValue
        val toLong: Long
    }

    @JsonAutoDetect(getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
    @JsonIgnoreProperties("flowUsedForSessions")
    private interface FlowLogicMixin

    private object CheckpointDumperBeanModifier : BeanSerializerModifier() {
        override fun changeProperties(config: SerializationConfig,
                                      beanDesc: BeanDescription,
                                      beanProperties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
            // Remove references to any node singletons
            beanProperties.removeIf { it.type.isTypeOrSubTypeOf(SerializeAsToken::class.java) }
            if (FlowLogic::class.java.isAssignableFrom(beanDesc.beanClass)) {
                beanProperties.removeIf {
                    it.type.isTypeOrSubTypeOf(ProgressTracker::class.java) || it.name == "_stateMachine" || it.name == "deprecatedPartySessionMap"
                }
            }
            return beanProperties
        }
    }

    private object FlowSessionImplSerializer : JsonSerializer<FlowSessionImpl>() {
        override fun serialize(value: FlowSessionImpl, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.jsonObject {
                writeObjectField("peer", value.counterparty)
            }
        }

        override fun handledType(): Class<FlowSessionImpl> = FlowSessionImpl::class.java
    }

    private object AttachmentSerializer : JsonSerializer<Attachment>() {
        override fun serialize(value: Attachment, gen: JsonGenerator, serializers: SerializerProvider) = gen.writeObject(value.id)
        override fun handledType(): Class<Attachment> = Attachment::class.java
    }

    private object MapSerializer : JsonSerializer<Map<Any, Any>>() {
        override fun serialize(map: Map<Any, Any>, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeStartArray(map.size)
            map.forEach { key, value ->
                gen.jsonObject {
                    writeObjectField("key", key)
                    writeObjectField("value", value)
                }
            }
            gen.writeEndArray()
        }

        override fun handledType(): Class<Map<Any, Any>> = uncheckedCast(Map::class.java)
    }

    @JsonSerialize(using = SignedTransactionSerializer::class)
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

    private class WireTransactionJson(
            val id: SecureHash,
            val notary: Party?,
            val inputs: List<StateRef>,
            val outputs: List<TransactionState<*>>,
            val commands: List<Command<*>>,
            val timeWindow: TimeWindow?,
            val attachments: List<SecureHash>,
            val privacySalt: PrivacySalt
    )

    @JsonIgnoreProperties("flow", "fiber")
    @JsonAutoDetect(getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
    private interface FlowSessionInternalMixin
}

// taken from newer corda version due to parent classes not being searched for fields
// needed to access `stack` inside `FlowStateMachineImpl`
private fun <T> Any.declaredField(name: String): DeclaredField<T> = DeclaredField(javaClass, name, this)

private class DeclaredField<T>(clazz: Class<*>, name: String, private val receiver: Any?) {
    private val javaField = findField(name, clazz)
    var value: T
        get() {
            synchronized(this) {
                return javaField.accessible { uncheckedCast<Any?, T>(get(receiver)) }
            }
        }
        set(value) {
            synchronized(this) {
                javaField.accessible {
                    set(receiver, value)
                }
            }
        }
    val name: String = javaField.name

    private fun <RESULT> Field.accessible(action: Field.() -> RESULT): RESULT {
        val accessible = isAccessible
        isAccessible = true
        try {
            return action(this)
        } finally {
            isAccessible = accessible
        }
    }

    @Throws(NoSuchFieldException::class)
    private fun findField(fieldName: String, clazz: Class<*>?): Field {
        if (clazz == null) {
            throw NoSuchFieldException(fieldName)
        }
        return try {
            return clazz.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            findField(fieldName, clazz.superclass)
        }
    }
}