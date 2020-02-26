package net.corda.node.services.persistence

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowIORequest
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.Checkpoint.FlowStatus
import net.corda.node.services.statemachine.FlowState
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
import org.hibernate.annotations.Type
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToOne

/**
 * Simple checkpoint key value storage in DB.
 */
class DBCheckpointStorage : CheckpointStorage {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    enum class StartReason {
        RPC, FLOW, SERVICE, SCHEDULED, INITIATED
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
    class DBFlowCheckpoint(
            @Id
            @Column(name = "id", length = 64, nullable = false)
            var id: String,

            @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
            @JoinColumn(name = "checkpoint_blob_id", referencedColumnName = "id")
            var blob: DBFlowCheckpointBlob?,

            @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
            @JoinColumn(name = "result_id", referencedColumnName = "id")
            var result: DBFlowResult?,

            @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
            @JoinColumn(name = "error_id", referencedColumnName = "id")
            var exceptionDetails: DBFlowException?,

            @OneToOne(fetch = FetchType.LAZY, optional = true)
            @JoinColumn(name = "flow_id", referencedColumnName = "invocation_id", nullable = true)
            var flowMetadata: DBFlowMetadata?,

            @Column(name = "status")
            var status: FlowStatus,

            @Column(name = "compatible")
            var compatible: Boolean,

            @Column(name = "progress_step")
            var progressStep: String,

            @Column(name = "flow_io_request")
            var ioRequestType: Class<FlowIORequest<*>>,

            @Column(name = "timestamp", nullable = false)
            var checkpointInstant: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints_blobs")
    class DBFlowCheckpointBlob(
            @Id
            @GeneratedValue(strategy = GenerationType.SEQUENCE)
            @Column(name = "id", nullable = false)
            private var id: Long = 0,

            @Type(type = "corda-blob")
            @Column(name = "checkpoint_value", nullable = false)
            var checkpoint: ByteArray = EMPTY_BYTE_ARRAY,

            @Type(type = "corda-blob")
            @Column(name = "flow_state", nullable = false)
            var flowStack: ByteArray = EMPTY_BYTE_ARRAY,

            @Column(name = "timestamp")
            var persistedInstant: Instant? = null,

            @Column(name = "hmac")
            var hmac: ByteArray
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_results")
    class DBFlowResult(
            @Id
            @Column(name = "id", nullable = false)
            @GeneratedValue(strategy = GenerationType.SEQUENCE)
            private var id: Long = 0,

            @Type(type = "corda-blob")
            @Column(name = "result_value", nullable = false)
            var checkpoint: ByteArray = EMPTY_BYTE_ARRAY,

            @Column(name = "timestamp")
            val persistedInstant: Instant? = null
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_exceptions")
    class DBFlowException(
            @Id
            @Column(name = "id", nullable = false)
            @GeneratedValue(strategy = GenerationType.SEQUENCE)
            private var id: Long = 0,

            @Column(name = "type", nullable = false)
            var type: Class<Exception>,

            @Type(type = "corda-blob")
            @Column(name = "exception_value", nullable = false)
            var value: ByteArray = EMPTY_BYTE_ARRAY,

            @Column(name = "exception_message")
            var message: String? = null,

            @Column(name = "timestamp")
            val persistedInstant: Instant? = null
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_metadata")
    class DBFlowMetadata(

            @Id
            @Column(name = "invocation_id", nullable = false)
            var invocationId: String,

            @Column(name = "flow_id", nullable = true)
            var flowId: String?,

            @Column(name = "flow_name", nullable = false)
            var flowName: String,

            @Column(name = "flow_identifier", nullable = true)
            var userSuppliedIdentifier: String?,

            @Column(name = "started_type", nullable = false)
            var startType: StartReason,

            @Column(name = "flow_parameters", nullable = false)
            var initialParameters: ByteArray = EMPTY_BYTE_ARRAY,

            @Column(name = "cordapp_name", nullable = false)
            var launchingCordapp: String,

            @Column(name = "platform_version", nullable = false)
            var platformVersion: Int,

            @Column(name = "rpc_user", nullable = false)
            var rpcUsername: String,

            @Column(name = "invocation_time", nullable = false)
            var invocationInstant: Instant,

            @Column(name = "received_time", nullable = false)
            var receivedInstant: Instant,

            @Column(name = "start_time", nullable = true)
            var startInstant: Instant?,

            @Column(name = "finish_time", nullable = true)
            var finishInstant: Instant?

    )

    private fun createDBCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint,
                                   serializedCheckpoint: SerializedBytes<Checkpoint>): DBFlowCheckpoint {
        val flowState = when (checkpoint.flowState) {
            is FlowState.Unstarted ->  checkpoint.flowState.frozenFlowLogic
            is FlowState.Started -> checkpoint.flowState.frozenFiber
        }
        return DBFlowCheckpoint(
                id = id.uuid.toString(),
                blob = DBFlowCheckpointBlob(
                        checkpoint = serializedCheckpoint.bytes,
                        flowStack = flowState.bytes,
                        hmac = ByteArray(16)
                ),
                result = DBFlowResult(),
                exceptionDetails = DBFlowException(type = Exception::class.java),
                status = FlowStatus.RUNNABLE,
                compatible = false,
                progressStep = "",
                ioRequestType = FlowIORequest.ForceCheckpoint.javaClass,
                checkpointInstant = Instant.now(),
                flowMetadata = null)
    }

    override fun addCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializationContext : CheckpointSerializationContext) {
        currentDBSession().save(createDBCheckpoint(id, checkpoint, checkpoint.checkpointSerialize(context = serializationContext)))
    }

    override fun updateCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializationContext : CheckpointSerializationContext) {
        currentDBSession().update(createDBCheckpoint(id, checkpoint, checkpoint.checkpointSerialize(context = serializationContext)))
    }

    override fun removeCheckpoint(id: StateMachineRunId): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBFlowCheckpoint::class.java)
        val root = delete.from(DBFlowCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBFlowCheckpoint::id.name), id.uuid.toString()))
        return session.createQuery(delete).executeUpdate() > 0
    }

    override fun getCheckpoint(id: StateMachineRunId): SerializedBytes<Checkpoint>? {
        val bytes = currentDBSession().get(DBFlowCheckpoint::class.java, id.uuid.toString())?.blob?.checkpoint ?: return null
        return SerializedBytes(bytes)
    }

    override fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, SerializedBytes<Checkpoint>>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(DBFlowCheckpoint::class.java)
        val root = criteriaQuery.from(DBFlowCheckpoint::class.java)
        criteriaQuery.select(root)
        return session.createQuery(criteriaQuery).stream().filter{it.blob != null}.map {
            StateMachineRunId(UUID.fromString(it.id)) to SerializedBytes<Checkpoint>(it.blob!!.checkpoint)
        }
    }

    override fun getCheckpointCount(connection: Connection): Long {
        return try {
            connection.prepareStatement("select count(*) from node_checkpoints").use { ps ->
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        } catch (e: SQLException) {
            // Happens when the table was not created yet.
            0L
        }
    }
}
