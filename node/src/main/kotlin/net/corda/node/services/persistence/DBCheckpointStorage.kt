package net.corda.node.services.persistence

import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.Checkpoint.FlowStatus
import net.corda.node.services.statemachine.CheckpointState
import net.corda.node.services.statemachine.ErrorState
import net.corda.node.services.statemachine.FlowState
import net.corda.node.services.statemachine.SubFlowVersion
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
import org.hibernate.annotations.Type
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
@Suppress("TooManyFunctions")
class DBCheckpointStorage(private val checkpointPerformanceRecorder: CheckpointPerformanceRecorder) : CheckpointStorage {

    companion object {
        val log = contextLogger()

        private const val HMAC_SIZE_BYTES = 16

        private const val MAX_PROGRESS_STEP_LENGTH = 256

        private val NOT_RUNNABLE_CHECKPOINTS = listOf(FlowStatus.COMPLETED, FlowStatus.FAILED, FlowStatus.KILLED)

        /**
         * This needs to run before Hibernate is initialised.
         *
         * No need to set up [DBCheckpointStorage] fully for this function
         *
         * @param connection The SQL Connection.
         * @return the number of checkpoints stored in the database.
         */
        fun getCheckpointCount(connection: Connection): Long {
            // No need to set up [DBCheckpointStorage] fully for this function
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

    enum class StartReason {
        RPC, SERVICE, SCHEDULED, INITIATED
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoints")
    class DBFlowCheckpoint(
        @Id
        @Column(name = "flow_id", length = 64, nullable = false)
        var id: String,

        @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
        @JoinColumn(name = "checkpoint_blob_id", referencedColumnName = "id")
        var blob: DBFlowCheckpointBlob,

        @OneToOne(fetch = FetchType.LAZY, optional = true)
        @JoinColumn(name = "result_id", referencedColumnName = "id")
        var result: DBFlowResult?,

        @OneToOne(fetch = FetchType.LAZY, optional = true)
        @JoinColumn(name = "error_id", referencedColumnName = "id")
        var exceptionDetails: DBFlowException?,

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "flow_id", referencedColumnName = "flow_id")
        var flowMetadata: DBFlowMetadata,

        @Column(name = "status", nullable = false)
        var status: FlowStatus,

        @Column(name = "compatible", nullable = false)
        var compatible: Boolean,

        @Column(name = "progress_step")
        var progressStep: String?,

        @Column(name = "flow_io_request")
        var ioRequestType: String?,

        @Column(name = "timestamp", nullable = false)
        var checkpointInstant: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}checkpoint_blobs")
    class DBFlowCheckpointBlob(
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        @Column(name = "id", nullable = false)
        var id: Long = 0,

        @Type(type = "corda-blob")
        @Column(name = "checkpoint_value", nullable = false)
        var checkpoint: ByteArray = EMPTY_BYTE_ARRAY,

        // A future task will make this nullable
        @Type(type = "corda-blob")
        @Column(name = "flow_state", nullable = false)
        var flowStack: ByteArray = EMPTY_BYTE_ARRAY,

        @Column(name = "hmac")
        var hmac: ByteArray,

        @Column(name = "timestamp")
        var persistedInstant: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_results")
    class DBFlowResult(
        @Id
        @Column(name = "id", nullable = false)
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        var id: Long = 0,

        @Type(type = "corda-blob")
        @Column(name = "result_value", nullable = false)
        var value: ByteArray = EMPTY_BYTE_ARRAY,

        @Column(name = "timestamp")
        val persistedInstant: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_exceptions")
    class DBFlowException(
        @Id
        @Column(name = "id", nullable = false)
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        var id: Long = 0,

        @Column(name = "type", nullable = false)
        var type: Class<out Throwable>,

        @Type(type = "corda-blob")
        @Column(name = "exception_value", nullable = false)
        var value: ByteArray = EMPTY_BYTE_ARRAY,

        @Column(name = "exception_message")
        var message: String? = null,

        @Column(name = "timestamp")
        val persistedInstant: Instant
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}flow_metadata")
    class DBFlowMetadata(

        @Id
        @Column(name = "flow_id", nullable = false)
        var flowId: String,

        @Column(name = "invocation_id", nullable = false)
        var invocationId: String,

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

        @Column(name = "start_time", nullable = true)
        var startInstant: Instant,

        @Column(name = "finish_time", nullable = true)
        var finishInstant: Instant?
    ) {
        @Suppress("ComplexMethod")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DBFlowMetadata

            if (flowId != other.flowId) return false
            if (invocationId != other.invocationId) return false
            if (flowName != other.flowName) return false
            if (userSuppliedIdentifier != other.userSuppliedIdentifier) return false
            if (startType != other.startType) return false
            if (!initialParameters.contentEquals(other.initialParameters)) return false
            if (launchingCordapp != other.launchingCordapp) return false
            if (platformVersion != other.platformVersion) return false
            if (rpcUsername != other.rpcUsername) return false
            if (invocationInstant != other.invocationInstant) return false
            if (startInstant != other.startInstant) return false
            if (finishInstant != other.finishInstant) return false

            return true
        }

        override fun hashCode(): Int {
            var result = flowId.hashCode()
            result = 31 * result + invocationId.hashCode()
            result = 31 * result + flowName.hashCode()
            result = 31 * result + (userSuppliedIdentifier?.hashCode() ?: 0)
            result = 31 * result + startType.hashCode()
            result = 31 * result + initialParameters.contentHashCode()
            result = 31 * result + launchingCordapp.hashCode()
            result = 31 * result + platformVersion
            result = 31 * result + rpcUsername.hashCode()
            result = 31 * result + invocationInstant.hashCode()
            result = 31 * result + startInstant.hashCode()
            result = 31 * result + (finishInstant?.hashCode() ?: 0)
            return result
        }
    }

    override fun addCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>) {
        currentDBSession().save(createDBCheckpoint(id, checkpoint, serializedFlowState))
    }

    override fun updateCheckpoint(id: StateMachineRunId, checkpoint: Checkpoint, serializedFlowState: SerializedBytes<FlowState>) {
        currentDBSession().update(updateDBCheckpoint(id, checkpoint, serializedFlowState))
    }

    override fun removeCheckpoint(id: StateMachineRunId): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val delete = criteriaBuilder.createCriteriaDelete(DBFlowCheckpoint::class.java)
        val root = delete.from(DBFlowCheckpoint::class.java)
        delete.where(criteriaBuilder.equal(root.get<String>(DBFlowCheckpoint::id.name), id.uuid.toString()))
        return session.createQuery(delete).executeUpdate() > 0
    }

    override fun getCheckpoint(id: StateMachineRunId): Checkpoint.Serialized? {
        return getDBCheckpoint(id)?.toSerializedCheckpoint()
    }

    override fun getAllCheckpoints(): Stream<Pair<StateMachineRunId, Checkpoint.Serialized>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(DBFlowCheckpoint::class.java)
        val root = criteriaQuery.from(DBFlowCheckpoint::class.java)
        criteriaQuery.select(root)
        return session.createQuery(criteriaQuery).stream().map {
            StateMachineRunId(UUID.fromString(it.id)) to it.toSerializedCheckpoint()
        }
    }

