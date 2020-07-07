package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
class ModifyParticipantsFlow(
        private val membership: StateAndRef<MembershipState>,
        private val participants: List<AbstractParty>,
        private val signers: List<Party>,
        private val notary: Party?
) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(participants = participants, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.ModifyParticipants(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        val observers = membership.state.data.participants.toSet() + participants - ourIdentity
        val observerSessions = observers.map { initiateFlow(it) }
        collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(ModifyParticipantsFlow::class)
class ModifyParticipantsResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.ModifyParticipants) {
                throw FlowException("Only Modify command is allowed")
            }
        }
    }
}