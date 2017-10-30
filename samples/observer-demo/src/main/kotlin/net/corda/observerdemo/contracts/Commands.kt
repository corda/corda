package net.corda.observerdemo.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.MoveCommand
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.NonEmptySet
import net.corda.node.utilities.AddOrRemove

interface Commands : CommandData {
    /**
     * The IDs of changed [Notice] or [Receivable] states.
     */
    val changed: Iterable<UniqueIdentifier>

    data class Issue(override val changed: NonEmptySet<UniqueIdentifier>) : Commands

    data class Move(override val contract: Class<out Contract>?, val changes: List<Pair<UniqueIdentifier, AbstractParty>>) : MoveCommand, Commands {
        override val changed: Iterable<UniqueIdentifier> = changes.map { it.first }
    }

    // TODO: It needs to be a lot clearer which way around these identifiers go
    data class Assign(val noticeReceivableChanges: List<AssignChange>) : Commands {
        override val changed: Iterable<UniqueIdentifier> = noticeReceivableChanges.map { it.noticeId } + noticeReceivableChanges.map { it.receivableId }
    }

    // TODO: While notices and receivables should never share IDs, we should have separation anyway just to be sure
    data class Exit(override val changed: NonEmptySet<UniqueIdentifier>) : Commands
}

data class AssignChange(val noticeId: UniqueIdentifier, val receivableId: UniqueIdentifier, val addOrRemove: AddOrRemove)