package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.data.MarketDataFxRateProvider
import com.opengamma.strata.pricer.curve.CalibrationMeasures
import com.opengamma.strata.pricer.curve.CurveCalibrator
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.AbstractStateReplacementFlow.Proposal
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateReplacementException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.finance.flows.TwoPartyDealFlow
import net.corda.vega.analytics.BimmAnalysisUtils
import net.corda.vega.analytics.InitialMarginTriple
import net.corda.vega.analytics.IsdaConfiguration
import net.corda.vega.analytics.OGSIMMAnalyticsEngine
import net.corda.vega.analytics.PortfolioNormalizer
import net.corda.vega.analytics.RwamBimmNotProductClassesCalculator
import net.corda.vega.analytics.compareIMTriples
import net.corda.vega.contracts.IRSState
import net.corda.vega.contracts.PortfolioState
import net.corda.vega.contracts.PortfolioValuation
import net.corda.vega.contracts.RevisionedState
import net.corda.vega.portfolio.Portfolio
import net.corda.vega.portfolio.toPortfolio
import java.time.LocalDate

private val calibrator = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)

/**
 * The Simm Flow is between two parties that both agree on a portfolio of trades to run valuations on. Both sides
 * will independently value the portfolio using a SIMM implementation and then come to consensus over those valuations.
 * It can also update an existing portfolio and revalue it.
 */
object SimmFlow {
    /**
     * Represents a new portfolio offer unless the stateRef field is non-null, at which point it represents a
     * portfolio update offer.
     */
    @CordaSerializable
    data class OfferMessage(val notary: Party,
                            val dealBeingOffered: PortfolioState,
                            val stateRef: StateRef?,
                            val valuationDate: LocalDate)

