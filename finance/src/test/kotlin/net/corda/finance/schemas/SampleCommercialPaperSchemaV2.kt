package net.corda.finance.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * Second version of a cash contract ORM schema that extends the common
 * [VaultFungibleState] abstract schema
 */
object SampleCommercialPaperSchemaV2 : MappedSchema(schemaFamily = CommercialPaperSchema.javaClass, version = 1,
        mappedTypes = listOf(PersistentCommercialPaperState::class.java)) {
    @Entity
    @Table(name = "cp_states_v2",
            indexes = arrayOf(Index(name = "ccy_code_index2", columnList = "ccy_code"),
                    Index(name = "maturity_index2", columnList = "maturity_instant")))
    class PersistentCommercialPaperState(
            @Column(name = "maturity_instant")
            var maturity: Instant,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value_issuer_key")
            var faceValueIssuerParty: String,

            @Column(name = "face_value_issuer_ref")
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
            val _issuerRef: ByteArray
    ) : CommonSchemaV1.FungibleState(_participants.toMutableSet(), _owner, _quantity, _issuerParty, _issuerRef)
}
