package net.corda.vega.flows

import co.paralleluniverse.fibers.Suspendable
import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.data.MarketDataFxRateProvider
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.pricer.curve.CalibrationMeasures
import com.opengamma.strata.pricer.curve.CurveCalibrator
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.Ack
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.dealsWith
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.TwoPartyDealFlow
import net.corda.vega.analytics.*
import net.corda.vega.contracts.*
import net.corda.vega.portfolio.Portfolio
import net.corda.vega.portfolio.toPortfolio
import net.corda.vega.portfolio.toStateAndRef
import java.time.LocalDate

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
    data class OfferMessage(val notary: Party,
                            val dealBeingOffered: PortfolioState,
                            val stateRef: StateRef?,
                            val valuationDate: LocalDate)

    /**
     * Initiates with the other party by sending a portfolio to agree on and then comes to consensus over initial
     * margin using SIMM. If there is an existing state it will update and revalue the portfolio agreement.
     */
    class Requester(val otherParty: Party,
                    val valuationDate: LocalDate,
                    val existing: StateAndRef<PortfolioState>?)
    : FlowLogic<RevisionedState<PortfolioState.Update>>() {
        constructor(otherParty: Party, valuationDate: LocalDate) : this(otherParty, valuationDate, null)

        lateinit var myIdentity: Party
        lateinit var notary: Party

        @Suspendable
        override fun call(): RevisionedState<PortfolioState.Update> {
            logger.debug("Calling from: ${serviceHub.myInfo.legalIdentity.name}. Sending to: ${otherParty.name}")
            require(serviceHub.networkMapCache.notaryNodes.isNotEmpty()) { "No notary nodes registered" }
            notary = serviceHub.networkMapCache.notaryNodes.first().notaryIdentity
            myIdentity = serviceHub.myInfo.legalIdentity

            val trades = serviceHub.vaultService.dealsWith<IRSState>(otherParty)
            val portfolio = Portfolio(trades, valuationDate)
            if (existing == null) {
                agreePortfolio(portfolio)
            } else {
                updatePortfolio(portfolio, existing)
            }
            val portfolioStateRef = serviceHub.vaultService.dealsWith<PortfolioState>(otherParty).first()
            val state = updateValuation(portfolioStateRef)
            logger.info("SimmFlow done")
            return state
        }

        @Suspendable
        private fun agreePortfolio(portfolio: Portfolio) {
            logger.info("Agreeing portfolio")
            val parties = Pair(myIdentity, otherParty)
            val portfolioState = PortfolioState(portfolio.refs, PortfolioSwap(), parties, valuationDate)

            send(otherParty, OfferMessage(notary, portfolioState, existing?.ref, valuationDate))
            logger.info("Awaiting two party deal acceptor")
            subFlow(TwoPartyDealFlow.Acceptor(otherParty), shareParentSessions = true)
        }

        @Suspendable
        private fun updatePortfolio(portfolio: Portfolio, stateAndRef: StateAndRef<PortfolioState>) {
            // Receive is a hack to ensure other side is ready
            sendAndReceive<Ack>(otherParty, OfferMessage(notary, stateAndRef.state.data, existing?.ref, valuationDate))
            logger.info("Updating portfolio")
            val update = PortfolioState.Update(portfolio = portfolio.refs)
            subFlow(StateRevisionFlow.Requester(stateAndRef, update), shareParentSessions = true)
        }

        @Suspendable
        private fun updateValuation(stateRef: StateAndRef<PortfolioState>): RevisionedState<PortfolioState.Update> {
            logger.info("Agreeing valuations")
            val state = stateRef.state.data
            val portfolio = state.portfolio.toStateAndRef<IRSState>(serviceHub).toPortfolio()
            val valuer = state.valuer
            val valuation = agreeValuation(portfolio, valuationDate, valuer)
            val update = PortfolioState.Update(valuation = valuation)
            return subFlow(StateRevisionFlow.Requester(stateRef, update), shareParentSessions = true).state.data
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
            val calibrator = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)

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

            require(agree<CordaMarketData>(cordaMarketData))
            require(agree<CurrencyParameterSensitivities>(sensitivities.first.toCordaCompatible()))
            require(agree<MultiCurrencyAmount>(sensitivities.second.toCordaCompatible()))
            require(agree<InitialMarginTriple>(cordaMargin))

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
            val valid = receive<T>(otherParty).unwrap {
                logger.trace("Comparing --> $it")
                logger.trace("with -------> $data")
                if (it is InitialMarginTriple && data is InitialMarginTriple) {
                    compareIMTriples(it, data)
                } else {
                    it == data
                }
            }
            logger.trace("valid is $valid")
            send(otherParty, valid)
            return valid
        }
    }

    /**
     * Service plugin for listening for incoming Simm flow communication
     */
    class Service(services: PluginServiceHub) {
        init {
            services.registerFlowInitiator(Requester::class, ::Receiver)
        }
    }

    /**
     * Receives and validates a portfolio and comes to consensus over the portfolio initial margin using SIMM.
     */
    class Receiver(val replyToParty: Party) : FlowLogic<Unit>() {
        lateinit var ownParty: Party
        lateinit var offer: OfferMessage

        @Suspendable
        override fun call() {
            ownParty = serviceHub.myInfo.legalIdentity
            val trades = serviceHub.vaultService.dealsWith<IRSState>(replyToParty)
            val portfolio = Portfolio(trades)
            logger.info("SimmFlow receiver started")
            offer = receive<OfferMessage>(replyToParty).unwrap { it }
            if (offer.stateRef == null) {
                agreePortfolio(portfolio)
            } else {
                updatePortfolio(portfolio)
            }
            val portfolioStateRef = serviceHub.vaultService.dealsWith<PortfolioState>(replyToParty).first()
            updateValuation(portfolioStateRef)
        }

        @Suspendable
        private fun agree(data: Any): Boolean {
            send(replyToParty, data)
            return receive<Boolean>(replyToParty).unwrap { it == true }
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
            val calibrator = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)

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
            require(offer.dealBeingOffered.portfolio == portfolio.refs)

            val seller = TwoPartyDealFlow.Instigator(
                    replyToParty,
                    TwoPartyDealFlow.AutoOffer(offer.notary, offer.dealBeingOffered),
                    serviceHub.legalIdentityKey)
            logger.info("Starting two party deal initiator with: ${replyToParty.name}")
            return subFlow(seller, shareParentSessions = true)
        }

        @Suspendable
        private fun updatePortfolio(portfolio: Portfolio) {
            logger.info("Handshake finished, awaiting Simm update")
            send(replyToParty, Ack) // Hack to state that this party is ready
            subFlow(StateRevisionFlow.Receiver<PortfolioState.Update>(replyToParty, {
                it.portfolio == portfolio.refs
            }), shareParentSessions = true)
        }

        @Suspendable
        private fun updateValuation(stateRef: StateAndRef<PortfolioState>) {
            val portfolio = stateRef.state.data.portfolio.toStateAndRef<IRSState>(serviceHub).toPortfolio()
            val valuer = stateRef.state.data.valuer
            val valuation = agreeValuation(portfolio, offer.valuationDate, valuer)

            subFlow(StateRevisionFlow.Receiver<PortfolioState.Update>(replyToParty) {
                it.valuation == valuation
            }, shareParentSessions = true)
        }

    }
}
