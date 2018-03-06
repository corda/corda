/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.sampleschemas

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.OpaqueBytes
import org.hibernate.annotations.Type
import java.time.Instant
import javax.persistence.*

/**
 * Second version of a cash contract ORM schema that extends the common
 * [VaultFungibleState] abstract schema
 */
object SampleCommercialPaperSchemaV2 : MappedSchema(schemaFamily = CommercialPaperSchema.javaClass, version = 1,
        mappedTypes = listOf(PersistentCommercialPaperState::class.java)) {

    override val migrationResource = "sample-cp-v2.changelog-init"

    @Entity
    @Table(name = "cp_states_v2",
            indexes = arrayOf(Index(name = "ccy_code_index2", columnList = "ccy_code"),
                    Index(name = "maturity_index2", columnList = "maturity_instant")))
    class PersistentCommercialPaperState(

            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name="cp_states_v2_participants", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            override var participants: MutableSet<AbstractParty>? = null,

            @Column(name = "maturity_instant")
            var maturity: Instant,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value_issuer_key_hash", length = MAX_HASH_HEX_SIZE)
            var faceValueIssuerPartyHash: String,

            @Column(name = "face_value_issuer_ref", length = MAX_ISSUER_REF_SIZE)
            @Type(type = "corda-wrapper-binary")
            var faceValueIssuerRef: ByteArray,

            /** parent attributes */
            @Transient
            val _participants: Set<AbstractParty>,
            @Transient
            val _owner: AbstractParty,
            @Transient
            // face value
            val _quantity: Long,
            @Transient
            val _issuerParty: AbstractParty,
            @Transient
            val _issuerRef: OpaqueBytes
    ) : CommonSchemaV1.FungibleState(_participants.toMutableSet(), _owner, _quantity, _issuerParty, _issuerRef.bytes)
}
