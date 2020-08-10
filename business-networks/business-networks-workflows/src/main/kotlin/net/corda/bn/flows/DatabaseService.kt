package net.corda.bn.flows

import net.corda.bn.schemas.GroupStateSchemaV1
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.GroupState
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

    private val ourIdentity = serviceHub.myInfo.legalIdentities.first()

    /**
     * Checks whether Business Network with [networkId] ID exists.
     *
     * @param networkId ID of the Business Network.
     */
    fun businessNetworkExists(networkId: String): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(membershipNetworkIdCriteria(networkId))
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
        if (!isBusinessNetworkMember(networkId, ourIdentity)) {
            return null
        }

        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(membershipNetworkIdCriteria(networkId))
                .and(identityCriteria(party))
        val states = serviceHub.vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }?.run {
            if (ourIdentity in state.data.participants) this else null
        }
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
        return states.maxBy { it.state.data.modified }?.run {
            if (isBusinessNetworkMember(state.data.networkId, ourIdentity) && ourIdentity in state.data.participants) this else null
        }
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
        if (!isBusinessNetworkMember(networkId, ourIdentity)) {
            return emptyList()
        }

        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(membershipNetworkIdCriteria(networkId))
                .and(statusCriteria(statuses.toList()))
        return serviceHub.vaultService.queryBy<MembershipState>(criteria).states.filter {
            ourIdentity in it.state.data.participants
        }
    }

    /**
     * Queries for all members inside Business Network with [networkId] ID authorised to modify membership
     * (can activate, suspend or revoke membership).
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of authorised members' membership states.
     */
    fun getMembersAuthorisedToModifyMembership(networkId: String): List<StateAndRef<MembershipState>> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        it.state.data.canModifyMembership()
    }

    /**
     * Checks whether Business Network Group with [groupId] ID exists.
     *
     * @param groupId ID of the Business Network Group.
     */
    fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(linearIdCriteria(groupId))
        val state = serviceHub.vaultService.queryBy<GroupState>(criteria).states.map { it.state.data }.maxBy { it.modified }
        return state != null && ourIdentity in state.participants
    }

    /**
     * Queries for Business Network Group with [groupId] ID.
     *
     * @param groupId ID of the Business Network Group.
     *
     * @return Business Network Group matching the query. If that group doesn't exist, return [null].
     */
    fun getBusinessNetworkGroup(groupId: UniqueIdentifier): StateAndRef<GroupState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(groupId))
        val states = serviceHub.vaultService.queryBy<GroupState>(criteria).states
        return states.maxBy { it.state.data.modified }?.run {
            if (ourIdentity in state.data.participants) this else null
        }
    }

    /**
     * Queries for all Business Network Groups inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of Business Network Groups.
     */
    fun getAllBusinessNetworkGroups(networkId: String): List<StateAndRef<GroupState>> {
        if (!isBusinessNetworkMember(networkId, ourIdentity)) {
            return emptyList()
        }

        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(groupNetworkIdCriteria(networkId))
        return serviceHub.vaultService.queryBy<GroupState>(criteria).states.filter {
            ourIdentity in it.state.data.participants
        }
    }

    private fun isBusinessNetworkMember(networkId: String, party: Party): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(membershipNetworkIdCriteria(networkId))
                .and(identityCriteria(party))
        return serviceHub.vaultService.queryBy<MembershipState>(criteria).states.isNotEmpty()
    }

    /** Instantiates custom vault query criteria for finding membership with given [networkId]. **/
    private fun membershipNetworkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding Business Network Group with given [networkId]. **/
    private fun groupNetworkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { GroupStateSchemaV1.PersistentGroupState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding membership with given [cordaIdentity]. **/
    private fun identityCriteria(cordaIdentity: Party) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::cordaIdentity.equal(cordaIdentity) })

    /** Instantiates custom vault query criteria for finding membership with any of given [statuses]. **/
    private fun statusCriteria(statuses: List<MembershipStatus>) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(statuses) })

    /** Instantiates custom vault query criteria for finding linear state with given [linearId]. **/
    private fun linearIdCriteria(linearId: UniqueIdentifier) = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
}