    override fun getRunnableCheckpoints(): Stream<Pair<StateMachineRunId, Checkpoint.Serialized>> {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(DBFlowCheckpoint::class.java)
        val root = criteriaQuery.from(DBFlowCheckpoint::class.java)
        criteriaQuery.select(root)
                .where(criteriaBuilder.not(root.get<FlowStatus>(DBFlowCheckpoint::status.name).`in`(NOT_RUNNABLE_CHECKPOINTS)))
        return session.createQuery(criteriaQuery).stream().map {
            StateMachineRunId(UUID.fromString(it.id)) to it.toSerializedCheckpoint()
        }
    }

    private fun getDBCheckpoint(id: StateMachineRunId): DBFlowCheckpoint? {
        return currentDBSession().find(DBFlowCheckpoint::class.java, id.uuid.toString())
    }

    private fun createDBCheckpoint(
        id: StateMachineRunId,
        checkpoint: Checkpoint,
        serializedFlowState: SerializedBytes<FlowState>
    ): DBFlowCheckpoint {
        val flowId = id.uuid.toString()
        val now = Instant.now()

        val serializedCheckpointState = checkpoint.checkpointState.storageSerialize()
        checkpointPerformanceRecorder.record(serializedCheckpointState, serializedFlowState)

        val blob = createDBCheckpointBlob(serializedCheckpointState, serializedFlowState, now)

        val metadata = createMetadata(flowId, checkpoint)
        // Most fields are null as they cannot have been set when creating the initial checkpoint
        return DBFlowCheckpoint(
            id = flowId,
            blob = blob,
            result = null,
            exceptionDetails = null,
            flowMetadata = metadata,
            status = checkpoint.status,
            compatible = checkpoint.compatible,
            progressStep = null,
            ioRequestType = null,
            checkpointInstant = Instant.now()
        )
    }

