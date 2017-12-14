package net.corda.finance.schemas

import net.corda.core.contracts.MAX_ISSUER_REF_SIZE
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.Table

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
            var participants: MutableSet<AbstractParty>? = null,

            /** X500Name of owner party **/
            @Column(name = "owner_name")
            var owner: AbstractParty,

            @Column(name = "pennies")
            var pennies: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            /** X500Name of issuer party **/
            @Column(name = "issuer_name")
            var issuer: AbstractParty,

            @Column(name = "issuer_ref", length = MAX_ISSUER_REF_SIZE)
            @Type(type = "corda-wrapper-binary")
            var issuerRef: ByteArray
    ) : PersistentState()
}
