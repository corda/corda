package net.corda.finance.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * Second version of a cash contract ORM schema that extends the common
 * [VaultFungibleState] abstract schema
 */
object SampleCashSchemaV2 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 2,
        mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "cash_states_v2",
            indexes = arrayOf(Index(name = "ccy_code_idx2", columnList = "ccy_code")))
    class PersistentCashState(
            /** product type */
            @Column(name = "ccy_code", length = 3)
            var currency: String,

            /** parent attributes */
            @Transient
            val _participants: Set<AbstractParty>,
            @Transient
            val _owner: AbstractParty,
            @Transient
            val _quantity: Long,
            @Transient
            val _issuerParty: AbstractParty,
            @Transient
            val _issuerRef: ByteArray
    ) : CommonSchemaV1.FungibleState(_participants.toMutableSet(), _owner, _quantity, _issuerParty, _issuerRef)
}
