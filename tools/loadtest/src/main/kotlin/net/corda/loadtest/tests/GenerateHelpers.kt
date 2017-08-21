package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.generateAmount
import net.corda.client.mock.pickOne
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.flows.CashExitFlow.ExitRequest
import net.corda.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.flows.CashPaymentFlow.PaymentRequest
import java.util.*

fun generateIssue(
        max: Long,
        currency: Currency,
        notary: Party,
        possibleRecipients: List<Party>,
        anonymous: Boolean
): Generator<IssueAndPaymentRequest> {
    return generateAmount(1, max, Generator.pure(currency)).combine(
            Generator.pure(OpaqueBytes.of(0)),
            Generator.pickOne(possibleRecipients)
    ) { amount, ref, recipient ->
        IssueAndPaymentRequest(amount, ref, recipient, notary, anonymous)
    }
}

fun generateMove(
        max: Long,
        currency: Currency,
        issuer: Party,
        possibleRecipients: List<Party>,
        anonymous: Boolean
): Generator<PaymentRequest> {
    return generateAmount(1, max, Generator.pure(Issued(PartyAndReference(issuer, OpaqueBytes.of(0)), currency))).combine(
            Generator.pickOne(possibleRecipients)
    ) { amount, recipient ->
        PaymentRequest(amount.withoutIssuer(), recipient, anonymous, setOf(issuer))
    }
}

fun generateExit(
        max: Long,
        currency: Currency
): Generator<ExitRequest> {
    return generateAmount(1, max, Generator.pure(currency)).map { amount ->
        ExitRequest(amount, OpaqueBytes.of(0))
    }
}
