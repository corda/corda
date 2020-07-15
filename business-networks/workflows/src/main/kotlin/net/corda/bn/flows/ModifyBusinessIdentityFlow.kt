package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.BNIdentity
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to modify membership's business identity. Queries for the membership with
 * [membershipId] linear ID and overwrites [MembershipState.identity.businessIdentity] field with [businessIdentity] value. Transaction
 * is signed by all active members authorised to modify membership and stored on ledger of all state's participants.
 *
 * @property membershipId ID of the membership to modify business identity.
 * @property businessIdentity Custom business identity to be given to membership.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
@StartableByRPC
class ModifyBusinessIdentityFlow(
        private val membershipId: UniqueIdentifier,
        private val businessIdentity: BNIdentity,
        private val notary: Party? = null
) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw MembershipNotFoundException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyBusinessIdentity() }

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val signers = authorisedMemberships.filter {
            it.state.data.isActive()
        }.map {
            it.state.data.identity.cordaIdentity
        }.filterNot {
            // remove modified member from signers only if it is not the flow initiator (since initiator must sign the transaction)
            it == membership.state.data.identity.cordaIdentity && it != ourIdentity
        }

        // building transaction
        val outputMembership = membership.state.data.run {
            copy(identity = identity.copy(businessIdentity = businessIdentity), modified = serviceHub.clock.instant())
        }
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.ModifyBusinessIdentity(requiredSigners, ourIdentity), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observerSessions = (outputMembership.participants - ourIdentity).map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(ModifyBusinessIdentityFlow::class)
class ModifyBusinessIdentityResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.ModifyBusinessIdentity) {
                throw FlowException("Only ModifyBusinessIdentity command is allowed")
            }
        }
    }
}