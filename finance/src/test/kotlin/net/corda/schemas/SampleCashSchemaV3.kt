package net.corda.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.CommonSchemaV1
import javax.persistence.*

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
            var participants: Set<String>,

            /** X500Name of anonymous owner party (after resolution by the IdentityService) **/
            var owner: String,

            @Column(name = "pennies")
            var pennies: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            /** X500Name of issuer party **/
            var issuer: String,

            @Column(name = "issuer_ref")
            var issuerRef: ByteArray
    ) : PersistentState() {
        constructor(_participants: Set<AbstractParty>, _owner: AbstractParty, _quantity: Long, _currency: String, _issuerParty: AbstractParty, _issuerRef: ByteArray)
                : this(participants = _participants.map { it.nameOrNull().toString() }.toSet(),
                        owner = _owner.nameOrNull().toString(),
                        pennies = _quantity,
                        currency = _currency,
                        issuer = _issuerParty.nameOrNull().toString(),
                        issuerRef = _issuerRef)
    }
}
