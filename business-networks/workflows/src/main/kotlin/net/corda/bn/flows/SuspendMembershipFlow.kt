package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipStatus
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
 * This flow is initiated by any member authorised to suspend membership. Queries for the membership with [membershipId] linear ID and
 * moves it to [MembershipStatus.SUSPENDED] status. Transaction is signed by all active members authorised to modify membership and stored
 * on ledgers of all state's participants.
 *
 * If this is new onboarded member (its membership was in pending status before suspension), it should be added in one of Business Network
 * Groups in order to be discoverable by any member authorised to modify memberships.
 *
 * @property membershipId ID of the membership to be suspended.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
@StartableByRPC
class SuspendMembershipFlow(private val membershipId: UniqueIdentifier, private val notary: Party? = null) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw MembershipNotFoundException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, databaseService) { it.canSuspendMembership() }

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity } - membership.state.data.identity.cordaIdentity

        // building transaction
        val outputMembership = membership.state.data.copy(status = MembershipStatus.SUSPENDED, modified = serviceHub.clock.instant())
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.Suspend(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observerSessions = (outputMembership.participants - ourIdentity).map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(SuspendMembershipFlow::class)
class SuspendMembershipFlowResponder(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.Suspend) {
                throw FlowException("Only Suspend command is allowed")
            }
        }
    }
}