    // Remove this when saving of metadata is properly handled
    private fun createMetadata(flowId: String, checkpoint: Checkpoint): DBFlowMetadata {
        val context = checkpoint.checkpointState.invocationContext
        val flowInfo = checkpoint.checkpointState.subFlowStack.first()
        return DBFlowMetadata(
            flowId = flowId,
            invocationId = context.trace.invocationId.value,
            flowName = flowInfo.flowClass.name,
            // will come from the context
            userSuppliedIdentifier = null,
            startType = context.getStartedType(),
            initialParameters = context.getFlowParameters().storageSerialize().bytes,
            launchingCordapp = (flowInfo.subFlowVersion as? SubFlowVersion.CorDappFlow)?.corDappName ?: "Core flow",
            platformVersion = PLATFORM_VERSION,
            rpcUsername = context.principal().name,
            invocationInstant = context.trace.invocationId.timestamp,
            startInstant = Instant.now(),
            finishInstant = null
        ).apply {
            currentDBSession().save(this)
        }
    }

    private fun updateDBCheckpoint(
        id: StateMachineRunId,
        checkpoint: Checkpoint,
        serializedFlowState: SerializedBytes<FlowState>
    ): DBFlowCheckpoint {
        val flowId = id.uuid.toString()
        val now = Instant.now()

        // Load the previous entity from the hibernate cache so the meta data join does not get updated
        val entity = currentDBSession().find(DBFlowCheckpoint::class.java, flowId)

        val serializedCheckpointState = checkpoint.checkpointState.storageSerialize()
        checkpointPerformanceRecorder.record(serializedCheckpointState, serializedFlowState)

        val blob = createDBCheckpointBlob(serializedCheckpointState, serializedFlowState, now)
        //This code needs to be added back in when we want to persist the result. For now this requires the result to be @CordaSerializable.
        //val result = updateDBFlowResult(entity, checkpoint, now)
        val exceptionDetails = updateDBFlowException(entity, checkpoint, now)

        return entity.apply {
            this.blob = blob
            //Set the result to null for now.
            this.result = null
            this.exceptionDetails = exceptionDetails
            // Do not update the meta data relationship on updates
            this.flowMetadata = entity.flowMetadata
            this.status = checkpoint.status
            this.compatible = checkpoint.compatible
            this.progressStep = checkpoint.progressStep?.take(MAX_PROGRESS_STEP_LENGTH)
            this.ioRequestType = checkpoint.flowIoRequest
            this.checkpointInstant = now
        }
    }

    private fun createDBCheckpointBlob(
        serializedCheckpointState: SerializedBytes<CheckpointState>,
        serializedFlowState: SerializedBytes<FlowState>,
        now: Instant
    ): DBFlowCheckpointBlob {
        return DBFlowCheckpointBlob(
            checkpoint = serializedCheckpointState.bytes,
            flowStack = serializedFlowState.bytes,
            hmac = ByteArray(HMAC_SIZE_BYTES),
            persistedInstant = now
        )
    }

