/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.api

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.parsePublicKeyBase58
import net.corda.core.utilities.toBase58String
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.DealState
import net.corda.vega.analytics.InitialMarginTriple
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.PortfolioState
import net.corda.vega.flows.IRSTradeFlow
import net.corda.vega.flows.SimmFlow
import net.corda.vega.flows.SimmRevaluation
import net.corda.vega.portfolio.Portfolio
import net.corda.vega.portfolio.toPortfolio
import net.corda.vega.portfolio.toStateAndRef
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

//TODO: Change import namespaces vega -> ....

@Path("simmvaluationdemo")
class PortfolioApi(val rpc: CordaRPCOps) {
    private val ownParty: Party get() = rpc.nodeInfo().legalIdentitiesAndCerts.first().party
    private val portfolioUtils = PortfolioApiUtils(ownParty)

    private inline fun <reified T : DealState> dealsWith(party: AbstractParty): List<StateAndRef<T>> {
        val linearStates = rpc.vaultQueryBy<T>().states
        // TODO: enhancement to Vault Query to check for any participant in participants attribute
        // QueryCriteria.LinearStateQueryCriteria(participants = anyOf(party))
        return linearStates.filter { it.state.data.participants.any { it == party } }
    }

    /**
     * DSL to get a party and then executing the passed function with the party as a parameter.
     * Used as such: withParty(name) { doSomethingWith(it) }
     */
    private fun withParty(partyName: String, func: (Party) -> Response): Response {
        val otherParty = rpc.partyFromKey(parsePublicKeyBase58(partyName))
        return if (otherParty != null) {
            func(otherParty)
        } else {
            Response.status(Response.Status.NOT_FOUND).entity("Unknown party $partyName").build()
        }
    }

    /**
     * DSL to get a portfolio and then executing the passed function with the portfolio as a parameter.
     * Used as such: withPortfolio(party) { doSomethingWith(it) }
     */
    private fun withPortfolio(party: Party, func: (PortfolioState) -> Response): Response {
        val portfolio = getPortfolioWith(party)
        return if (portfolio != null) {
            func(portfolio)
        } else {
            Response.status(Response.Status.NOT_FOUND).entity("Portfolio not yet agreed").build()
        }
    }

    /**
     * Gets all existing IRSStates with the party provided.
     */
    private fun getTradesWith(party: Party) = dealsWith<IRSState>(party)

    /**
     * Gets the most recent portfolio state, or null if not extant, with the party provided.
     */
    private fun getPortfolioWith(party: Party): PortfolioState? {
        val portfolios = dealsWith<PortfolioState>(party)
        // Can have at most one between any two parties with the current no split portfolio model
        require(portfolios.size < 2) { "This API currently only supports one portfolio with a counterparty" }
        return portfolios.firstOrNull()?.state?.data
    }

    /**
     * Gets the most recent portfolio state and ref with the named party.
     *
     * @warning Do not call if you have not agreed a portfolio with the other party.
     */
    private fun getPortfolioStateAndRefWith(party: Party): StateAndRef<PortfolioState> {
        val portfolios = dealsWith<PortfolioState>(party)
        // Can have at most one between any two parties with the current no split portfolio model
        require(portfolios.size < 2) { "This API currently only supports one portfolio with a counterparty" }
        return portfolios.first()
    }

    /**
     * Get the current business date.
     *
     * TODO: Move into core API
     */
    @GET
    @Path("business-date")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBusinessDate(): Any {
        return mapOf(
                "business-date" to LocalDateTime.ofInstant(rpc.currentNodeTime(), ZoneId.systemDefault()).toLocalDate()
        )
    }

    /**
     * Get a list of all current trades in the portfolio. This will not represent an agreed portfolio which is a
     * snapshot of trades at the time of agreement.
     */
    @GET
    @Path("{party}/trades")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPartyTrades(@PathParam("party") partyName: String): Response {
        return withParty(partyName) {
            val states = dealsWith<IRSState>(it)
            val latestPortfolioStateRef: StateAndRef<PortfolioState>
            var latestPortfolioStateData: PortfolioState? = null
            var PVs: Map<String, MultiCurrencyAmount>? = null
            var IMs: Map<String, InitialMarginTriple>? = null
            if (dealsWith<PortfolioState>(it).isNotEmpty()) {
                latestPortfolioStateRef = dealsWith<PortfolioState>(it).last()
                latestPortfolioStateData = latestPortfolioStateRef.state.data
                PVs = latestPortfolioStateData.valuation?.presentValues
                IMs = latestPortfolioStateData.valuation?.imContributionMap
            }

            val swaps = states.map { it.state.data.swap }
            Response.ok().entity(swaps.map {
                it.toView(ownParty,
                        latestPortfolioStateData?.portfolio?.toStateAndRef<IRSState>(rpc)?.toPortfolio(),
                        PVs?.get(it.id.second) ?: MultiCurrencyAmount.empty(),
                        IMs?.get(it.id.second) ?: InitialMarginTriple.zero()
                )
            }).build()
        }
    }

    /**
     * Get the most recent version of a trade with the named counterparty.
     */
    @GET
    @Path("{party}/trades/{tradeId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPartyTrade(@PathParam("party") partyName: String, @PathParam("tradeId") tradeId: String): Response {
        return withParty(partyName) {
            val states = dealsWith<IRSState>(it)
            val tradeState = states.first { it.state.data.swap.id.second == tradeId }.state.data
            Response.ok().entity(portfolioUtils.createTradeView(rpc, tradeState)).build()
        }
    }

