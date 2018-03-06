/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.generateAmount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.flows.CashExitFlow.ExitRequest
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.finance.flows.CashPaymentFlow.PaymentRequest
import java.util.*

fun generateIssue(
        max: Long,
        currency: Currency,
        notary: Party,
        possibleRecipients: List<Party>
): Generator<IssueAndPaymentRequest> {
    return generateAmount(1, max, Generator.pure(currency)).combine(
            Generator.pure(OpaqueBytes.of(0)),
            Generator.pickOne(possibleRecipients)
    ) { amount, ref, recipient ->
        IssueAndPaymentRequest(amount, ref, recipient, notary, true)
    }
}

fun generateMove(
        max: Long,
        currency: Currency,
        issuer: Party,
        possibleRecipients: List<Party>
): Generator<PaymentRequest> {
    return generateAmount(1, max, Generator.pure(Issued(PartyAndReference(issuer, OpaqueBytes.of(0)), currency))).combine(
            Generator.pickOne(possibleRecipients)
    ) { amount, recipient ->
        PaymentRequest(amount.withoutIssuer(), recipient, true, setOf(issuer))
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