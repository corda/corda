package net.corda.node.testing

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@CordaSerializable
data class MessageData(val value: String)

@BelongsToContract(MessageChainContract::class)
data class MessageChainState(val message: MessageData, val by: Party, override val linearId: UniqueIdentifier = UniqueIdentifier(), val extraParty: Party? = null) : LinearState, QueryableState {
    override val participants: List<AbstractParty> = if (extraParty == null) listOf(by) else listOf(by, extraParty)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is MessageChainSchemaV1 -> MessageChainSchemaV1.PersistentMessage(
                    by = by.name.toString(),
                    value = message.value
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(MessageChainSchemaV1)
}

object MessageChainSchema
object MessageChainSchemaV1 : MappedSchema(
        schemaFamily = MessageChainSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentMessage::class.java)) {

    @Entity
    @Table(name = "messages")
    class PersistentMessage(
            @Column(name = "message_by", nullable = false)
            var by: String,

            @Column(name = "message_value", nullable = false)
            var value: String
    ) : PersistentState()
}

const val MESSAGE_CHAIN_CONTRACT_PROGRAM_ID = "net.corda.node.testing.MessageChainContract"

open class MessageChainContract : Contract {
    private fun verifySend(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            // Generic constraints around the IOU transaction.
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<MessageChainState>().single()
            "Message sender must sign." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            "Message value must not be empty." using (out.message.value.isNotBlank())
        }
    }

    private fun verifySplit(tx: LedgerTransaction) {
        requireThat {
            "Two output state should be created." using (tx.outputs.size == 2)
        }
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>().value
        when (command) {
            is Commands.Send -> verifySend(tx)
            is Commands.Split -> verifySplit(tx)
        }
    }

    interface Commands : CommandData {
        class Send : Commands
        class Split : Commands
    }
}
