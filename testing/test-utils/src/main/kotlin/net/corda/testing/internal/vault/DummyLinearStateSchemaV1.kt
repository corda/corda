/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal.vault

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.*
import javax.persistence.*

/**
 * An object used to fully qualify the [DummyLinearStateSchema] family name (i.e. independent of version).
 */
object DummyLinearStateSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [DummyLinearState] contract state as it stood
 * at the time of writing.
 */
object DummyLinearStateSchemaV1 : MappedSchema(schemaFamily = DummyLinearStateSchema.javaClass, version = 1, mappedTypes = listOf(PersistentDummyLinearState::class.java)) {

    override val migrationResource = "dummy-linear-v1.changelog-init"

    @Entity
    @Table(name = "dummy_linear_states", indexes = [Index(name = "external_id_idx", columnList = "external_id"), Index(name = "uuid_idx", columnList = "uuid")])
    class PersistentDummyLinearState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name = "dummy_linear_state_parts", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            var participants: MutableSet<AbstractParty>,

            /**
             * UniqueIdentifier
             */
            @Column(name = "external_id", nullable = true)
            var externalId: String?,

            @Column(name = "uuid", nullable = false)
            @Type(type = "uuid-char")
            var uuid: UUID,

            /**
             *  Dummy attributes
             */
            @Column(name = "linear_string", nullable = true)
            var linearString: String?,

            @Column(name = "linear_number", nullable = false)
            var linearNumber: Long,

            @Column(name = "linear_timestamp", nullable = false)
            var linearTimestamp: Instant,

            @Column(name = "linear_boolean", nullable = false)
            var linearBoolean: Boolean
    ) : PersistentState()
}
