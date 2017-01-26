package net.corda.simulation

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.contracts.CommercialPaper
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.`issued by`
import net.corda.core.days
import net.corda.core.flatMap
import net.corda.core.flows.FlowStateMachine
import net.corda.core.node.recordTransactions
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.TwoPartyTradeFlow.Buyer
import net.corda.flows.TwoPartyTradeFlow.Seller
import net.corda.testing.initiateSingleShotFlow
import net.corda.testing.node.InMemoryMessagingNetwork
import java.time.Instant

/**
 * Simulates a never ending series of trades that go pair-wise through the banks (e.g. A and B trade with each other,
 * then B and C trade with each other, then C and A etc).
 */
class TradeSimulation(runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(false, runAsync, latencyInjector) {
    override fun startMainSimulation(): ListenableFuture<Unit> {
        startTradingCircle { i, j -> tradeBetween(i, j) }
        return Futures.immediateFailedFuture(UnsupportedOperationException("This future never completes"))
    }

    private fun tradeBetween(buyerBankIndex: Int, sellerBankIndex: Int): ListenableFuture<MutableList<SignedTransaction>> {
        val buyer = banks[buyerBankIndex]
        val seller = banks[sellerBankIndex]

        buyer.services.fillWithSomeTestCash(1500.DOLLARS, notary.info.notaryIdentity)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(seller.info.legalIdentity.ref(1, 2, 3), 1100.DOLLARS `issued by` DUMMY_CASH_ISSUER,
                    Instant.now() + 10.days, notary.info.notaryIdentity)
            tx.setTime(Instant.now(), 30.seconds)
            val notaryKey = notary.services.notaryIdentityKey
            val sellerKey = seller.services.legalIdentityKey
            tx.signWith(notaryKey)
            tx.signWith(sellerKey)
            tx.toSignedTransaction(true)
        }
        seller.services.recordTransactions(issuance)

        val amount = 1000.DOLLARS

        @Suppress("UNCHECKED_CAST")
        val buyerFuture = buyer.initiateSingleShotFlow(Seller::class) {
            Buyer(it, notary.info.notaryIdentity, amount, CommercialPaper.State::class.java)
        }.flatMap { (it.stateMachine as FlowStateMachine<SignedTransaction>).resultFuture }

        val sellerKey = seller.services.legalIdentityKey
        val sellerFlow = Seller(
                buyer.info.legalIdentity,
                notary.info,
                issuance.tx.outRef<OwnableState>(0),
                amount,
                sellerKey)

        showConsensusFor(listOf(buyer, seller, notary))
        showProgressFor(listOf(buyer, seller))

        val sellerFuture = seller.services.startFlow(sellerFlow).resultFuture

        return Futures.successfulAsList(buyerFuture, sellerFuture)
    }

}
