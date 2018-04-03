package net.corda

import net.corda.annotations.serialization.Serializable
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@Serializable
data class Message(val value: String)

data class MessageState(val message: Message, val by: Party, override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
    override val participants: List<AbstractParty> = listOf(by)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is MessageSchemaV1 -> MessageSchemaV1.PersistentMessage(
                    by = by.name.toString(),
                    value = message.value
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(MessageSchemaV1)
}

object MessageSchema
object MessageSchemaV1 : MappedSchema(
        schemaFamily = MessageSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentMessage::class.java)) {

    @Entity
    @Table(name = "messages")
    class PersistentMessage(
            @Column(name = "message_by")
            var by: String,

            @Column(name = "message_value")
            var value: String
    ) : PersistentState()
}

const val MESSAGE_CONTRACT_PROGRAM_ID = "net.corda.MessageContract"

open class MessageContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Send>()
        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when sending a message." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<MessageState>().single()
            "Message sender must sign." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            "Message value must not be empty." using (out.message.value.isNotBlank())
        }
    }

    interface Commands : CommandData {
        class Send : Commands
    }
}