package net.corda.finance.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import org.hibernate.annotations.Type
import javax.persistence.*

/**
 * An object used to fully qualify the [CashSchema] family name (i.e. independent of version).
 */
object CashSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
@CordaSerializable
object CashSchemaV1 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "contract_cash_states",
            indexes = arrayOf(Index(name = "ccy_code_idx", columnList = "ccy_code"),
                    Index(name = "pennies_idx", columnList = "pennies")))
    class PersistentCashState(
            /** X500Name of owner party **/
            @Column(name = "owner_name", nullable = true)
            var owner: AbstractParty,

            @Column(name = "pennies", nullable = false)
            var pennies: Long,

            @Column(name = "ccy_code", length = 3, nullable = false)
            var currency: String,

            @Column(name = "issuer_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var issuerPartyHash: String,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE, nullable = false)
            @Type(type = "corda-wrapper-binary")
            var issuerRef: ByteArray
    ) : PersistentState()
}