    /**
     * Initiates with the other party by sending a portfolio to agree on and then comes to consensus over initial
     * margin using SIMM. If there is an existing state it will update and revalue the portfolio agreement.
     */
    @InitiatingFlow
    @StartableByRPC
    class Requester(private val otherParty: Party,
                    private val valuationDate: LocalDate,
                    private val existing: StateAndRef<PortfolioState>?)
        : FlowLogic<RevisionedState<PortfolioState.Update>>() {
        constructor(otherParty: Party, valuationDate: LocalDate) : this(otherParty, valuationDate, null)

        lateinit var notary: Party
        lateinit var otherPartySession: FlowSession

        @Suspendable
        override fun call(): RevisionedState<PortfolioState.Update> {
            logger.debug("Calling from: $ourIdentity. Sending to: $otherParty")
            require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
            notary = serviceHub.networkMapCache.notaryIdentities.first() // TODO We should pass the notary as a parameter to the flow, not leave it to random choice.

            val criteria = LinearStateQueryCriteria(participants = listOf(otherParty))
            val trades = serviceHub.vaultService.queryBy<IRSState>(criteria).states

            val portfolio = Portfolio(trades, valuationDate)
            otherPartySession = initiateFlow(otherParty)
            if (existing == null) {
                agreePortfolio(portfolio)
            } else {
                updatePortfolio(portfolio, existing)
            }
            val portfolioStateRef = serviceHub.vaultService.queryBy<PortfolioState>(criteria).states.first()

            val state = updateValuation(portfolioStateRef)
            logger.info("SimmFlow done")
            return state
        }

        @Suspendable
        private fun agreePortfolio(portfolio: Portfolio) {
            logger.info("Agreeing portfolio")
            val parties = Pair(ourIdentity, otherParty)
            val portfolioState = PortfolioState(portfolio.refs, parties, valuationDate)

            otherPartySession.send(OfferMessage(notary, portfolioState, existing?.ref, valuationDate))
            logger.info("Awaiting two party deal acceptor")
            subFlow(TwoPartyDealFlow.Acceptor(otherPartySession))
        }

        @Suspendable
        private fun updatePortfolio(portfolio: Portfolio, stateAndRef: StateAndRef<PortfolioState>) {
            // Receive is a hack to ensure other side is ready
            otherPartySession.sendAndReceive<Ack>(OfferMessage(notary, stateAndRef.state.data, existing?.ref, valuationDate))
            logger.info("Updating portfolio")
            val update = PortfolioState.Update(portfolio = portfolio.refs)
            subFlow(StateRevisionFlowRequester(otherPartySession, stateAndRef, update))
        }

        private class StateRevisionFlowRequester<T>(val session: FlowSession, stateAndRef: StateAndRef<RevisionedState<T>>, update: T) : StateRevisionFlow.Requester<T>(stateAndRef, update) {
            override fun getParticipantSessions(): List<Pair<FlowSession, List<AbstractParty>>> {
                return listOf(session to listOf(session.counterparty))
            }
        }

        @Suspendable
        private fun updateValuation(stateRef: StateAndRef<PortfolioState>): RevisionedState<PortfolioState.Update> {
            logger.info("Agreeing valuations")
            val state = stateRef.state.data
            val portfolio = serviceHub.vaultService.queryBy<IRSState>(VaultQueryCriteria(stateRefs = state.portfolio)).states.toPortfolio()

            val valuer = serviceHub.identityService.wellKnownPartyFromAnonymous(state.valuer)
            require(valuer != null) { "Valuer party must be known to this node" }
            val valuation = agreeValuation(portfolio, valuationDate, valuer!!)
            val update = PortfolioState.Update(valuation = valuation)
            return subFlow(StateRevisionFlowRequester(otherPartySession, stateRef, update)).state.data
        }

        @Suspendable
        private fun agreeValuation(portfolio: Portfolio, asOf: LocalDate, valuer: Party): PortfolioValuation {
            // TODO: The attachments need to be added somewhere
            // TODO: handle failures
            val analyticsEngine = OGSIMMAnalyticsEngine()

            val referenceData = ReferenceData.standard()
            val curveGroup = analyticsEngine.curveGroup()
            val marketData = analyticsEngine.marketData(asOf)

            val pricer = DiscountingSwapProductPricer.DEFAULT
            val OGTrades = portfolio.swaps.map { it -> it.toFixedLeg().resolve(referenceData) }

            val ratesProvider = calibrator.calibrate(curveGroup, marketData, ReferenceData.standard())
            val fxRateProvider = MarketDataFxRateProvider.of(marketData)
            val combinedRatesProvider = ImmutableRatesProvider.combined(fxRateProvider, ratesProvider)

            val PVs = OGTrades.map { it.info.id.get().value to pricer.presentValue(it.product, combinedRatesProvider).toCordaCompatible() }.toMap()

            val sensitivities = analyticsEngine.sensitivities(OGTrades, pricer, ratesProvider)
            val normalizer = PortfolioNormalizer(Currency.EUR, combinedRatesProvider) // TODO.. Not just EUR
            val calculatorTotal = RwamBimmNotProductClassesCalculator(fxRateProvider, Currency.EUR, IsdaConfiguration.INSTANCE)
            val margin = BimmAnalysisUtils.computeMargin(combinedRatesProvider, normalizer, calculatorTotal, sensitivities.first, sensitivities.second)
            val sensBatch = analyticsEngine.calculateSensitivitiesBatch(OGTrades, pricer, ratesProvider)

            val cordaMarketData = fxRateProvider.marketData.toCordaCompatible()
            val cordaMargin = InitialMarginTriple(margin.first, margin.second, margin.third).toCordaCompatible()
            val imBatch = analyticsEngine.calculateMarginBatch(sensBatch, combinedRatesProvider, fxRateProvider, cordaMargin)
            val cordaIMMap = imBatch.map { it.key.info.id.get().value to it.value.toCordaCompatible() }.toMap()

            require(agree(cordaMarketData))
            require(agree(sensitivities.first.toCordaCompatible()))
            require(agree(sensitivities.second.toCordaCompatible()))
            require(agree(cordaMargin))

            return PortfolioValuation(
                    portfolio.trades.size,
                    portfolio.getNotionalForParty(valuer),
                    cordaMarketData,
                    sensitivities.first.toCordaCompatible(),
                    sensitivities.second.toCordaCompatible(),
                    cordaMargin,
                    cordaIMMap,
                    PVs
            )
        }


        // TODO: In the real world, this would be tolerance aware for different types
        @Suspendable
        private inline fun <reified T : Any> agree(data: T): Boolean {
            val valid = otherPartySession.receive<T>().unwrap {
                logger.trace("Comparing --> $it")
                logger.trace("with -------> $data")
                if (it is InitialMarginTriple && data is InitialMarginTriple) {
                    compareIMTriples(it, data)
                } else {
                    it == data
                }
            }
            logger.trace("valid is $valid")
            otherPartySession.send(valid)
            return valid
        }
    }

