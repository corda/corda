package net.corda.bn.flows

import net.corda.bn.schemas.GroupStateSchemaV1
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.bn.MembershipManagementFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BNRole
import net.corda.core.node.services.bn.BusinessNetworkGroup
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Handles all [MembershipState] related vault queries.
 *
 * @property vaultService Corda's [VaultService] used to query node's vault for Business Network Membership information.
 */
class MembershipStorage(private val vaultService: VaultService) {

    /**
     * Checks whether Business Network with [networkId] ID exists.
     *
     * @param networkId ID of the Business Network.
     */
    fun businessNetworkExists(networkId: String): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(networkIdCriteria(networkId))
        return vaultService.queryBy<MembershipState>(criteria).states.isNotEmpty()
    }

    /**
     * Queries for [MembershipState] with [party] identity inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     * @param party Identity of the member.
     *
     * @return State and ref pair of [MembershipState] matching the query. If that state doesn't exist, returns [null].
     */
    fun getMembership(networkId: String, party: Party): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(identityCriteria(party))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    /**
     * Queries for [MembershipState] with [linearId] linear ID.
     *
     * @param linearId linear ID of the [MembershipState].
     *
     * @return State and ref pair of [MembershipState] matching the query. If that state doesn't exist, returns [null].
     */
    fun getMembership(linearId: UniqueIdentifier): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    /**
     * Queries for all [MembershipState]s inside Business Network with [networkId] with one of [statuses].
     *
     * @param networkId ID of the Business Network.
     * @param statuses [MembershipStatus] of the memberships to be fetched.
     *
     * @return List of state and ref pairs of [MembershipState]s matching the query.
     */
    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<StateAndRef<MembershipState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(statusCriteria(statuses.toList()))
        return vaultService.queryBy<MembershipState>(criteria).states
    }

    /**
     * Queries for all [MembershipState]s inside Business Network with [networkId] ID authorised to modify membership (have at least one
     * administrative given).
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of [MembershipState]s matching the query.
     */
    fun getMembersAuthorisedToModifyMembership(networkId: String): List<StateAndRef<MembershipState>> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        it.state.data.toBusinessNetworkMembership().canModifyMembership()
    }

    /** Instantiates custom vault query criteria for finding [MembershipState] with given [networkId]. **/
    private fun networkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding [MembershipState] with given [cordaIdentity]. **/
    private fun identityCriteria(cordaIdentity: Party) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::cordaIdentity.equal(cordaIdentity) })

    /** Instantiates custom vault query criteria for finding [MembershipState] with any of given [statuses]. **/
    private fun statusCriteria(statuses: List<MembershipStatus>) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(statuses) })
}

/**
 * Handles all [GroupState] related vault queries.
 *
 * @property vaultService Corda's [VaultService] used to query node's vault for Business Network Group information.
 */
class GroupStorage(private val vaultService: VaultService) {

    /**
     * Checks whether [GroupState] with [linearId] linear ID exists.
     *
     * @param linearId linear ID of the [GroupState].
     */
    fun businessNetworkGroupExists(linearId: UniqueIdentifier): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
        return vaultService.queryBy<GroupState>(criteria).states.isNotEmpty()
    }

    /**
     * Queries for [GroupState] with [linearId] linear ID.
     *
     * @param linearId linear ID of the [GroupState].
     *
     * @return State and ref pair of [GroupState] matching the query. If that state doesn't exist, return [null].
     */
    fun getBusinessNetworkGroup(linearId: UniqueIdentifier): StateAndRef<GroupState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
        val states = vaultService.queryBy<GroupState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    /**
     * Queries for all [GroupState]s inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of [GroupState]s matching the query.
     */
    fun getAllBusinessNetworkGroups(networkId: String): List<StateAndRef<GroupState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
        return vaultService.queryBy<GroupState>(criteria).states
    }

    /** Instantiates custom vault query criteria for finding [GroupState] with given [networkId]. **/
    private fun networkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { GroupStateSchemaV1.PersistentGroupState::networkId.equal(networkId) })
}

