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
 * This flow is initiated by any member authorised to modify membership's additional identity. Queries for the membership with
 * [membershipId] linear ID and overwrites [MembershipState.identity.additionalIdentity] field with [additionalIdentity] value. Transaction
 * is signed by all active members authorised to modify membership and stored on ledger of all members authorised to modify membership and
 * on modified member's ledger.
 *
 * @property membershipId ID of the membership to modify additional identity.
 * @property additionalIdentity Custom additional identity to be given to membership.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
@StartableByRPC
class ModifyAdditionalIdentityFlow(private val membershipId: UniqueIdentifier, private val additionalIdentity: BNIdentity, private val notary: Party? = null) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw MembershipNotFoundException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyAdditionalIdentity() }

        // fetch observers and signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val observers = authorisedMemberships.map { it.state.data.identity.cordaIdentity }.toSet() + membership.state.data.identity.cordaIdentity - ourIdentity
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity } - membership.state.data.identity.cordaIdentity

        // building transaction
        val outputMembership = membership.state.data.run {
            copy(
                    identity = identity.copy(additionalIdentity = additionalIdentity),
                    modified = serviceHub.clock.instant(),
                    participants = (observers + ourIdentity).toList()
            )
        }
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.ModifyAdditionalIdentity(requiredSigners, ourIdentity), requiredSigners)
        builder.verify(serviceHub)

        val observerSessions = observers.map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

@InitiatedBy(ModifyAdditionalIdentityFlow::class)
class ModifyAdditionalIdentityResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.ModifyAdditionalIdentity) {
                throw FlowException("Only ModifyAdditionalIdentity command is allowed")
            }
        }
    }
}