package net.corda.finance.test

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
object SampleCashSchemaV3 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 3,
        mappedTypes = listOf(PersistentCashState::class.java)) {
    @Entity
    @Table(name = "cash_states_v3")
    class PersistentCashState(
            /** [ContractState] attributes */

            /** X500Name of participant parties **/
            @ElementCollection
            var participants: MutableSet<AbstractParty?>? = null,

            /** X500Name of owner party **/
            @Column(name = "owner_name", nullable = true)
            var owner: AbstractParty?,

            @Column(name = "pennies", nullable = false)
            var pennies: Long,

            @Column(name = "ccy_code", length = 3, nullable = false)
            var currency: String,

            /** X500Name of issuer party **/
            @Column(name = "issuer_name", nullable = true)
            var issuer: AbstractParty?,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE, nullable = false)
            @JdbcTypeCode(SqlTypes.VARBINARY)
            var issuerRef: ByteArray
    ) : PersistentState()
}
