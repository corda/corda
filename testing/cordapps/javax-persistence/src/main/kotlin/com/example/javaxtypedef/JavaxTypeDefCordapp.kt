package com.example.javaxtypedef

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import org.hibernate.annotations.TypeDef
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * A CorDapp built against `javax.persistence` for Corda 4.12 whose schema uses Hibernate 5's `@TypeDef`, which has no equivalent in
 * Hibernate 7: the conversion refuses it, and the tests show the message a developer gets.
 */
object JavaxTypeDefSchema

object JavaxTypeDefSchemaV1 : MappedSchema(JavaxTypeDefSchema::class.java, 1, listOf(PersistentJavaxTypeDefState::class.java))

@Entity
@Table(name = "javax_typedef_states")
@TypeDef(name = "label-type", typeClass = Any::class)
class PersistentJavaxTypeDefState(@Column(name = "label", nullable = false) var label: String) : PersistentState() {
    constructor() : this("")
}

class JavaxTypeDefContract : Contract {
    class Create : CommandData

    override fun verify(tx: LedgerTransaction) {}
}

@BelongsToContract(JavaxTypeDefContract::class)
data class JavaxTypeDefState(val label: String, override val participants: List<AbstractParty>) : QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(JavaxTypeDefSchemaV1)
    override fun generateMappedObject(schema: MappedSchema): PersistentState = PersistentJavaxTypeDefState(label)
}
