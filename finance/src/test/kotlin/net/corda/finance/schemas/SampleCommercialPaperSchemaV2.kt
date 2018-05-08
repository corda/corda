package net.corda.finance.schemas

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
    @Entity
    @Table(name = "cp_states_v2",
            indexes = [Index(name = "ccy_code_index2", columnList = "ccy_code"), Index(name = "maturity_index2", columnList = "maturity_instant")])
    class PersistentCommercialPaperState(
            @Column(name = "maturity_instant", nullable = false)
            var maturity: Instant,

            @Column(name = "ccy_code", length = 3, nullable = false)
            var currency: String,

            @Column(name = "face_value_issuer_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var faceValueIssuerPartyHash: String,

            @Column(name = "face_value_issuer_ref", length = MAX_ISSUER_REF_SIZE, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var faceValueIssuerRef: ByteArray,

            participants: Set<AbstractParty>,
            owner: AbstractParty,
            quantity: Long,
            issuerParty: AbstractParty,
            issuerRef: OpaqueBytes
    ) : CommonSchemaV1.FungibleState(participants.toMutableSet(), owner, quantity, issuerParty, issuerRef.bytes) {

        @ElementCollection
        @Column(name = "participants")
        @CollectionTable(name = "cp_states_v2_participants", joinColumns = [JoinColumn(name = "output_index", referencedColumnName = "output_index"), JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")])
        override var participants: MutableSet<AbstractParty>? = null
    }
}
