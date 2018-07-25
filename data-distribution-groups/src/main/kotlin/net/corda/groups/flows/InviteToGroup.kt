package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.groups.contracts.Group

class InviteToGroup {
    @InitiatingFlow
    class Initiator(val groupDetails: Group.Details, val invitee: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Get the certificate for this groupDetails and send it to the invitee withe the details.
            val cert = serviceHub.identityService.certificateFromKey(groupDetails.key)
                    ?: throw IllegalArgumentException("There is no groupDetails for public key ${groupDetails.key}.")
            val detailsWithCert = Group.DetailsWithCert(groupDetails, cert)

            // Start a new session.
            // Send the new groupDetails details to the invitee.
            val session = initiateFlow(invitee)
            session.send(detailsWithCert)

            // Check the new groupDetails state is what we expect.
            return subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val newGroup = stx.tx.outputsOfType<Group.State>().single()
                    check(newGroup.details == groupDetails) { "Group details are different." }
                }
            })
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Receive the details + cert and register the cert with the identity service.
            val detailsWithCert = otherSession.receive<Group.DetailsWithCert>().unwrap { it }
            serviceHub.identityService.verifyAndRegisterIdentity(detailsWithCert.cert)

            // Create a transaction to add this groupDetails to our store.
            // We require the inviter's signature.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val newGroup = Group.State(
                    details = detailsWithCert.details,
                    participants = listOf(ourIdentity, otherSession.counterparty)
            )

            val utx = TransactionBuilder(notary = notary).apply {
                addOutputState(newGroup, Group.contractId)
                addCommand(Group.Invite(), listOf(ourIdentity.owningKey, otherSession.counterparty.owningKey))
            }

            // Sign and collect signatures.
            val ptx = serviceHub.signInitialTransaction(utx)
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(otherSession)))
            return subFlow(FinalityFlow(stx))
        }
    }
}