/**
 * Concrete implementation of [BusinessNetworksService] based on states, contracts and transaction implementation of memberships and
 * groups which are stored in node's vault.
 *
 * @property vaultService Corda's [VaultService] used to query node's vault for Business Network information.
 * @property membershipStorage handles all Business Network Membership related queries.
 * @property groupStorage handles all Business Network Group related queries.
 */
@Suppress("SpreadOperator", "TooManyFunctions")
class VaultBusinessNetworksService(private val vaultService: VaultService) : BusinessNetworksService, SingletonSerializeAsToken() {

    val membershipStorage = MembershipStorage(vaultService)
    val groupStorage = GroupStorage(vaultService)

    override fun getAllBusinessNetworkIds(): List<String> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        return vaultService.queryBy<MembershipState>(criteria).states.map { it.state.data.networkId }.toSet().toList()
    }

    override fun businessNetworkExists(networkId: String): Boolean = membershipStorage.businessNetworkExists(networkId)

    override fun getMembership(networkId: String, party: Party): BusinessNetworkMembership? =
            membershipStorage.getMembership(networkId, party)?.state?.data?.toBusinessNetworkMembership()

    override fun getMembership(membershipId: UniqueIdentifier): BusinessNetworkMembership? =
            membershipStorage.getMembership(membershipId)?.state?.data?.toBusinessNetworkMembership()

    override fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership> =
            membershipStorage.getAllMembershipsWithStatus(networkId, *statuses).map { it.state.data.toBusinessNetworkMembership() }

    override fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        it.canModifyMembership()
    }

    override fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean = groupStorage.businessNetworkGroupExists(groupId)

    override fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup? =
            groupStorage.getBusinessNetworkGroup(groupId)?.state?.data?.toBusinessNetworkGroup()

    override fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup> =
            groupStorage.getAllBusinessNetworkGroups(networkId).map { it.state.data.toBusinessNetworkGroup() }

    override fun activateMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = ActivateMembershipFlow(membershipId, notary)

    override fun createBusinessNetworkFlow(
            networkId: UniqueIdentifier,
            businessIdentity: BNIdentity?,
            groupId: UniqueIdentifier,
            groupName: String?,
            notary: Party?
    ): MembershipManagementFlow<*> = CreateBusinessNetworkFlow(networkId, businessIdentity, groupId, groupName, notary)

    override fun createGroupFlow(
            networkId: String,
            groupId: UniqueIdentifier,
            groupName: String?,
            additionalParticipants: Set<UniqueIdentifier>,
            notary: Party?
    ): MembershipManagementFlow<*> = CreateGroupFlow(networkId, groupId, groupName, additionalParticipants, notary)

    override fun deleteGroupFlow(groupId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = DeleteGroupFlow(groupId, notary)

    override fun modifyBusinessIdentityFlow(membershipId: UniqueIdentifier, businessIdentity: BNIdentity, notary: Party?): MembershipManagementFlow<*> =
            ModifyBusinessIdentityFlow(membershipId, businessIdentity, notary)

    override fun modifyGroupFlow(groupId: UniqueIdentifier, name: String?, participants: Set<UniqueIdentifier>?, notary: Party?): MembershipManagementFlow<*> =
            ModifyGroupFlow(groupId, name, participants, notary)

    override fun modifyRolesFlow(membershipId: UniqueIdentifier, roles: Set<BNRole>, notary: Party?): MembershipManagementFlow<*> =
            ModifyRolesFlow(membershipId, roles, notary)

    override fun requestMembershipFlow(authorisedParty: Party, networkId: String, businessIdentity: BNIdentity?, notary: Party?): MembershipManagementFlow<*> =
            RequestMembershipFlow(authorisedParty, networkId, businessIdentity, notary)

    override fun revokeMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = RevokeMembershipFlow(membershipId, notary)

    override fun suspendMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = SuspendMembershipFlow(membershipId, notary)
}
