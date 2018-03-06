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

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import javax.persistence.*

/**
 * An object used to fully qualify the [DummyDealStateSchema] family name (i.e. independent of version).
 */
object DummyDealStateSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [DummyDealState] contract state as it stood
 * at the time of writing.
 */
object DummyDealStateSchemaV1 : MappedSchema(schemaFamily = DummyDealStateSchema.javaClass, version = 1, mappedTypes = listOf(PersistentDummyDealState::class.java)) {

    override val migrationResource = "dummy-deal.changelog-init"

    @Entity
    @Table(name = "dummy_deal_states")
    class PersistentDummyDealState(
            /** parent attributes */
            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name = "dummy_deal_states_parts", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            override var participants: MutableSet<AbstractParty>? = null,

            @Transient
            val uid: UniqueIdentifier

    ) : CommonSchemaV1.LinearState(uuid = uid.id, externalId = uid.externalId, participants = participants)
}
