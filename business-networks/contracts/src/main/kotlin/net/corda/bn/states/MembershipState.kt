package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Represents a membership on the ledger.
 *
 * @property identity Corda Identity of a member.
 * @property networkId Unique identifier of a Business Network membership belongs to.
 * @property status Status of the state (i.e. PENDING, ACTIVE, SUSPENDED).
 * @property roles Set of all the roles associated to the membership.
 * @property issued Timestamp when the state has been issued.
 * @property modified Timestamp when the state has been modified last time.
 */
@BelongsToContract(MembershipContract::class)
data class MembershipState(
        val identity: Party,
        val networkId: String,
        val status: MembershipStatus,
        val roles: Set<BNRole> = emptySet(),
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                cordaIdentity = identity,
                networkId = networkId,
                status = status
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)

    fun isPending() = status == MembershipStatus.PENDING
    fun isActive() = status == MembershipStatus.ACTIVE
    fun isSuspended() = status == MembershipStatus.SUSPENDED

    private fun permissions() = roles.flatMap { it.permissions }.toSet()
    fun canActivateMembership() = AdminPermission.CAN_ACTIVATE_MEMBERSHIP in permissions()
    fun canSuspendMembership() = AdminPermission.CAN_SUSPEND_MEMBERSHIP in permissions()
    fun canRevokeMembership() = AdminPermission.CAN_REVOKE_MEMBERSHIP in permissions()
    fun canModifyRoles() = AdminPermission.CAN_MODIFY_ROLE in permissions()
    fun canModifyMembership() = permissions().isNotEmpty()
}

/**
 * Statuses that membership can go through.
 */
@CordaSerializable
enum class MembershipStatus {
    /**
     * Newly submitted state which hasn't been approved by authorised member yet. Pending members can't transact on the Business Network.
     */
    PENDING,

    /**
     * Active members can transact on the Business Network and modify other memberships if they are authorised.
     */
    ACTIVE,

    /**
     * Suspended members can't transact on the Business Network or modify other memberships. Suspended members can be activated back.
     */
    SUSPENDED
}

/**
 * Represents role associated with Business Network membership. Every custom Business Network related role should extend this class.
 *
 * @property name Name of the role.
 * @property permissions Set of permissions given to the role.
 */
@CordaSerializable
open class BNRole(val name: String, val permissions: Set<BNPermission>) {
    override fun equals(other: Any?) = other is BNRole && name == other.name && permissions == other.permissions
    override fun hashCode() = name.hashCode() + 31 * permissions.hashCode()
}

/**
 * Represents Business Network Operator (BNO) role which has all Business Network administrative permissions given.
 */
@CordaSerializable
class BNORole : BNRole("BNO", AdminPermission.values().toSet())

/**
 * Represents simple member which doesn't have any Business Network administrative permission given.
 */
@CordaSerializable
class MemberRole : BNRole("Member", emptySet())

/**
 * Represents permission given in the context of Business Network membership. Every custom Business Network related permission should
 * implement this interface.
 */
@CordaSerializable
interface BNPermission

/**
 * Business Network administrative permissions that can be given to a role.
 */
@CordaSerializable
enum class AdminPermission : BNPermission {
    /**
     * Enables member to activate Business Network memberships.
     */
    CAN_ACTIVATE_MEMBERSHIP,

    /**
     * Enables member to suspend Business Network memberships.
     */
    CAN_SUSPEND_MEMBERSHIP,

    /**
     * Enables member to revoke Business Network memberships.
     */
    CAN_REVOKE_MEMBERSHIP,

    /**
     * Enables member to modify memberships' roles.
     */
    CAN_MODIFY_ROLE
}