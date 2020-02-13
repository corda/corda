package com.r3.dbfailure.contracts

import com.r3.dbfailure.schemas.DbFailureSchemaV1
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
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
            override val participants: List<AbstractParty>,
            val randomValue: String?,
            val errorTarget: Int = 0,
            override val owner: AbstractParty
    ) : LinearState, QueryableState, OwnableState {


        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DbFailureSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return if (schema is DbFailureSchemaV1){
                DbFailureSchemaV1.PersistentTestState( participants.toString(), randomValue, errorTarget, linearId.id)
            }
            else {
                throw IllegalArgumentException("Unsupported schema $schema")
            }
        }

        override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
            return CommandAndState(Commands.Send(), TestState(this.linearId, this.participants.plus(newOwner).toSet().toList(), this.randomValue, this.errorTarget, newOwner))
        }

        fun withNewOwnerAndErrorTarget(newOwner: AbstractParty, errorTarget: Int): CommandAndState {
            return CommandAndState(Commands.Send(), TestState(this.linearId, this.participants.plus(newOwner).toSet().toList(), this.randomValue, errorTarget, newOwner))
        }
    }

    override fun verify(tx: LedgerTransaction) {
        // no op - don't care for now
    }

    interface Commands : CommandData{
        class Create: Commands
        class Send : Commands
    }
}