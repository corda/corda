package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.bn.BNRole
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.MembershipIdentity
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import java.time.Instant

/**
 * Represents [BusinessNetworkMembership] on the ledger.
 *
 * @property identity Identity of a member.
 * @property networkId Unique identifier of a Business Network membership belongs to.
 * @property status Status of the state (i.e. PENDING, ACTIVE, SUSPENDED).
 * @property roles Set of all the roles associated to the membership.
 * @property issued Timestamp when the state has been issued.
 * @property modified Timestamp when the state has been modified last time.
 */
@BelongsToContract(MembershipContract::class)
data class MembershipState(
        val identity: MembershipIdentity,
        val networkId: String,
        val status: MembershipStatus,
        val roles: Set<BNRole> = emptySet(),
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState, QueryableState {

    /** Converts [MembershipState] to [BusinessNetworkMembership]. **/
    fun toBusinessNetworkMembership(): BusinessNetworkMembership = BusinessNetworkMembership(
            identity = identity,
            networkId = networkId,
            status = status,
            roles = roles,
            issued = issued,
            modified = modified,
            membershipId = linearId,
            participants = participants
    )

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                cordaIdentity = identity.cordaIdentity,
                networkId = networkId,
                status = status
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)
}