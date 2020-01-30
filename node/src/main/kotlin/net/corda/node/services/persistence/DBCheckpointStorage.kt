package net.corda.node.services.persistence

import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowIORequest
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.debug
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Stream
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import org.hibernate.annotations.Type
import java.lang.Exception
import java.math.BigInteger
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.OneToOne

/**
 * Simple checkpoint key value storage in DB.
 */
class DBCheckpointStorage : CheckpointStorage {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    enum class FlowStatus {
        RUNNABLE,
        FAILED,
        COMPLETED,
        HOSPITALIZED,
        KILLED,
        PAUSED
    }

    enum class StartReason {
        RPC, FLOW, SERVICE, SCHEDULED, INITIATED
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints_new")
    class DBFlowCheckpoint(
            @Id
            @Column(name = "flow_id", length = 64, nullable = false)
            var id: String? = null,

            @OneToOne(fetch = FetchType.LAZY)
            @JoinColumn(name = "checkpoint_blob_id", referencedColumnName = "id")
            var blob: DBFlowCheckpointBlob? = null,

            @OneToOne(fetch = FetchType.LAZY)
            @JoinColumn(name = "result_id", referencedColumnName = "id")
            var result: DBFlowResult? = null,

            @OneToOne(fetch = FetchType.LAZY)
            @JoinColumn(name = "error_id", referencedColumnName = "id")
            var exceptionDetails: DBFlowException? = null,

            @OneToOne(fetch = FetchType.LAZY)
            @JoinColumn(name = "flow_id", referencedColumnName = "flow_id")
            var flowMetadata: DBFlowMetadata? = null,

            @Column(name = "status")
            var status: FlowStatus? = null,

            @Column(name = "compatible")
            var compatible: Boolean? = null,

            @Column(name = "progress_step")
            var progressStep: String? = null,

            @Column(name = "flow_io_request")
            var ioRequestType: Class<FlowIORequest<*>>? = null,

            @Column(name = "timestamp")
            var checkpointInstant: Instant? = null
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints_blobs")
    class DBFlowCheckpointBlob(
            @Id
            @Column(name = "id", nullable = false)
            var id: BigInteger? = null,

            @Type(type = "corda-blob")
            @Column(name = "checkpoint_value", nullable = false)
            var checkpoint: ByteArray = EMPTY_BYTE_ARRAY,

            @Type(type = "corda-blob")
            @Column(name = "flow_state", nullable = false)
            var flowStack: ByteArray = EMPTY_BYTE_ARRAY,

            @Column(name = "timestamp")
            var persistedInstant: Instant? = null
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_results")
    class DBFlowResult(
            @Id
            @Column(name = "id", nullable = false)
            var id: BigInteger? = null,

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
            var id: BigInteger? = null,

            @Column(name = "type", nullable = false)
            var type: Class<Exception>? = null,

            @Type(type = "corda-blob")
            @Column(name = "exception_value", nullable = false)
            var value: ByteArray,

            @Column(name = "exception_message")
            var message: String? = null,

            @Column(name = "timestamp")
            val persistedInstant: Instant? = null
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_metadata")
    class DBFlowMetadata(

            @Id
            @Column(name = "flow_id", length = 64, nullable = false)
            var flowId: String? = null,

            @Column(name = "flow_name", nullable = false)
            var flowName: String? = null,

            @Column(name = "flow_identifier", nullable = true)
            var userSuppliedIdentifier: String? = null,

            @Column(name = "started_type", nullable = true)
            var startType: StartReason? = null,

            @Column(name = "flow_parameters", nullable = true)
            var initialParameters: ByteArray? = null,

            @Column(name = "cordapp_name", nullable = true)
            var launchingCordapp: String? = null,

            @Column(name = "platform_version", nullable = true)
            var platformVersion: Int? = null,

            @Column(name = "rpc_user", nullable = true)
            var rpcUsername: String? = null,

            @Column(name = "invocation_time", nullable = true)
            var invocationInstant: Instant? = null,

            @Column(name = "received_time", nullable = true)
            var receivedInstant: Instant? = null,

            @Column(name = "start_time", nullable = true)
            var startInstant: Instant? = null,

            @Column(name = "finish_time", nullable = true)
            var finishInstant: Instant? = null

    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
    class DBCheckpoint(
            @Id
            @Suppress("MagicNumber") // database column width
            @Column(name = "checkpoint_id", length = 64, nullable = false)
            var checkpointId: String = "",

            @Type(type = "corda-blob")
            @Column(name = "checkpoint_value", nullable = false)
            var checkpoint: ByteArray = EMPTY_BYTE_ARRAY
    ) {
        override fun toString() = "DBCheckpoint(checkpointId = ${checkpointId}, checkpointSize = ${checkpoint.size})"
    }

    override fun addCheckpoint(id: StateMachineRunId, checkpoint: SerializedBytes<Checkpoint>) {
        currentDBSession().save(DBCheckpoint().apply {
            checkpointId = id.uuid.toString()
            this.checkpoint = checkpoint.bytes
            log.debug { "Checkpoint $checkpointId, size=${this.checkpoint.size}" }
        })
    }

    override fun updateCheckpoint(id: StateMachineRunId, checkpoint: SerializedBytes<Checkpoint>) {
        currentDBSession().update(DBCheckpoint().apply {
            checkpointId = id.uuid.toString()
            this.checkpoint = checkpoint.bytes
            log.debug { "Checkpoint $checkpointId, size=${this.checkpoint.size}" }
        })
    }

    override fun removeCheckpoint(id: StateMachineRunId): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBCheckpoint::class.java)
        val root = delete.from(DBCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBCheckpoint::checkpointId.name), id.uuid.toString()))
        return session.createQuery(delete).executeUpdate() > 0
    }

    override fun getCheckpoint(id: StateMachineRunId): SerializedBytes<Checkpoint>? {
        val bytes = currentDBSession().get(DBCheckpoint::class.java, id.uuid.toString())?.checkpoint ?: return null
        return SerializedBytes(bytes)
    }

    override fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, SerializedBytes<Checkpoint>>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(DBCheckpoint::class.java)
        val root = criteriaQuery.from(DBCheckpoint::class.java)
        criteriaQuery.select(root)
        return session.createQuery(criteriaQuery).stream().map {
            StateMachineRunId(UUID.fromString(it.checkpointId)) to SerializedBytes<Checkpoint>(it.checkpoint)
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
