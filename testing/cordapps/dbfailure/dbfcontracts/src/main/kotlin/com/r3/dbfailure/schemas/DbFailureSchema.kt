package com.r3.dbfailure.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object DbFailureSchema

object DbFailureSchemaV1 : MappedSchema(
    schemaFamily = DbFailureSchema.javaClass,
    version = 1,
    mappedTypes = listOf(DbFailureSchemaV1.PersistentTestState::class.java)){
    override val migrationResource = "dbfailure.changelog-master"

    @Entity
    @Table( name = "fail_test_states")
    class PersistentTestState(
        @Column( name = "participant")
        var participantName: String,

        @Column( name = "random_value", nullable = false)
        var randomValue: String?,

        @Column( name = "error_target")
        var errorTarget: Int,

        @Column( name = "linear_id")
        var linearId: UUID
    ) : PersistentState() {
        constructor() : this( "", "", 0, UUID.randomUUID())
    }
}
