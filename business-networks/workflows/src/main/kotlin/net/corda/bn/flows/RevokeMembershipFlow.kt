package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to revoke membership. Queries for the membership with [membershipId] linear ID and
 * marks it historic. Transaction is signed by all active members authorised to modify membership and stored on ledgers of all members
 * authorised to modify membership and on revoked member's ledger.
 *
 * @property membershipId ID of the membership to be revoked.
 */
@InitiatingFlow
@StartableByRPC
class RevokeMembershipFlow(private val membershipId: UniqueIdentifier) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw FlowException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        val auth = BNUtils.loadBNMemberAuth()
        authorise(networkId, databaseService) { auth.canRevokeMembership(it) }

        // fetch observers and signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId, auth)
        val observers = authorisedMemberships.map { it.state.data.identity } + membership.state.data.identity - ourIdentity
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity } - membership.state.data.identity

        // building transaction
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addCommand(MembershipContract.Commands.Revoke(), signers.map { it.owningKey })
        builder.verify(serviceHub)

        // send info to observers whether they need to sign the transaction
        val observerSessions = observers.map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(RevokeMembershipFlow::class)
class RevokeMembershipFlowResponder(val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.Revoke) {
                throw FlowException("Only Revoke command is allowed")
            }
        }
    }
}