package net.corda.testMessage

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import java.time.Instant
import java.util.*
import kotlin.reflect.jvm.jvmName

@SchedulableFlow
@InitiatingFlow
class ScheduledFlow(private val stateRef: StateRef) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val state = serviceHub.toStateAndRef<ScheduledState>(stateRef)
        val scheduledState = state.state.data
        // Only run flow over states originating on this node
        if (!serviceHub.myInfo.isLegalIdentity(scheduledState.source)) {
            return
        }
        require(!scheduledState.processed) { "State should not have been previously processed" }
        val lock = UUID.randomUUID()
        try {
            serviceHub.vaultService.softLockReserve(lock, NonEmptySet.of(state.ref))
        } catch (e: StatesNotAvailableException) {
            return
        }
        val notary = state.state.notary
        val newStateOutput = scheduledState.copy(processed = true)
        val builder = TransactionBuilder(notary)
                .addInputState(state)
                .addOutputState(newStateOutput, DummyContract.PROGRAM_ID)
                .addCommand(dummyCommand(ourIdentity.owningKey))
        val tx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(tx, initiateFlow(scheduledState.destination)))
    }
}

@InitiatedBy(ScheduledFlow::class)
class ScheduledResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSide))
    }
}

@BelongsToContract(DummyContract::class)
data class ScheduledState(val creationTime: Instant,
                          val source: Party,
                          val destination: Party,
                          val identity: String,
                          val processed: Boolean = false,
                          val scheduledFor: Instant = creationTime,
                          override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = identity)) : SchedulableState, LinearState {
    override val participants get() = listOf(source, destination)
    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return if (!processed) {
            val logicRef = flowLogicRefFactory.create(ScheduledFlow::class.jvmName, thisStateRef)
            ScheduledActivity(logicRef, scheduledFor)
        } else {
            null
        }
    }
}

@BelongsToContract(DummyContract::class)
data class SpentState(val identity: String,
                      val source: Party,
                      val destination: Party,
                      override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = identity)) : LinearState {
    override val participants: List<Party> get() = listOf(source, destination)
}
