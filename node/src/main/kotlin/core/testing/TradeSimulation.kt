package core.testing

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import contracts.CommercialPaper
import core.contracts.DOLLARS
import core.contracts.SignedTransaction
import core.days
import core.node.subsystems.NodeWalletService
import core.random63BitValue
import core.seconds
import protocols.TwoPartyTradeProtocol
import java.time.Instant

/**
 * Simulates a never ending series of trades that go pair-wise through the banks (e.g. A and B trade with each other,
 * then B and C trade with each other, then C and A etc).
 */
class TradeSimulation(runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(runAsync, latencyInjector) {
    override fun startMainSimulation(): ListenableFuture<Unit> {
        startTradingCircle { i, j -> tradeBetween(i, j) }
        return Futures.immediateFailedFuture(UnsupportedOperationException("This future never completes"))
    }

    private fun tradeBetween(buyerBankIndex: Int, sellerBankIndex: Int): ListenableFuture<MutableList<SignedTransaction>> {
        val buyer = banks[buyerBankIndex]
        val seller = banks[sellerBankIndex]

        (buyer.services.walletService as NodeWalletService).fillWithSomeTestCash(notary.info.identity, 1500.DOLLARS)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(seller.info.identity.ref(1, 2, 3), 1100.DOLLARS, Instant.now() + 10.days, notary.info.identity)
            tx.setTime(Instant.now(), notary.info.identity, 30.seconds)
            tx.signWith(notary.storage.myLegalIdentityKey)
            tx.signWith(seller.storage.myLegalIdentityKey)
            tx.toSignedTransaction(true)
        }
        seller.services.storageService.validatedTransactions[issuance.id] = issuance

        val sessionID = random63BitValue()
        val buyerProtocol = TwoPartyTradeProtocol.Buyer(seller.net.myAddress, notary.info.identity,
                1000.DOLLARS, CommercialPaper.State::class.java, sessionID)
        val sellerProtocol = TwoPartyTradeProtocol.Seller(buyer.net.myAddress, notary.info,
                issuance.tx.outRef(0), 1000.DOLLARS, seller.storage.myLegalIdentityKey, sessionID)

        linkConsensus(listOf(buyer, seller, notary), sellerProtocol)
        linkProtocolProgress(buyer, buyerProtocol)
        linkProtocolProgress(seller, sellerProtocol)

        val buyerFuture = buyer.smm.add("bank.$buyerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.buyer", buyerProtocol)
        val sellerFuture = seller.smm.add("bank.$sellerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.seller", sellerProtocol)

        return Futures.successfulAsList(buyerFuture, sellerFuture)
    }
}