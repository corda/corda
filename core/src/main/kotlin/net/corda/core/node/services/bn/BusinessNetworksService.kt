package net.corda.core.node.services.bn

import net.corda.core.DoNotImplement
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.bn.MembershipManagementFlow
import net.corda.core.identity.Party

/**
 * A [BusinessNetworksService] provides APIs that allows CorDapps to query Business Network related information and instantiate
 * implementation specific administrative Business Network management flows.
 */
@DoNotImplement
@Suppress("TooManyFunctions")
interface BusinessNetworksService {

    /**
     * Queries for all Business Networks' IDs caller is part of.
     *
     * @return List of Business Networks' [networkId]s.
     */
    fun getAllBusinessNetworkIds(): List<String>

    /**
     * Checks whether Business Network with [networkId] ID exists.
     *
     * @param networkId ID of the Business Network.
     */
    fun businessNetworkExists(networkId: String): Boolean

    /**
     * Queries for membership with [party] identity inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     * @param party Identity of the member.
     *
     * @return Membership matching the query. If that membership doesn't exist, returns [null].
     */
    fun getMembership(networkId: String, party: Party): BusinessNetworkMembership?

    /**
     * Queries for membership with [membershipId] linear ID.
     *
     * @param membershipId ID of the [BusinessNetworkMembership].
     *
     * @return Membership matching the query. If that membership doesn't exist, returns [null].
     */
    fun getMembership(membershipId: UniqueIdentifier): BusinessNetworkMembership?

    /**
     * Queries for all the memberships inside Business Network with [networkId] with one of [statuses].
     *
     * @param networkId ID of the Business Network.
     * @param statuses [MembershipStatus] of the memberships to be fetched.
     *
     * @return List of memberships matching the query.
     */
    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership>

    /**
     * Queries for all members inside Business Network with [networkId] ID authorised to modify membership (have at least one
     * administrative given).
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of authorised memberships.
     */
    fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership>

    /**
     * Checks whether Business Network Group with [groupId] ID exists.
     *
     * @param groupId ID of the Business Network Group.
     */
    fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean

    /**
     * Queries for Business Network Group with [groupId] ID.
     *
     * @param groupId ID of the Business Network Group.
     *
     * @return Business Network Group matching the query. If that group doesn't exist, return [null].
     */
    fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup?

    /**
     * Queries for all Business Network Groups inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of Business Network Groups matching the query.
     */
    fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup>

    /**
     * Instantiates flow responsible for membership activation with necessary flow arguments.
     */
    fun activateMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for Business Network creation with necessary flow arguments.
     */
    fun createBusinessNetworkFlow(
            networkId: UniqueIdentifier,
            businessIdentity: BNIdentity?,
            groupId: UniqueIdentifier,
            groupName: String?,
            notary: Party?
    ): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for Business Network Group creation with necessary flow arguments.
     */
    fun createGroupFlow(
            networkId: String,
            groupId: UniqueIdentifier,
            groupName: String?,
            additionalParticipants: Set<UniqueIdentifier>,
            notary: Party?
    ): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for Business Network Group deletion with necessary flow arguments.
     */
    fun deleteGroupFlow(groupId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for membership business identity modification with necessary flow arguments.
     */
    fun modifyBusinessIdentityFlow(membershipId: UniqueIdentifier, businessIdentity: BNIdentity, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for Business Network Group modification with necessary flow arguments.
     */
    fun modifyGroupFlow(groupId: UniqueIdentifier, name: String?, participants: Set<UniqueIdentifier>?, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for membership roles modification with necessary flow arguments.
     */
    fun modifyRolesFlow(membershipId: UniqueIdentifier, roles: Set<BNRole>, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for membership request with necessary flow arguments.
     */
    fun requestMembershipFlow(authorisedParty: Party, networkId: String, businessIdentity: BNIdentity?, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for membership revocation with necessary flow arguments.
     */
    fun revokeMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    /**
     * Instantiates flow responsible for membership suspension with necessary flow arguments.
     */
    fun suspendMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>
}