package net.corda.bn.states

import net.corda.bn.contracts.GroupContract
import net.corda.bn.schemas.GroupStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import java.time.Instant

/**
 * Represents a Business Network groups on ledger.
 *
 * @property networkId Unique identifier of a Business Network group belongs to.
 * @property name Name of group, more descriptive way to distinct groups rather than linear ID.
 * @property issued Timestamp when the state has been issued.
 * @property modified Timestamp when the state has been modified last time.
 */
@BelongsToContract(GroupContract::class)
data class GroupState(
        val networkId: String,
        val name: String? = null,
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<Party>
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is GroupStateSchemaV1 -> GroupStateSchemaV1.PersistentGroupState(networkId = networkId)
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(GroupStateSchemaV1)
}