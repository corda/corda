package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.groups.contracts.Group

// TODO: THe group will be represented by a Cert only in future versions.
class CreateGroup(val name: String) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Create a new key pair and certificate for the key.
        // Currently we are sending the certificate and key when inviting a new member to join the group.
        val newGroupIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)

        // Store the key information in a Group State.
        // The founding group state only has the founder as a participant. Easier this way as we can re-use the
        // same state definition for inviting new members as well as founding enw groups.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val groupDetails = Group.Details(newGroupIdentity.owningKey, name)
        val newGroup = Group.State(groupDetails, listOf(ourIdentity))

        val utx = TransactionBuilder(notary = notary).apply {
            addOutputState(newGroup, Group.contractId)
            addCommand(Group.Create(), listOf(ourIdentity.owningKey))
        }

        val stx = serviceHub.signInitialTransaction(utx)
        return subFlow(FinalityFlow(stx))
    }
}