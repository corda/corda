/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testMessage

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRefFactory
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import java.time.Instant
import java.util.*
import kotlin.reflect.jvm.jvmName

@SchedulableFlow
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
        serviceHub.vaultService.softLockReserve(lock, NonEmptySet.of(state.ref))
        val notary = state.state.notary
        val newStateOutput = scheduledState.copy(processed = true)
        val builder = TransactionBuilder(notary)
                .addInputState(state)
                .addOutputState(newStateOutput, DummyContract.PROGRAM_ID)
                .addCommand(dummyCommand(ourIdentity.owningKey))
        val tx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(tx, setOf(scheduledState.destination)))
    }
}

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

data class SpentState(val identity: String,
                      val source: Party,
                      val destination: Party,
                      override val linearId: UniqueIdentifier = UniqueIdentifier(externalId = identity)) : LinearState {
    override val participants: List<Party> get() = listOf(source, destination)
}
