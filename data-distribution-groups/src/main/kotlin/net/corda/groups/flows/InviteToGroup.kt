package net.corda.groups.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.groups.contracts.Group
import net.corda.groups.utilities.getGroupByKey
import java.security.PublicKey

// TODO: Add member as read only or read/write.
class InviteToGroup {
    @InitiatingFlow
    class Initiator(val key: PublicKey, val invitee: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // TODO: Check this party has not already been invited.
            // Get the certificate for this key and send it to the invitee withe the details.
            val cert = serviceHub.identityService.certificateFromKey(key)
                    ?: throw IllegalArgumentException("There is no cert for public key $key.")
            val group = getGroupByKey(key, serviceHub)
                    ?: throw IllegalArgumentException("There is no group for public key $key.")
            val groupDetails = group.state.data.details
            val detailsWithCert = Group.DetailsWithCert(groupDetails, cert)

            // Start a new session.
            // Send the new key details to the invitee.
            val session = initiateFlow(invitee)
            session.send(detailsWithCert)

            // Check the new key state is what we expect.
            return subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val newGroup = stx.tx.outputsOfType<Group.State>().single()
                    check(newGroup.details == groupDetails) { "Group details are not as expected." }
                }
            })

            // TODO: Send all previous transactions added to the group.
            // Currently we cannot do this as there's no recording of prior transactions send to the group.
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // TODO: Check are not already a member of this group. Does this matter? Depends what we want the "network" to look like.
            // Receive the details + cert and register the cert with the identity service.
            val detailsWithCert = otherSession.receive<Group.DetailsWithCert>().unwrap { it }
            // TODO: What happens if the cert is bogus? What actually are we checking here? Likely need new API on identity service.
            serviceHub.identityService.verifyAndRegisterIdentity(detailsWithCert.cert)

            // Create a transaction to add this group to our store.
            // We require the inviter's signature.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val newGroup = Group.State(
                    details = detailsWithCert.details, // The certificate is not stored on ledger.
                    participants = listOf(ourIdentity, otherSession.counterparty)
            )

            // The membership state is a bi-lateral agreement between the inviter and the invitee.
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





