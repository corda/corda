package net.corda.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.node.services.vault.schemas.jpa.CommonSchemaV1
import javax.persistence.*

/**
 * First version of a cash contract ORM schema that maps all fields of the [Cash] contract state as it stood
 * at the time of writing.
 */
object SampleCashSchemaV3 : MappedSchema(schemaFamily = CashSchema.javaClass, version = 3,
                                   mappedTypes = listOf(PersistentCashState::class.java, CommonSchemaV1.Party::class.java)) {
    @Entity
    @Table(name = "cash_states_v3")
    class PersistentCashState(
            /** [ContractState] attributes */
            @OneToMany(cascade = arrayOf(CascadeType.ALL))
            var participants: Set<CommonSchemaV1.Party>,

            @OneToOne(cascade = arrayOf(CascadeType.ALL))
            var owner: CommonSchemaV1.Party,

            @Column(name = "pennies")
            var pennies: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @OneToOne(cascade = arrayOf(CascadeType.ALL))
            var issuerParty: CommonSchemaV1.Party,

            @Column(name = "issuer_ref")
            var issuerRef: ByteArray
    ) : PersistentState() {
        constructor(_participants: Set<AbstractParty>, _owner: AbstractParty, _quantity: Long, _currency: String, _issuerParty: AbstractParty, _issuerRef: ByteArray)
                : this(participants = _participants.map { CommonSchemaV1.Party(it) }.toSet(),
                        owner = CommonSchemaV1.Party(_owner),
                        pennies = _quantity,
                        currency = _currency,
                        issuerParty = CommonSchemaV1.Party(_issuerParty),
                        issuerRef = _issuerRef)
    }
}
