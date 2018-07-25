package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.TransactionBuilder
import net.corda.groups.contracts.Group

class CreateGroup(val name: String) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Create a new key pair and certificate for the groupDetails.
        val newGroupIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)

        // Store the groupDetails information in a Group State.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val groupDetails = Group.Details(name, newGroupIdentity.owningKey)
        val newGroup = Group.State(groupDetails, listOf(ourIdentity))

        val utx = TransactionBuilder(notary = notary).apply {
            addOutputState(newGroup, Group.contractId)
            addCommand(Group.Create(), listOf(ourIdentity.owningKey))
        }

        val stx = serviceHub.signInitialTransaction(utx)
        subFlow(FinalityFlow(stx))
    }
}