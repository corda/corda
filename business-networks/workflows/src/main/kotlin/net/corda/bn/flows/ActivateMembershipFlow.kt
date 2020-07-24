package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to activate membership. Queries for the membership with [membershipId] linear ID and
 * moves it to [MembershipStatus.ACTIVE] status. Transaction is signed by all active members authorised to modify membership and stored on
 * ledgers of all state's participants.
 *
 * If this is new onboarded member (its membership was in pending status before activation), it should be added in one of Business Network
 * Groups in order to be discoverable by any member authorised to modify memberships.
 *
 * @property membershipId ID of the membership to be activated.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
class ActivateMembershipFlow(private val membershipId: UniqueIdentifier, private val notary: Party? = null) : AbstractMembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val storage = (serviceHub.businessNetworksService as? VaultBusinessNetworksService)?.membershipStorage
                ?: throw FlowException("Business Network Service not initialised")
        val membership = storage.getMembership(membershipId)
                ?: throw MembershipNotFoundException("Membership with $membershipId membership ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, storage) { it.toBusinessNetworkMembership().canActivateMembership() }

        // fetch signers
        val authorisedMemberships = storage.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val signers = authorisedMemberships.filter {
            it.state.data.toBusinessNetworkMembership().isActive()
        }.map {
            it.state.data.identity.cordaIdentity
        }

        // building transaction
        val outputMembership = membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant())
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.Activate(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observerSessions = (outputMembership.participants - ourIdentity).map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(ActivateMembershipFlow::class)
class ActivateMembershipResponderFlow(private val session: FlowSession) : AbstractMembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.Activate) {
                throw FlowException("Only Activate command is allowed")
            }
        }
    }
}
