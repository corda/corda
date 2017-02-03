package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.generateAmount
import net.corda.client.mock.pickOne
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashFlowCommand
import java.util.*

fun generateIssue(
        max: Long,
        currency: Currency,
        notary: Party,
        possibleRecipients: List<Party>
): Generator<CashFlowCommand.IssueCash> {
    return generateAmount(0, max, Generator.pure(currency)).combine(
            Generator.pure(OpaqueBytes.of(0)),
            Generator.pickOne(possibleRecipients)
    ) { amount, ref, recipient ->
        CashFlowCommand.IssueCash(amount, ref, recipient, notary)
    }
}

fun generateMove(
        max: Long,
        currency: Currency,
        issuer: AnonymousParty,
        possibleRecipients: List<Party>
): Generator<CashFlowCommand.PayCash> {
    return generateAmount(1, max, Generator.pure(Issued(PartyAndReference(issuer, OpaqueBytes.of(0)), currency))).combine(
            Generator.pickOne(possibleRecipients)
    ) { amount, recipient ->
        CashFlowCommand.PayCash(amount, recipient)
    }
}

fun generateExit(
        max: Long,
        currency: Currency
): Generator<CashFlowCommand.ExitCash> {
    return generateAmount(1, max, Generator.pure(currency)).map { amount ->
        CashFlowCommand.ExitCash(amount, OpaqueBytes.of(0))
    }
}
