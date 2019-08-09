package net.corda.notaryhealthcheck.cordapp

import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.notaryhealthcheck.utils.Monitorable
import java.security.PublicKey
import java.time.Instant
import kotlin.reflect.jvm.jvmName

class SchedulingContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }

    companion object {
        val PROGRAM_ID = SchedulingContract::class.java.name

        object EmptyCommandData : TypeOnlyCommandData()

        fun emptyCommand(vararg signers: PublicKey) = Command<TypeOnlyCommandData>(EmptyCommandData, signers.toList())
    }

    /**
     * Scheduled state to kick off a notary healthcheck at the specified start time
     *
     * @param participants List of participants, inherited from LinearState. Should only be the node the check CordApp is running on
     * @param linearId The linear id of this check (used to track progress from scheduled to running/abandoned/success/failure
     * @param statesToCheck A list of linear ids of scheduled/running checks that were outstanding when this state was created
     * @param target The notary or notary cluster member to check
     * @param startTime The time after which to run the check
     * @param lastSuccessTime The last time a successful check for this state's target was run
     * @param waitTimeSeconds How long to wait for the next check (i.e. time to add to the current time for the next states startTime)
     * @param waitForOutstandingFlowsSeconds How long to wait before running another notary check if the previous one is still outstanding.
     */
    class ScheduledCheckState(
            override val participants: List<AbstractParty>,
            override val linearId: UniqueIdentifier,
            val statesToCheck: List<UniqueIdentifier>,
            val target: Monitorable,
            val startTime: Instant,
            val lastSuccessTime: Instant,
            val waitTimeSeconds: Int,
            val waitForOutstandingFlowsSeconds: Int) : SchedulableState, LinearState {
        override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
            val logicRef = flowLogicRefFactory.create(ScheduledCheckFlow::class.jvmName, thisStateRef, waitTimeSeconds, waitForOutstandingFlowsSeconds)
            return ScheduledActivity(logicRef, startTime)
        }
    }

    /**
     * State to mark a running notary health check. The ScheduleCheckFlow evolves a ScheduleCheckState into this when it
     * runs the healthcheck flow for its target.
     */
    data class RunningCheckState(override val linearId: UniqueIdentifier, override val participants: List<AbstractParty>, val startTime: Instant) : LinearState

    /**
     * State to mark a successful health check. The ScheduleCheckFlow evolves a RunningCheckState into this if the healthcheck
     * flow returns successfully.
     */
    data class SuccessfulCheckState(override val linearId: UniqueIdentifier, override val participants: List<AbstractParty>, val finishTime: Instant) : LinearState

    /**
     * State to mark a failed health check. The ScheduleCheckFlow evolves a RunningCheckState into this if the healthcheck
     * flow fails
     */
    data class FailedCheckState(override val linearId: UniqueIdentifier, override val participants: List<AbstractParty>) : LinearState

    /**
     * State to mark an abandoned (not started) healthcheck. The ScheduleCheckFlow evolves a ScheduleCheckState into this
     * if there was still one or more healthcheck flows outstanding and the last one had been started less than waitForOutstandingFlowsSeconds
     * ago.
     */
    data class AbandonedCheckState(override val linearId: UniqueIdentifier, override val participants: List<AbstractParty>) : LinearState
}