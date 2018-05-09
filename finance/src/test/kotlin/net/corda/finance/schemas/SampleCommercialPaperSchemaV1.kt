/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.schemas

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import org.hibernate.annotations.Type
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * An object used to fully qualify the [CommercialPaperSchema] family name (i.e. independent of version).
 */
object CommercialPaperSchema

/**
 * First version of a commercial paper contract ORM schema that maps all fields of the [CommercialPaper] contract state
 * as it stood at the time of writing.
 */
object SampleCommercialPaperSchemaV1 : MappedSchema(schemaFamily = CommercialPaperSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCommercialPaperState::class.java)) {

    override val migrationResource = "sample-cp-v1.changelog-init"

    @Entity
    @Table(name = "cp_states_v1",
            indexes = arrayOf(Index(name = "ccy_code_index", columnList = "ccy_code"),
                    Index(name = "maturity_index", columnList = "maturity_instant"),
                    Index(name = "face_value_index", columnList = "face_value")))
    class PersistentCommercialPaperState(
            @Column(name = "issuance_key_hash", length = MAX_HASH_HEX_SIZE)
            var issuancePartyHash: String,

            @Column(name = "issuance_ref")
            @Type(type = "corda-wrapper-binary")
            var issuanceRef: ByteArray,

            @Column(name = "owner_key_hash", length = MAX_HASH_HEX_SIZE)
            var ownerHash: String,

            @Column(name = "maturity_instant")
            var maturity: Instant,

            @Column(name = "face_value")
            var faceValue: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value_issuer_key_hash", length = MAX_HASH_HEX_SIZE)
            var faceValueIssuerPartyHash: String,

            @Column(name = "face_value_issuer_ref", length = MAX_ISSUER_REF_SIZE)
            @Type(type = "corda-wrapper-binary")
            var faceValueIssuerRef: ByteArray
    ) : PersistentState()
}
