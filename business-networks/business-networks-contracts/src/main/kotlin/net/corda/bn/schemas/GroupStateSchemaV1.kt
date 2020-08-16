package net.corda.bn.schemas

import net.corda.bn.states.GroupState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * A database schema configured to represent [GroupState].
 */
object GroupStateSchemaV1 : MappedSchema(schemaFamily = GroupState::class.java, version = 1, mappedTypes = listOf(PersistentGroupState::class.java)) {

    /**
     * Mapped [GroupState] to be exported to a schema.
     *
     * @property networkId Mapped column for [GroupState.networkId].
     */
    @Entity
    @Table(name = "group_state")
    class PersistentGroupState(
            @Column(name = "network_id")
            val networkId: String
    ) : PersistentState()
}