    /**
     * Add a new trade with the named counterparty. All trades are currently IRSes.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{party}/trades")
    fun addTrade(swap: SwapDataModel, @PathParam("party") partyName: String): Response {
        return withParty(partyName) {
            val buyer = if (swap.buySell.isBuy) ownParty else it
            val seller = if (swap.buySell.isSell) ownParty else it
            rpc.startFlow(IRSTradeFlow::Requester, swap.toData(buyer, seller), it).returnValue.getOrThrow()
            Response.accepted().entity("{}").build()
        }
    }

    /**
     * Provides the most recent valuation for a portfolio with the named counterparty.
     *
     * Note: Requires portfolio and valuations to be agreed.
     */
    @GET
    @Path("{party}/portfolio/valuations")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPartyPortfolioValuations(@PathParam("party") partyName: String): Response {
        return withParty(partyName) { otherParty ->
            withPortfolio(otherParty) { portfolioState ->
                val portfolio = portfolioState.portfolio.toStateAndRef<IRSState>(rpc).toPortfolio()
                Response.ok().entity(portfolioUtils.createValuations(portfolioState, portfolio)).build()
            }
        }
    }

    /**
     * Provides a summary of the portfolio.
     *
     * Note: Can be used before portfolio is agreed.
     */
    @GET
    @Path("{party}/portfolio/summary")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPortfolioSummary(@PathParam("party") partyName: String): Response {
        return withParty(partyName) { party ->
            val trades = getTradesWith(party)
            val portfolio = Portfolio(trades)
            val summary = mapOf(
                    "trades" to portfolio.trades.size,
                    "notional" to portfolio.getNotionalForParty(ownParty).toDouble()
            )
            Response.ok().entity(summary).build()
        }
    }

    /**
     * Used for serialising to JSON in the aggregated history endpoint.
     */
    data class AggregatedHistoryView(val activeTrades: Int, val notional: Double, val date: LocalDate, val IM: Double, val MTM: Double)

    /**
     * Provides a history of the portfolio
     *
     * Note: Requires portfolio to be agreed.
     * Note: Currently only outputs one datapoint (TODO: Output history).
     */
    @GET
    @Path("{party}/portfolio/history/aggregated")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPartyPortfolioHistoryAggregated(@PathParam("party") partyName: String): Response {
        return withParty(partyName) { party ->
            withPortfolio(party) { state ->
                if (state.valuation != null) {
                    val isValuer = state.valuer == ownParty
                    val rawMtm = state.valuation.presentValues.map {
                        it.value.amounts.first().amount
                    }.reduce { a, b -> a + b }
                    val notional = if (isValuer) state.valuation.notional else -state.valuation.notional
                    val mtm = if (isValuer) rawMtm else -rawMtm
                    // TODO: Stop using localdate.now
                    val history = AggregatedHistoryView(state.valuation.trades, notional.toDouble(), LocalDate.now(), state.valuation.margin.first, mtm)
                    Response.ok().entity(history).build()
                } else {
                    Response.status(Response.Status.NOT_FOUND).entity("Portfolio not yet valued").build()
                }
            }
        }
    }

    data class ApiParty(val id: String, val text: CordaX500Name)
    data class AvailableParties(val self: ApiParty, val counterparties: List<ApiParty>)

    /**
     * Returns the identity of the current node as well as a list of other counterparties that it is aware of.
     */
    @GET
    @Path("whoami")
    @Produces(MediaType.APPLICATION_JSON)
    fun getWhoAmI(): AvailableParties {
        val parties = rpc.networkMapSnapshot()
        val notaries = rpc.notaryIdentities()
        // TODO We are not able to filter by network map node now
        val counterParties = parties.filterNot {
            it.legalIdentities.any { it in notaries }
                    || ownParty in it.legalIdentities
        }
        return AvailableParties(
                self = ApiParty(ownParty.owningKey.toBase58String(), ownParty.name),
                // TODO It will show all identities including service identities.
                counterparties = counterParties.flatMap { it.legalIdentitiesAndCerts.map { ApiParty(it.owningKey.toBase58String(), it.name) } }
        )
    }

    data class ValuationCreationParams(val valuationDate: LocalDate)

    /**
     * Runs portfolio and valuation agreement or update over the portfolio with the named party.
     */

    @POST
    @Path("{party}/portfolio/valuations/calculate")
    @Produces(MediaType.APPLICATION_JSON)
    fun startPortfolioCalculations(params: ValuationCreationParams = ValuationCreationParams(LocalDate.of(2016, 6, 6)), @PathParam("party") partyName: String): Response {
        return withParty(partyName) { otherParty ->
            val existingSwap = getPortfolioWith(otherParty)
            val flowHandle = if (existingSwap == null) {
                rpc.startFlow(SimmFlow::Requester, otherParty, params.valuationDate)
            } else {
                rpc.startFlow(SimmRevaluation::Initiator, getPortfolioStateAndRefWith(otherParty).ref, params.valuationDate)
            }
            flowHandle.returnValue.getOrThrow()

            withPortfolio(otherParty) { portfolioState ->
                val portfolio = portfolioState.portfolio.toStateAndRef<IRSState>(rpc).toPortfolio()
                Response.ok().entity(portfolioUtils.createValuations(portfolioState, portfolio)).build()
            }
        }
    }
}
