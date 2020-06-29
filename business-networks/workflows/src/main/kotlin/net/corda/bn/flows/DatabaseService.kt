package net.corda.bn.flows

import net.corda.bn.flows.extensions.BNMemberAuth
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Service which handles all Business Network related vault queries.
 *
 * Each method querying vault for Business Network information must be included here.
 */
@CordaService
class DatabaseService(private val serviceHub: ServiceHub) : SingletonSerializeAsToken() {

    /**
     * Checks whether Business Network with [networkID] ID exists.
     *
     * @param networkID ID of the Business Network.
     */
    fun businessNetworkExists(networkID: String): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(networkIdCriteria(networkID))
        return serviceHub.vaultService.queryBy<MembershipState>(criteria).states.isNotEmpty()
	}

	/**
     * Queries for membership with [party] identity inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     * @param party Identity of the member.
     *
     * @return Membership state of member matching the query. If that member doesn't exist, returns [null].
     */
    fun getMembership(networkId: String, party: Party): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(identityCriteria(party))
        val states = serviceHub.vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    /**
     * Queries for membership with [linearId] linear ID.
     *
     * @param linearId Linear ID of the [MembershipState].
     *
     * @return Membership state matching the query. If that membership doesn't exist, returns [null].
     */
    fun getMembership(linearId: UniqueIdentifier): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(linearId))
        val states = serviceHub.vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    /**
     * Queries for all the membership states inside Business Network with [networkId] with one of [statuses].
     *
     * @param networkId ID of the Business Network.
     * @param statuses [MembershipStatus] of the memberships to be fetched.
     *
     * @return List of state and ref pairs of memberships matching the query.
     */
    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<StateAndRef<MembershipState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(statusCriteria(statuses.toList()))
        return serviceHub.vaultService.queryBy<MembershipState>(criteria).states
    }

    /**
     * Queries for all members inside Business Network with [networkId] ID authorised to modify membership
     * (can activate, suspend or revoke membership).
     *
     * @param networkId ID of the Business Network.
     * @param auth Object containing authorisation methods.
     *
     * @return List of state and ref pairs of authorised members' membership states.
     */
    fun getMembersAuthorisedToModifyMembership(networkId: String, auth: BNMemberAuth): List<StateAndRef<MembershipState>> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        val membership = it.state.data
        auth.run { canActivateMembership(membership) || canSuspendMembership(membership) || canRevokeMembership(membership) }
    }

    private fun networkIdCriteria(networkID: String) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::networkId.equal(networkID) })
    private fun identityCriteria(cordaIdentity: Party) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::cordaIdentity.equal(cordaIdentity) })
    private fun statusCriteria(statuses: List<MembershipStatus>) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(statuses) })
    private fun linearIdCriteria(linearId: UniqueIdentifier) = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
}
