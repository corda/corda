package net.corda.groups.schemas

import net.corda.core.crypto.NullKeys
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

object GroupSchema

@CordaSerializable
object GroupSchemaV1 : MappedSchema(
        schemaFamily = GroupSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentGroupState::class.java)
) {
    @Entity
    @Table(name = "data_group_states")
    class PersistentGroupState(
            @Lob @Column(name = "group_key") var key: ByteArray,
            @Column(name = "group_name") var name: String
    ) : PersistentState() {
        @Suppress("UNUSED")
        constructor() : this(key = NullKeys.NullPublicKey.encoded, name = "")
    }
}