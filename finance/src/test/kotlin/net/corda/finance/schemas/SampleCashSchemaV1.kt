package net.corda.finance.schemas

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * An object used to fully qualify the [CashSchema] family name (i.e. independent of version).
 */
object CashSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
object SampleCashSchemaV1 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "contract_cash_states",
            indexes = arrayOf(Index(name = "ccy_code_idx", columnList = "ccy_code"),
                    Index(name = "pennies_idx", columnList = "pennies")))
    class PersistentCashState(
            @Column(name = "owner_key_hash", length = MAX_HASH_HEX_SIZE)
            var ownerHash: String,

            @Column(name = "pennies")
            var pennies: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "issuer_key_hash", length = MAX_HASH_HEX_SIZE)
            var issuerPartyHash: String,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE)
            @Type(type = "corda-wrapper-binary")
            var issuerRef: ByteArray
    ) : PersistentState()
}
