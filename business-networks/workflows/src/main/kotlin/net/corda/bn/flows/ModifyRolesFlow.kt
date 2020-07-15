package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.BNORole
import net.corda.bn.states.BNRole
import net.corda.bn.states.MemberRole
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to modify membership roles. Queries for the membership with [membershipId] linear ID and
 * overwrites [MembershipState.roles] field with [roles] value. Transaction is signed by all active members authorised to modify membership
 * and stored on ledgers of all state's participants.
 *
 * If the members becomes authorised to modify memberships, it should be added to all Business Network Groups in order to observe all
 * Business Network's memberships.
 *
 * @property membershipId ID of the membership to assign roles.
 * @property roles Set of roles to be assigned to membership.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
@StartableByRPC
class ModifyRolesFlow(private val membershipId: UniqueIdentifier, private val roles: Set<BNRole>, private val notary: Party? = null) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw MembershipNotFoundException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyRoles() }

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity } - membership.state.data.identity.cordaIdentity

        // building transaction
        val outputMembership = membership.state.data.copy(roles = roles, modified = serviceHub.clock.instant())
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.ModifyRoles(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transactions
        val observerSessions = (outputMembership.participants - ourIdentity).map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}

/**
 * This flow assigns [BNORole] to membership with [membershipId] linear ID. It is meant to be conveniently used from node shell.
 *
 * @property membershipId ID of the membership to assign role.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@StartableByRPC
class AssignBNORoleFlow(private val membershipId: UniqueIdentifier, private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ModifyRolesFlow(membershipId, setOf(BNORole()), notary))
    }
}

/**
 * This flow assigns [MemberRole] to membership with [membershipId] linear ID. It is meant to be conveniently used from node shell.
 *
 * @property membershipId ID of the membership to assign role.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@StartableByRPC
class AssignMemberRoleFlow(private val membershipId: UniqueIdentifier, private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ModifyRolesFlow(membershipId, setOf(MemberRole()), notary))
    }
}

@InitiatedBy(ModifyRolesFlow::class)
class ModifyRolesResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.ModifyRoles) {
                throw FlowException("Only ModifyPermissions command is allowed")
            }
        }
    }
}
