package com.r3corda.simulation

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.contracts.CommercialPaper
import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER
import com.r3corda.contracts.testing.fillWithSomeTestCash
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.OwnableState
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.days
import com.r3corda.core.flatMap
import com.r3corda.core.node.recordTransactions
import com.r3corda.core.seconds
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.protocols.TwoPartyTradeProtocol.Buyer
import com.r3corda.protocols.TwoPartyTradeProtocol.Seller
import com.r3corda.testing.initiateSingleShotProtocol
import com.r3corda.testing.node.InMemoryMessagingNetwork
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

        val buyerFuture = buyer.initiateSingleShotProtocol(Seller::class) {
            Buyer(it, notary.info.notaryIdentity, amount, CommercialPaper.State::class.java)
        }.flatMap { it.resultFuture }

        val sellerKey = seller.services.legalIdentityKey
        val sellerProtocol = Seller(
                buyer.info.legalIdentity,
                notary.info,
                issuance.tx.outRef<OwnableState>(0),
                amount,
                sellerKey)

        showConsensusFor(listOf(buyer, seller, notary))
        showProgressFor(listOf(buyer, seller))

        val sellerFuture = seller.services.startProtocol("bank.$sellerBankIndex.seller", sellerProtocol)

        return Futures.successfulAsList(buyerFuture, sellerFuture)
    }

}
