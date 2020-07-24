package net.corda.core.node.services.bn

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Represents Business Network Group which is one of the multiple membership lists that Business Network can have. Each member is
 * subscribed to the changes of all memberships that are part of the groups he is member of.
 *
 * @property networkId Unique identifier of a Business Network group belongs to.
 * @property name Name of group, more descriptive way to distinct groups rather than group ID.
 * @property issued Timestamp when the group has been issued.
 * @property modified Timestamp when the group has been modified last time.
 * @property groupId Unique identifier of a group.
 * @property participants List of all parties whose memberships are part of the group.
 */
@CordaSerializable
data class BusinessNetworkGroup(
        val networkId: String,
        val name: String? = null,
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        val groupId: UniqueIdentifier,
        val participants: List<Party>
)