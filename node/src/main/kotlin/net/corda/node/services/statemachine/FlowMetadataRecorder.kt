package net.corda.node.services.statemachine

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.time.Clock

class FlowMetadataRecorder(
    private val checkpointStorage: CheckpointStorage,
    private val cordappProvider: CordappProviderInternal,
    private val database: CordaPersistence,
    private val clock: Clock
) {

    private companion object {
        const val UNDEFINED_CORDAPP = "Undefined"
    }

    fun record(
        flow: Class<out FlowLogic<*>>,
        invocationContext: InvocationContext,
        startedType: DBCheckpointStorage.StartReason,
        startedBy: String,
        parameters: List<Any?> = emptyList(),
        userSuppliedIdentifier: String? = null
    ) {
        database.transaction {
            checkpointStorage.addMetadata(
                CheckpointStorage.FlowMetadata(
                    invocationId = invocationContext.trace.invocationId.value,
                    flowName = flow.canonicalName ?: flow.name,
                    userSuppliedIdentifier = userSuppliedIdentifier,
                    startedType = startedType,
                    parameters = parameters,
                    launchingCordapp = cordappProvider.getCordappForFlow(flow)?.name ?: UNDEFINED_CORDAPP,
                    platformVersion = PLATFORM_VERSION,
                    startedBy = startedBy,
                    invocationInstant = invocationContext.trace.invocationId.timestamp,
                    // Just take now's time or record it earlier and pass it to here?
                    receivedInstant = clock.instant()
                )
            )
        }
    }
}