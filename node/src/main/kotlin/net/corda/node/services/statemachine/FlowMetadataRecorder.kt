package net.corda.node.services.statemachine

import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.persistence.DBCheckpointStorage
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.time.Clock

class FlowMetadataRecorder(
    private val checkpointStorage: CheckpointStorage,
    private val clock: Clock,
    private val database: CordaPersistence? = null
) {

    fun record(
        flow: Class<out FlowLogic<*>>,
        invocationContext: InvocationContext,
        startedType: DBCheckpointStorage.StartReason,
        startedBy: String,
        parameters: List<Any?> = emptyList(),
        userSuppliedIdentifier: String? = null
    ) {
        database?.transaction {
            checkpointStorage.addMetadata(
                CheckpointStorage.FlowMetadata(
                    invocationId = invocationContext.trace.invocationId.value,
                    flowName = flow.canonicalName,
                    // Is this the right value to pass in?
                    userSuppliedIdentifier = userSuppliedIdentifier,
                    startedType = startedType,
                    // Do not include the flow name in the parameters list
                    parameters = parameters,
                    // Probably going to be filled in on flow start
                    // CordappProvider.getCordappForFlow
                    // Either DI the class or it can be filled in on flow start since the information is held
                    // within the first checkpoint (inside the [SubFlow] class
                    launchingCordapp = "where do I get this info?",
                    platformVersion = PLATFORM_VERSION,
                    // might be able to keep the code above depends on what the context contains in it
                    startedBy = startedBy,
                    invocationInstant = invocationContext.trace.invocationId.timestamp,
                    // Just take now's time or record it earlier and pass it to here?
                    receivedInstant = clock.instant()
                )
            )
        }
    }
}