    /**
     * Receives and validates a portfolio and comes to consensus over the portfolio initial margin using SIMM.
     */
    @InitiatedBy(Requester::class)
    class Receiver(val replyToSession: FlowSession) : FlowLogic<Unit>() {
        lateinit var offer: OfferMessage

        @Suspendable
        override fun call() {
            val criteria = LinearStateQueryCriteria(participants = listOf(replyToSession.counterparty))
            val trades = serviceHub.vaultService.queryBy<IRSState>(criteria).states
            val portfolio = Portfolio(trades)
            logger.info("SimmFlow receiver started")
            offer = replyToSession.receive<OfferMessage>().unwrap { it }
            if (offer.stateRef == null) {
                agreePortfolio(portfolio)
            } else {
                updatePortfolio(portfolio)
            }
            val portfolioStateRef = serviceHub.vaultService.queryBy<PortfolioState>(criteria).states.first()
            updateValuation(portfolioStateRef)
        }

        @Suspendable
        private fun agree(data: Any): Boolean {
            replyToSession.send(data)
            return replyToSession.receive<Boolean>().unwrap { it }
        }

        /**
         * So this is the crux of the Simm Agreement flow
         * It needs to do several things - which are mainly defined by the analytics engine we are using - which in this
         * case is the Open Gamma version.
         *
         * 1) Agree the portfolio (collection) of trades that are in scope.
         * 2) Now agree (a) reference (static) data and (b) market data
         * 3) Resolve the trades - this is basically creating a trade object such that all required data for it to be priced
         * is included so that any computation on that trade can be repeated and the same result returned
         * 4) Determine the present value (PV) of each trade - this is how much the trade "is worth" in todays terms
         * 5) There is now some curve calibration and portfolio normalization that happens for OG
         * 6) Once we have the above, we can then calculate sensitivities to risk factors
         * ( In essence, this is a value that "if risk factor X changes, how much will that affect the PV of the trade" )
         * 7) Now the sensitivities can be agreed over the flow
         * 8) Finally.. the last phase is the IM calculation. This occurs in several steps
         * 8a) "margin" is the colloquial initial margin for the entire portfolio
         * 8b) "cordaMargin" in an adjusted margin that we have (basically) rounded to 2dp such that it can be compared without issues in floating point accuracy
         * 8c) "imBatch" (aka trade IM contribution) is a map of trades to an initial margin calculated by:
         *       (initial margin for the entire portfolio) - (initial margin for the portfolio *excluding* that particular trade)
         * 9) These are then agreed over corda. The agreement is done at the end to try to separate business and network logic.
         * [ reference data is data such as calendars etc, market data is data such as current market price of ]
         */
        @Suspendable
        private fun agreeValuation(portfolio: Portfolio, asOf: LocalDate, valuer: Party): PortfolioValuation {
            // TODO: The attachments need to be added somewhere
            // TODO: handle failures
            val analyticsEngine = OGSIMMAnalyticsEngine()

            val referenceData = ReferenceData.standard()
            val curveGroup = analyticsEngine.curveGroup()
            val marketData = analyticsEngine.marketData(asOf)

            val pricer = DiscountingSwapProductPricer.DEFAULT
            val OGTrades = portfolio.swaps.map { it -> it.toFixedLeg().resolve(referenceData) }

            val ratesProvider = calibrator.calibrate(curveGroup, marketData, ReferenceData.standard())
            val fxRateProvider = MarketDataFxRateProvider.of(marketData)
            val combinedRatesProvider = ImmutableRatesProvider.combined(fxRateProvider, ratesProvider)

            val PVs = OGTrades.map { it.info.id.get().value to pricer.presentValue(it.product, combinedRatesProvider).toCordaCompatible() }.toMap()

            val sensitivities = analyticsEngine.sensitivities(OGTrades, pricer, ratesProvider)
            val normalizer = PortfolioNormalizer(Currency.EUR, combinedRatesProvider) // TODO.. Not just EUR
            val calculatorTotal = RwamBimmNotProductClassesCalculator(fxRateProvider, Currency.EUR, IsdaConfiguration.INSTANCE)
            val margin = BimmAnalysisUtils.computeMargin(combinedRatesProvider, normalizer, calculatorTotal, sensitivities.first, sensitivities.second)
            val sensBatch = analyticsEngine.calculateSensitivitiesBatch(OGTrades, pricer, ratesProvider)

            val cordaMargin = InitialMarginTriple(margin.first, margin.second, margin.third).toCordaCompatible()
            val imBatch = analyticsEngine.calculateMarginBatch(sensBatch, combinedRatesProvider, fxRateProvider, cordaMargin)
            val cordaMarketData = fxRateProvider.marketData.toCordaCompatible()
            val cordaIMMap = imBatch.map { it.key.info.id.get().value to it.value.toCordaCompatible() }.toMap()

            // Slightly out of order but for readabilities sake, agrees at the bottom
            require(agree(cordaMarketData))
            require(agree(sensitivities.first.toCordaCompatible()))
            require(agree(sensitivities.second.toCordaCompatible()))
            require(agree(cordaMargin))

            return PortfolioValuation(
                    portfolio.trades.size,
                    portfolio.getNotionalForParty(valuer),
                    cordaMarketData,
                    sensitivities.first.toCordaCompatible(),
                    sensitivities.second.toCordaCompatible(),
                    cordaMargin,
                    cordaIMMap,
                    PVs
            )

        }