    /**
     * Creates, updates or deletes the result related to the current flow/checkpoint.
     *
     * This is needed because updates are not cascading via Hibernate, therefore operations must be handled manually.
     *
     * A [DBFlowResult] is created if [DBFlowCheckpoint.result] does not exist and the [Checkpoint] has a result..
     * The existing [DBFlowResult] is updated if [DBFlowCheckpoint.result] exists and the [Checkpoint] has a result.
     * The existing [DBFlowResult] is deleted if [DBFlowCheckpoint.result] exists and the [Checkpoint] has no result.
     * Nothing happens if both [DBFlowCheckpoint] and [Checkpoint] do not have a result.
     */
    private fun updateDBFlowResult(entity: DBFlowCheckpoint, checkpoint: Checkpoint, now: Instant): DBFlowResult? {
        val result = checkpoint.result?.let { createDBFlowResult(it, now) }
        if (entity.result != null) {
            if (result != null) {
                result.id = entity.result!!.id
                currentDBSession().update(result)
            } else {
                currentDBSession().delete(entity.result)
            }
        } else if (result != null) {
            currentDBSession().save(result)
        }
        return result
    }

    private fun createDBFlowResult(result: Any, now: Instant): DBFlowResult {
        return DBFlowResult(
            value = result.storageSerialize().bytes,
            persistedInstant = now
        )
    }

    /**
     * Creates, updates or deletes the error related to the current flow/checkpoint.
     *
     * This is needed because updates are not cascading via Hibernate, therefore operations must be handled manually.
     *
     * A [DBFlowException] is created if [DBFlowCheckpoint.exceptionDetails] does not exist and the [Checkpoint] has an error attached to it.
     * The existing [DBFlowException] is updated if [DBFlowCheckpoint.exceptionDetails] exists and the [Checkpoint] has an error.
     * The existing [DBFlowException] is deleted if [DBFlowCheckpoint.exceptionDetails] exists and the [Checkpoint] has no error.
     * Nothing happens if both [DBFlowCheckpoint] and [Checkpoint] are related to no errors.
     */
    private fun updateDBFlowException(entity: DBFlowCheckpoint, checkpoint: Checkpoint, now: Instant): DBFlowException? {
        val exceptionDetails = (checkpoint.errorState as? ErrorState.Errored)?.let { createDBFlowException(it, now) }
        if (entity.exceptionDetails != null) {
            if (exceptionDetails != null) {
                exceptionDetails.id = entity.exceptionDetails!!.id
                currentDBSession().update(exceptionDetails)
            } else {
                currentDBSession().delete(entity.exceptionDetails)
            }
        } else if (exceptionDetails != null) {
            currentDBSession().save(exceptionDetails)
        }
        return exceptionDetails
    }

    private fun createDBFlowException(errorState: ErrorState.Errored, now: Instant): DBFlowException {
        return errorState.errors.last().exception.let {
            DBFlowException(
                type = it::class.java,
                message = it.message,
                value = it.storageSerialize().bytes,
                persistedInstant = now
            )
        }
    }

    private fun InvocationContext.getStartedType(): StartReason {
        return when (origin) {
            is InvocationOrigin.RPC, is InvocationOrigin.Shell -> StartReason.RPC
            is InvocationOrigin.Peer -> StartReason.INITIATED
            is InvocationOrigin.Service -> StartReason.SERVICE
            is InvocationOrigin.Scheduled -> StartReason.SCHEDULED
        }
    }

    private fun InvocationContext.getFlowParameters(): List<Any?> {
        // Only RPC flows have parameters which are found in index 1
        return if(arguments.isNotEmpty()) {
            uncheckedCast<Any?, Array<Any?>>(arguments[1]).toList()
        } else {
            emptyList()
        }
    }

    private fun DBFlowCheckpoint.toSerializedCheckpoint(): Checkpoint.Serialized {
        return Checkpoint.Serialized(
            serializedCheckpointState = SerializedBytes(blob.checkpoint),
            serializedFlowState = SerializedBytes(blob.flowStack),
            // Always load as a [Clean] checkpoint to represent that the checkpoint is the last _good_ checkpoint
            errorState = ErrorState.Clean,
            // A checkpoint with a result should not normally be loaded (it should be [null] most of the time)
            result = result?.let { SerializedBytes<Any>(it.value) },
            status = status,
            progressStep = progressStep,
            flowIoRequest = ioRequestType,
            compatible = compatible
        )
    }

    private fun <T : Any> T.storageSerialize(): SerializedBytes<T> {
        return serialize(context = SerializationDefaults.STORAGE_CONTEXT)
    }
}
