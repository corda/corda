package net.corda.observerdemo.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.observerdemo.Observed
import java.security.PublicKey
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

/**
 * Contract for managing lifecycle of a receivable which is recorded on the distributed ledger. These are entered by
 * a third party (typically a potential creditor), and then shared using the trade finance registry, allowing others to
 * attach/detach notices of ownership interest/objection.
 *
 * States of this contract *are not* fungible, and as such special rules apply. States must be unique within the
 * inputs/outputs, and strictly ordered, in order to make it easy to verify that outputs match the inputs except where
 * commands mean there are changes.
 */
class ReceivableContract : Contract {
    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.observerdemo.contracts.ReceivableContract"
        fun generateMove(ptx: TransactionBuilder,
                         receivable: StateAndRef<Receivable>,
                         owner: AbstractParty): Set<AbstractParty> {
            val (command, output) = receivable.state.data.withNewOwner(owner)
            ptx.addInputState(receivable)

            ptx.addCommand(command, receivable.state.data.owner.owningKey)
            ptx.addCommand(Observed(), listOf(receivable.state.data.observer.owningKey))
            ptx.addOutputState(output, PROGRAM_ID)
            return setOf(receivable.state.data.owner)
        }

        fun generateIssue(ptx: TransactionBuilder,
                          observer: Party,
                          receivableIssueRef: String?,
                          receivableCreated: ZonedDateTime,
                          accountDebtor: Party,
                          receivableValue: Amount<Currency>,
                          owner: AbstractParty,
                          notary: Party): Set<PublicKey> {
            val receivable = Receivable.build(receivableIssueRef, observer, receivableCreated, accountDebtor, receivableValue, owner)
            ptx.addOutputState(receivable, PROGRAM_ID, notary)
            ptx.addCommand(Commands.Issue(NonEmptySet.of(receivable.linearId)), owner.owningKey)
            ptx.addCommand(Observed(), observer.owningKey)
            ptx.setTimeWindow(receivable.filingDate, Duration.ofSeconds(30))
            return setOf(owner.owningKey)
        }
    }

    override fun verify(tx: LedgerTransaction) {
        val inputs = tx.inputsOfType<Receivable>()
        val outputs = tx.outputsOfType<Receivable>()
        val observedCommand = tx.commands.requireSingleCommand<Observed>()
        val observerPubKeys = HashSet<PublicKey>()
        inputs.forEach { observerPubKeys.add(it.observer.owningKey) }
        outputs.forEach { observerPubKeys.add(it.observer.owningKey) }
        requireThat {
            "transaction has been sent to all observers" using (observedCommand.signers.containsAll(observerPubKeys))
        }
        val activeCommand = tx.commands.requireSingleCommand<Commands>()
        when (activeCommand.value) {
            is Commands.Issue -> verifyIssue(tx, inputs, outputs, activeCommand as CommandWithParties<Commands.Issue>)
            is Commands.Exit -> verifyExit(tx, inputs, outputs, activeCommand as CommandWithParties<Commands.Exit>)
            is Commands.Move -> verifyMove(tx, inputs, outputs, activeCommand as CommandWithParties<Commands.Move>)
        }
    }

    private fun verifyIssue(tx: LedgerTransaction,
                            inputs: List<Receivable>,
                            outputs: List<Receivable>,
                            command: CommandWithParties<Commands.Issue>) {
        val timestamp = tx.timeWindow

        // Records for receivables are never fungible, so we just want to make sure all inputs exist as
        // outputs, and there are new outputs.
        requireThat {
            "there are more output states than input states" using (outputs.size > inputs.size)
            // TODO: Should timestamps perhaps be enforced on all receivable transactions?
            "the transaction has a timestamp" using (timestamp != null)
        }

        val expectedOutputs = ArrayList(inputs)
        val keysThatSigned = command.signers
        val owningPubKeys = HashSet<PublicKey>()
        outputs
                .filter { it.linearId in command.value.changed }
                .forEach { state ->
                    val filingInLocalZone = state.filingDate.atZone(state.requestDate.zone)
                    requireThat {
                        "the state is filed at or after it was requested" using (filingInLocalZone >= state.requestDate)
                        // TODO: Should narrow the window on how long ago the registration can be compared to the transaction
                        "the state is filed at or before the transaction date: ${state.filingDate} < ${timestamp?.untilTime}" using (state.filingDate <= timestamp?.untilTime)
                    }
                    owningPubKeys.add(state.owner.owningKey)
                    expectedOutputs.add(state)
                }
        // Re-sort the outputs now we've finished changing them
        expectedOutputs.sortBy { state -> state.linearId }
        requireThat {
            "the owning keys are a subset of the signing keys" using keysThatSigned.containsAll(owningPubKeys)
            "outputs match inputs with expected changes applied" using (outputs == expectedOutputs)
        }
    }

    private fun verifyExit(tx: LedgerTransaction,
                           inputs: List<Receivable>,
                           outputs: List<Receivable>,
                           command: CommandWithParties<Commands.Exit>) {
        val unmatchedIds = HashSet<UniqueIdentifier>(command.value.changed)
        val owningPubKeys = HashSet<PublicKey>()
        val expectedOutputs = inputs.filter { input ->
            if (unmatchedIds.contains(input.linearId)) {
                unmatchedIds.remove(input.linearId)
                owningPubKeys.add(input.owner.owningKey)
                false
            } else {
                true
            }
        }
        val keysThatSigned = command.signers
        requireThat {
            "inputs are not empty" using inputs.isNotEmpty()
            "outputs match inputs with expected changes applied" using (outputs == expectedOutputs)
            "the owning keys are a subset of the signing keys" using keysThatSigned.containsAll(owningPubKeys)
        }
    }

    private fun verifyMove(tx: LedgerTransaction,
                           inputs: List<Receivable>,
                           outputs: List<Receivable>,
                           command: CommandWithParties<Commands.Move>) {
        val changes = command.value.changes
        require(changes.size <= inputs.size) // Sanity check the command before we build a map from it
        val changesById = changes.toMap()
        // Rebuild the outputs we expect, then compare. Receivables are not fungible, so inputs and outputs
        // must match one to one
        val expectedOutputs: List<OwnableState> = inputs.map { input ->
            val newOwner = changesById[input.linearId]
            if (newOwner != null) {
                input.withNewOwner(newOwner).ownableState
            } else {
                input
            }
        }
        requireThat {
            "inputs are not empty" using inputs.isNotEmpty()
            "outputs match inputs with expected changes applied" using (outputs == expectedOutputs)
        }
        // Do standard move command checks including the signature checks
        verifyMoveCommand<Commands.Move>(inputs, listOf(command))
    }
}