        @Suspendable
        private fun agreePortfolio(portfolio: Portfolio): SignedTransaction {
            logger.info("Handshake finished, awaiting Simm offer")

            require(offer.dealBeingOffered.portfolio.toSet() == portfolio.refs.toSet())

            val seller = TwoPartyDealFlow.Instigator(
                    replyToSession,
                    TwoPartyDealFlow.AutoOffer(offer.notary, offer.dealBeingOffered))
            logger.info("Starting two party deal initiator with: ${replyToSession.counterparty.name}")
            return subFlow(seller)
        }

        @Suspendable
        private fun updatePortfolio(portfolio: Portfolio) {
            logger.info("Handshake finished, awaiting Simm update")
            replyToSession.send(Ack) // Hack to state that this party is ready.
            subFlow(object : StateRevisionFlow.Receiver<PortfolioState.Update>(replyToSession) {
                override fun verifyProposal(stx: SignedTransaction, proposal: Proposal<PortfolioState.Update>) {
                    super.verifyProposal(stx, proposal)
                    if (proposal.modification.portfolio != portfolio.refs) throw StateReplacementException()
                }
            })
        }

        @Suspendable
        private fun updateValuation(stateRef: StateAndRef<PortfolioState>) {
            val portfolio = serviceHub.vaultService.queryBy<IRSState>(VaultQueryCriteria(stateRefs = stateRef.state.data.portfolio)).states.toPortfolio()
            val valuer = serviceHub.identityService.wellKnownPartyFromAnonymous(stateRef.state.data.valuer) ?: throw IllegalStateException("Unknown valuer party ${stateRef.state.data.valuer}")
            val valuation = agreeValuation(portfolio, offer.valuationDate, valuer)
            subFlow(object : StateRevisionFlow.Receiver<PortfolioState.Update>(replyToSession) {
                override fun verifyProposal(stx: SignedTransaction, proposal: Proposal<PortfolioState.Update>) {
                    super.verifyProposal(stx, proposal)
                    if (proposal.modification.valuation != valuation) throw StateReplacementException()
                }
            })
        }
    }

    @CordaSerializable
    private object Ack
}
