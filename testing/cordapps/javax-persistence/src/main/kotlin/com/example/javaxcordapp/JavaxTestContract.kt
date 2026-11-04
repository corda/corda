package com.example.javaxcordapp

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.util.UUID

class JavaxTestContract : Contract {
    class Create : CommandData

    override fun verify(tx: LedgerTransaction) {}
}

@BelongsToContract(JavaxTestContract::class)
data class JavaxTestState(val label: String, val ref: UUID, override val participants: List<AbstractParty>) : QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(JavaxTestSchemaV1)
    override fun generateMappedObject(schema: MappedSchema): PersistentState = PersistentJavaxTestState(label, ref)
}
