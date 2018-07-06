/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.mock

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.USD
import java.util.*
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.finance.flows.CashExitFlow.ExitRequest
import net.corda.finance.flows.CashPaymentFlow.PaymentRequest

/**
 * [Generator]s for incoming/outgoing cash flow events between parties. It doesn't necessarily generate correct events!
 * Especially at the beginning of simulation there might be few insufficient spend errors.
 */

open class EventGenerator(val parties: List<Party>, val currencies: List<Currency>, val notary: Party) {
    protected val partyGenerator = Generator.pickOne(parties)
    protected val issueRefGenerator = Generator.intRange(0, 1).map { number -> OpaqueBytes.of(number.toByte()) }
    protected val amountGenerator = Generator.longRange(10000, 1000000)
    protected val currencyGenerator = Generator.pickOne(currencies)
    protected val currencyMap: MutableMap<Currency, Long> = mutableMapOf(USD to 0L, GBP to 0L) // Used for estimation of how much money we have in general.

    protected fun addToMap(ccy: Currency, amount: Long) {
        currencyMap.computeIfPresent(ccy) { _, value -> Math.max(0L, value + amount) }
    }

    protected val issueCashGenerator = amountGenerator.combine(partyGenerator, issueRefGenerator, currencyGenerator) { amount, to, issueRef, ccy ->
        addToMap(ccy, amount)
        IssueAndPaymentRequest(Amount(amount, ccy), issueRef, to, notary, anonymous = true)
    }

    protected val exitCashGenerator = amountGenerator.combine(issueRefGenerator, currencyGenerator) { amount, issueRef, ccy ->
        addToMap(ccy, -amount)
        ExitRequest(Amount(amount, ccy), issueRef)
    }

    open val moveCashGenerator = amountGenerator.combine(partyGenerator, currencyGenerator) { amountIssued, recipient, currency ->
        PaymentRequest(Amount(amountIssued, currency), recipient, anonymous = true)
    }

    open val issuerGenerator = Generator.frequency(listOf(
            0.1 to exitCashGenerator,
            0.9 to issueCashGenerator
    ))
}

/**
 * [Generator]s for incoming/outgoing events of starting different cash flows. It invokes flows that throw exceptions
 * for use in explorer flow triage. Exceptions are of kind spending/exiting too much cash.
 */
class ErrorFlowsEventGenerator(parties: List<Party>, currencies: List<Currency>, notary: Party) : EventGenerator(parties, currencies, notary) {
    enum class IssuerEvents {
        NORMAL_EXIT,
        EXIT_ERROR
    }

    private val errorGenerator = Generator.pickOne(IssuerEvents.values().toList())

    private val errorExitCashGenerator = amountGenerator.combine(issueRefGenerator, currencyGenerator, errorGenerator) { amount, issueRef, ccy, errorType ->
        when (errorType) {
            IssuerEvents.NORMAL_EXIT -> {
                println("Normal exit")
                if (currencyMap[ccy]!! <= amount) addToMap(ccy, -amount)
                ExitRequest(Amount(amount, ccy), issueRef) // It may fail at the beginning, but we don't care.
            }
            IssuerEvents.EXIT_ERROR -> {
                println("Exit error")
                ExitRequest(Amount(currencyMap[ccy]!! * 2, ccy), issueRef)
            }
        }
    }

    private val normalMoveGenerator = amountGenerator.combine(partyGenerator, currencyGenerator) { amountIssued, recipient, currency ->
        PaymentRequest(Amount(amountIssued, currency), recipient, anonymous = true)
    }

    private val errorMoveGenerator = partyGenerator.combine(currencyGenerator) { recipient, currency ->
        PaymentRequest(Amount(currencyMap[currency]!! * 2, currency), recipient, anonymous = true)
    }

    override val moveCashGenerator = Generator.frequency(listOf(
            0.2 to errorMoveGenerator,
            0.8 to normalMoveGenerator
    ))

    override val issuerGenerator = Generator.frequency(listOf(
            0.3 to errorExitCashGenerator,
            0.7 to issueCashGenerator
    ))
}
