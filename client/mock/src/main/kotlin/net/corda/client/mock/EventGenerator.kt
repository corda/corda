package net.corda.client.mock

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashFlowCommand
import java.util.*

/**
 * [Generator]s for incoming/outgoing events to/from the [WalletMonitorService]. Internally it keeps track of owned
 * state/ref pairs, but it doesn't necessarily generate "correct" events!
 */

class EventGenerator(val parties: List<Party>, val currencies: List<Currency>, val notary: Party) {
    private val partyGenerator = Generator.pickOne(parties)
    private val issueRefGenerator = Generator.intRange(0, 1).map { number -> OpaqueBytes(ByteArray(1, { number.toByte() })) }
    private val amountGenerator = Generator.longRange(10000, 1000000)
    private val currencyGenerator = Generator.pickOne(currencies)

    private val issueCashGenerator = amountGenerator.combine(partyGenerator, issueRefGenerator, currencyGenerator) { amount, to, issueRef, ccy ->
        CashFlowCommand.IssueCash(Amount(amount, ccy), issueRef, to, notary)
    }

    private val exitCashGenerator = amountGenerator.combine(issueRefGenerator, currencyGenerator) { amount, issueRef, ccy ->
        CashFlowCommand.ExitCash(Amount(amount, ccy), issueRef)
    }

    val moveCashGenerator = amountGenerator.combine(partyGenerator, currencyGenerator) { amountIssued, recipient, currency ->
        CashFlowCommand.PayCash(Amount(amountIssued, currency), recipient)
    }

    val issuerGenerator = Generator.frequency(listOf(
            0.1 to exitCashGenerator,
            0.9 to issueCashGenerator
    ))
}
