package net.corda.vega.api

import com.opengamma.strata.product.swap.FixedRateCalculation
import com.opengamma.strata.product.swap.IborRateCalculation
import com.opengamma.strata.product.swap.RateCalculationSwapLeg
import com.opengamma.strata.product.swap.SwapLegType
import net.corda.core.contracts.hash
import net.corda.core.crypto.Party
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.PortfolioState
import net.corda.vega.portfolio.Portfolio
import java.time.LocalDate

/**
 * API JSON generation functions for larger JSON outputs.
 */
class PortfolioApiUtils(private val ownParty: Party) {
    fun createValuations(state: PortfolioState, portfolio: Portfolio): Any {
        val valuation = state.valuation!!

        val currency = if (portfolio.trades.isNotEmpty()) {
            portfolio.swaps.first().toView(ownParty).currency
        } else {
            ""
        }

        val tradeCount = portfolio.trades.size
        val marketData = valuation.marketData.values.map { it.key.replace("OG-Ticker~", "") to it.value }.toMap()
        val yieldCurveCurrenciesValues = marketData.filter { !it.key.contains("/") }.map { it -> Triple(it.key.split("-")[0], it.key.split("-", limit = 2)[1], it.value) }
        val grouped = yieldCurveCurrenciesValues.groupBy { it.first }
        val subgroups = grouped.map { it.key to it.value.groupBy { v -> v.second } }.toMap()

        val completeSubgroups = subgroups.mapValues { it.value.mapValues { it.value[0].third.toDouble() }.toSortedMap() }

        val yieldCurves = json {
            obj(
                    "name" to "EUR",
                    "values" to completeSubgroups.get("EUR")!!.filter { !it.key.contains("Fixing") }.map {
                        json {
                            obj(
                                    "tenor" to it.key,
                                    "rate" to it.value
                            )
                        }
                    }
            )
        }
        val fixings = json {
            obj(
                    "name" to "EUR",
                    "values" to completeSubgroups.get("EUR")!!.filter { it.key.contains("Fixing") }.map {
                        json {
                            obj(
                                    "tenor" to it.key,
                                    "rate" to it.value
                            )
                        }
                    }
            )
        }

        val processedSensitivities = valuation.totalSensivities.sensitivities.map { it.marketDataName to it.parameterMetadata.map { it.label }.zip(it.sensitivity.toList()).toMap() }.toMap()

        return json {
            obj(
                    "businessDate" to LocalDate.now(),
                    "portfolio" to obj(
                            "trades" to tradeCount,
                            "baseCurrency" to currency,
                            "IRFX" to tradeCount,
                            "commodity" to 0,
                            "equity" to 0,
                            "credit" to 0,
                            "total" to tradeCount,
                            "agreed" to true
                    ),
                    "marketData" to obj(
                            "yieldCurves" to yieldCurves,
                            "fixings" to fixings,
                            "agreed" to true
                    ),
                    "sensitivities" to obj("curves" to processedSensitivities,
                            "currency" to valuation.currencySensitivies.amounts.toList().map {
                                obj(
                                        "currency" to it.currency.code,
                                        "amount" to it.amount
                                )
                            },
                            "agreed" to true
                    ),
                    "initialMargin" to obj(
                            "baseCurrency" to currency,
                            "post" to obj(
                                    "IRFX" to valuation.margin.first,
                                    "commodity" to 0,
                                    "equity" to 0,
                                    "credit" to 0,
                                    "total" to valuation.margin.first
                            ),
                            "call" to obj(
                                    "IRFX" to valuation.margin.first,
                                    "commodity" to 0,
                                    "equity" to 0,
                                    "credit" to 0,
                                    "total" to valuation.margin.first
                            ),
                            "agreed" to true
                    ),
                    "confirmation" to obj(
                            "hash" to state.hash().toString(),
                            "agreed" to true
                    )
            )
        }
    }

    fun createTradeView(state: IRSState): Any {
        val trade = if (state.buyer.name == ownParty.name) state.swap.toFloatingLeg() else state.swap.toFloatingLeg()
        val fixedLeg = trade.product.legs.first { it.type == SwapLegType.FIXED } as RateCalculationSwapLeg
        val floatingLeg = trade.product.legs.first { it.type != SwapLegType.FIXED } as RateCalculationSwapLeg
        val fixedRate = fixedLeg.calculation as FixedRateCalculation
        val floatingRate = floatingLeg.calculation as IborRateCalculation

        return json {
            obj(
                    "fixedLeg" to obj(
                            "fixedRatePayer" to state.buyer.name,
                            "notional" to obj(
                                    "token" to fixedLeg.currency.code,
                                    "quantity" to fixedLeg.notionalSchedule.amount.initialValue
                            ),
                            "paymentFrequency" to fixedLeg.paymentSchedule.paymentFrequency.toString(),
                            "effectiveDate" to fixedLeg.startDate.unadjusted,
                            "terminationDate" to fixedLeg.endDate.unadjusted,
                            "fixedRate" to obj(
                                    "value" to fixedRate.rate.initialValue
                            ),
                            "paymentRule" to fixedLeg.paymentSchedule.paymentRelativeTo.name,
                            "calendar" to arr("TODO"),
                            "paymentCalendar" to obj() // TODO
                    ),
                    "floatingLeg" to obj(
                            "floatingRatePayer" to state.seller.name,
                            "notional" to obj(
                                    "token" to floatingLeg.currency.code,
                                    "quantity" to floatingLeg.notionalSchedule.amount.initialValue
                            ),
                            "paymentFrequency" to floatingLeg.paymentSchedule.paymentFrequency.toString(),
                            "effectiveDate" to floatingLeg.startDate.unadjusted,
                            "terminationDate" to floatingLeg.endDate.unadjusted,
                            "index" to floatingRate.index.name,
                            "paymentRule" to floatingLeg.paymentSchedule.paymentRelativeTo,
                            "calendar" to arr("TODO"),
                            "paymentCalendar" to arr("TODO"),
                            "fixingCalendar" to obj() // TODO
                    ),
                    "common" to obj(
                            "valuationDate" to trade.product.startDate.unadjusted,
                            "hashLegalDocs" to state.contract.legalContractReference.toString(),
                            "interestRate" to obj(
                                    "name" to "TODO",
                                    "oracle" to "TODO",
                                    "tenor" to obj(
                                            "name" to "TODO"
                                    )
                            )
                    ),
                    "ref" to trade.info.id.get().value
            )
        }
    }
}
