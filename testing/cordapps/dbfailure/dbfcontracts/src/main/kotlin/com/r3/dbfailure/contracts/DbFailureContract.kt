package com.r3.dbfailure.contracts

import com.r3.dbfailure.schemas.DbFailureSchemaV1
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

class DbFailureContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.r3.dbfailure.contracts.DbFailureContract"
    }

    class TestState(
            override val linearId: UniqueIdentifier,
            val particpant: Party,
            val randomValue: String?,
            val errorTarget: Int = 0
    ) : LinearState, QueryableState {

        override val participants: List<AbstractParty> = listOf(particpant)

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DbFailureSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return if (schema is DbFailureSchemaV1){
                DbFailureSchemaV1.PersistentTestState( particpant.name.toString(), randomValue, errorTarget, linearId.id)
            }
            else {
                throw IllegalArgumentException("Unsupported schema $schema")
            }
        }
    }

    override fun verify(tx: LedgerTransaction) {
        // no op - don't care for now
    }

    interface Commands : CommandData{
        class Create: Commands
    }
}