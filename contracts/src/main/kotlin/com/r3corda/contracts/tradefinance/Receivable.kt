package com.r3corda.contracts.tradefinance

import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.isOrderedAndUnique
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.utilities.NonEmptySet
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

/**
 * Contract for managing lifecycle of a receivable which is recorded on the distributed ledger. These are entered by
 * a third party (typically a potential creditor), and then shared by the trade finance registry, allowing others to
 * attach/detach notices of ownership interest/objection.
 *
 * States of this contract *are not* fungible, and as such special rules apply. States must be unique within the
 * inputs/outputs, and strictly ordered, in order to make it easy to verify that outputs match the inputs except where
 * commands mean there are changes.
 */
class Receivable : Contract {
    data class State(override val linearId: UniqueIdentifier = UniqueIdentifier(),
                     val created: ZonedDateTime, // When the underlying receivable was raised
                     val registered: Instant, // When the receivable was added to the registry
                     val payer: Party,
                     val payee: Party,
                     val payerRef: OpaqueBytes?,
                     val payeeRef: OpaqueBytes?,
                     val value: Amount<Issued<Currency>>,
                     val attachments: Set<SecureHash>,
                     val notices: List<Notice>,
                     override val owner: PublicKey) : OwnableState, LinearState {
        override val contract: Contract = Receivable()
        override val participants: List<PublicKey> = listOf(owner)
        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean
            = ourKeys.contains(payer.owningKey) || ourKeys.contains(payee.owningKey) || ourKeys.contains(owner)
        override fun withNewOwner(newOwner: PublicKey): Pair<CommandData, OwnableState>
            = Pair(Commands.Move(null, mapOf(Pair(linearId, newOwner))), copy(owner = newOwner))
    }

    interface Commands : CommandData {
        val changed: Iterable<UniqueIdentifier>
        data class Issue(override val changed: NonEmptySet<UniqueIdentifier>,
                         override val nonce: Long = random63BitValue()) : IssueCommand, Commands
        data class Move(override val contractHash: SecureHash?, val changes: Map<UniqueIdentifier, PublicKey>) : MoveCommand, Commands {
            override val changed: Iterable<UniqueIdentifier> = changes.keys
        }
        data class Note(val changes: Map<UniqueIdentifier, Diff<Notice>>) : Commands {
            override val changed: Iterable<UniqueIdentifier> = changes.keys
        }
        // TODO: Write Amend clause, possibly to merge into Move
        /* data class Amend(val id: UniqueIdentifier,
                         val payer: Party,
                         val payee: Party,
                         val payerRef: OpaqueBytes?,
                         val payeeRef: OpaqueBytes?,
                         val value: Amount<Issued<Currency>>,
                         val attachments: Set<SecureHash>) : Commands */
        data class Exit(override val changed: NonEmptySet<UniqueIdentifier>) : Commands
    }

    data class Diff<T : Any>(val added: List<T>, val removed: List<T>)

