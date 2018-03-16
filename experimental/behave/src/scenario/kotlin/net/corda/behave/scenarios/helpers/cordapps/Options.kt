//package net.corda.behave.scenarios.helpers.cordapps
//
//import net.corda.behave.scenarios.ScenarioState
//import net.corda.behave.scenarios.helpers.Substeps
//import net.corda.core.contracts.Amount
//import net.corda.core.contracts.UniqueIdentifier
//import net.corda.core.messaging.startFlow
//import net.corda.core.transactions.SignedTransaction
//import net.corda.core.utilities.getOrThrow
//import net.corda.finance.contracts.asset.Cash
//import net.corda.option.base.OptionType
//import net.corda.option.base.state.OptionState
//import net.corda.option.client.flow.OptionIssueFlow
//import net.corda.option.client.flow.OptionTradeFlow
//import net.corda.option.client.flow.SelfIssueCashFlow
//import java.time.LocalDate
//import java.time.ZoneOffset
//import java.util.*
//
//class Options(state: ScenarioState) : Substeps(state) {
//
//    fun selfIssueCash(node: String, amount: Long, currency: String): Cash.State {
//        return withClientProxy(node) {
//            val issueAmount = Amount(amount * 100, Currency.getInstance(currency))
//            return@withClientProxy it.startFlow(::SelfIssueCashFlow, issueAmount).returnValue.getOrThrow()
//        }
//    }
//
//    fun issue(node: String, optionType: String, strike: Int, currency: String, expiry: String, underlying: String, issuerName: String) : SignedTransaction {
//        return withClientProxy(node) {
//            val ownParty = it.partiesFromName(node, false).first()
//            val issuerParty = it.partiesFromName(issuerName, false).firstOrNull() ?: throw IllegalArgumentException("Unknown issuer $issuerName.")
//            val expiryDate = LocalDate.parse(expiry).atStartOfDay().toInstant(ZoneOffset.UTC)
//            val type = if (optionType == "CALL") OptionType.CALL else OptionType.PUT
//            val strikePrice = Amount(strike.toLong() * 100, Currency.getInstance(currency))
//            val optionToIssue = OptionState(
//                    strikePrice = strikePrice,
//                    expiryDate = expiryDate,
//                    underlyingStock = underlying,
//                    issuer = issuerParty,
//                    owner = ownParty,
//                    optionType = type)
//            return@withClientProxy it.startFlow(OptionIssueFlow::Initiator, optionToIssue).returnValue.getOrThrow()
//        }
//    }
//
//    fun trade(ownNode: String, tradeId: String, counterpartyNode: String) : SignedTransaction {
//        return withClientProxy(ownNode) {
//            val linearId = UniqueIdentifier.fromString(tradeId)
//            val counterparty = it.partiesFromName(counterpartyNode, false).firstOrNull() ?: throw IllegalArgumentException("Unknown counterparty: $counterpartyNode")
//            return@withClientProxy it.startFlow(OptionTradeFlow::Initiator, linearId, counterparty).returnValue.getOrThrow()
//        }
//    }
//}