    interface Clauses {
        /**
         * Assert that each input/output state is unique within that list of states, and that states are ordered. There
         * should never be the same receivable twice in a transaction. Uniqueness is also enforced by the notary,
         * but we get the check as a side-effect of comparing states, so the duplication is acceptable.
         */
        class StatesAreOrderedAndUnique : Clause<State, Commands, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                // Enforce that states are ordered, so that the transaction can only be assembled in one way
                requireThat {
                    "input receivables are ordered and unique" by inputs.isOrderedAndUnique { linearId }
                    "output receivables are ordered and unique" by outputs.isOrderedAndUnique { linearId }
                }
                return emptySet()
            }
        }

        /**
         * Check that all inputs are present as outputs, and that all owners for new outputs have signed the command.
         */
        class Issue : Clause<State, Commands, Unit>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Issue::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(groupingKey == null)
                // TODO: Take in matched commands as a parameter
                val command = commands.requireSingleCommand<Commands.Issue>()
                val timestamp = tx.timestamp

                // Records for receivables are never fungible, so we just want to make sure all inputs exist as
                // outputs, and there are new outputs.
                requireThat {
                    "there are more output states than input states" by (outputs.size > inputs.size)
                    // TODO: Should timestamps perhaps be enforced on all receivable transactions?
                    "the transaction has a timestamp" by (timestamp != null)
                }

                val expectedOutputs = ArrayList(inputs)
                val keysThatSigned = command.signers
                val owningPubKeys = HashSet<PublicKey>()
                outputs
                        .filter { it.linearId in command.value.changed }
                        .forEach { state ->
                    val registrationInLocalZone = state.registered.atZone(state.created.zone)
                    requireThat {
                        "the receivable is registered after it was created" by (state.created < registrationInLocalZone)
                        // TODO: Should narrow the window on how long ago the registration can be compared to the transaction
                        "the receivable is registered before the transaction date" by (state.registered < timestamp?.before)
                    }
                    owningPubKeys.add(state.owner)
                    expectedOutputs.add(state)
                }
                // Re-sort the outputs now we've finished changing them
                expectedOutputs.sortBy { state -> state.linearId }
                requireThat {
                    "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
                    "outputs match inputs with expected changes applied" by outputs.equals(expectedOutputs)
                }

                return setOf(command.value as Commands)
            }
        }

        /**
         * Check that inputs and outputs are exactly the same, except for ownership changes specified in the command.
         * The command must be signed by the previous owners of all changed input states.
         */
        class Move : Clause<State, Commands, Unit>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Move::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(groupingKey == null)
                // TODO: Take in matched commands as a parameter
                val moveCommand = commands.requireSingleCommand<Commands.Move>()
                val changes = moveCommand.value.changes
                // Rebuild the outputs we expect, then compare. Receivables are not fungible, so inputs and outputs
                // must match one to one
                val expectedOutputs: List<State> = inputs.map { input ->
                    val newOwner = changes[input.linearId]
                    if (newOwner != null) {
                        input.copy(owner = newOwner)
                    } else {
                        input
                    }
                }
                requireThat {
                    "inputs are not empty" by inputs.isNotEmpty()
                    "outputs match inputs with expected changes applied" by outputs.equals(expectedOutputs)
                }
                // Do standard move command checks including the signature checks
                verifyMoveCommand<Commands.Move>(inputs, commands)
                return setOf(moveCommand.value as Commands)
            }
        }

        /**
         * Add and/or remove notices on receivables. All input states must match output states, except for the
         * changed notices.
         */
        class Note : Clause<State, Commands, Unit>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Note::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(groupingKey == null)
                // TODO: Take in matched commands as a parameter
                val command = commands.requireSingleCommand<Commands.Note>()
                // Rebuild the outputs we expect, then compare. Receivables are not fungible, so inputs and outputs
                // must match one to one
                val (expectedOutputs, owningPubKeys) = deriveOutputStates(inputs, command)
                val keysThatSigned = command.signers
                requireThat {
                    "inputs are not empty" by inputs.isNotEmpty()
                    "outputs match inputs with expected changes applied" by outputs.equals(expectedOutputs)
                    "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
                }
                return setOf(command.value as Commands)
            }

            fun deriveOutputStates(inputs: List<State>,
                                   command: AuthenticatedObject<Commands.Note>): Pair<List<State>, Set<PublicKey>> {
                val changes = command.value.changes
                val seenNotices = HashSet<Notice>()
                val outputs = inputs.map { input ->
                    val stateChanges = changes[input.linearId]
                    if (stateChanges != null) {
                        val notices = ArrayList<Notice>(input.notices)
                        stateChanges.added.forEach { notice ->
                            require(!seenNotices.contains(notice)) { "Notices can only appear once in the add and/or remove lists" }
                            require(!notices.contains(notice)) { "Notice is already present on the receivable" }
                            seenNotices.add(notice)
                            notices.add(notice)
                        }
                        stateChanges.removed.forEach { notice ->
                            require(!seenNotices.contains(notice)) { "Notices can only appear once in the add and/or remove lists" }
                            require(notices.remove(notice)) { "Notice is not present on the receivable" }
                            seenNotices.add(notice)
                        }
                        input.copy(notices = notices)
                    } else {
                        input
                    }
                }
                return Pair(outputs, seenNotices.map { it.owner }.toSet() )
            }
        }

        /**
         * Remove a receivable from the ledger. This can only be done once all notices have been removed.
         */
        class Exit : Clause<State, Commands, Unit>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Exit::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(groupingKey == null)
                // TODO: Take in matched commands as a parameter
                val command = commands.requireSingleCommand<Commands.Exit>()
                val unmatchedIds = HashSet<UniqueIdentifier>(command.value.changed)
                val owningPubKeys = HashSet<PublicKey>()
                val expectedOutputs = inputs.filter { input ->
                    if (unmatchedIds.contains(input.linearId)) {
                        requireThat {
                            "there are no notices on receivables to be removed from the ledger" by input.notices.isEmpty()
                        }
                        unmatchedIds.remove(input.linearId)
                        owningPubKeys.add(input.owner)
                        false
                    } else {
                        true
                    }
                }
                val keysThatSigned = command.signers
                requireThat {
                    "inputs are not empty" by inputs.isNotEmpty()
                    "outputs match inputs with expected changes applied" by outputs.equals(expectedOutputs)
                    "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
                }
                return setOf(command.value as Commands)
            }
        }

        // TODO: Amend clause, which replaces the Move clause

        /**
         * Default clause, which checks the inputs and outputs match. Normally this wouldn't be expected to trigger,
         * as other commands would handle the transaction, but this exists in case the states need to be witnessed by
         * other contracts within the transaction but not modified.
         */
        class InputsAndOutputsMatch : Clause<State, Commands, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Unit?): Set<Commands> {
                require(groupingKey == null)
                require(inputs.equals(outputs)) { "Inputs and outputs must match unless commands indicate otherwise" }
                return emptySet()
            }
        }
    }

    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.gov/receivables.html")
    fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<Commands>>
            = commands.select<Commands>()
    override fun verify(tx: TransactionForContract)
        = verifyClause(tx, FilterOn<State, Commands, Unit>(
            AllComposition(
                Clauses.StatesAreOrderedAndUnique(), // TODO: This is varient of the LinearState.ClauseVerifier, and we should move it up there
                FirstComposition(
                    Clauses.Issue(),
                    Clauses.Exit(),
                    Clauses.Note(),
                    Clauses.Move(),
                    Clauses.InputsAndOutputsMatch()
                )
            ), { states -> states.filterIsInstance<State>() }),
            extractCommands(tx.commands))
}
