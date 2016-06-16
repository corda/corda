package com.r3corda.node.internal.testing

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.contracts.CommercialPaper
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.days
import com.r3corda.core.random63BitValue
import com.r3corda.core.seconds
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.protocols.TwoPartyTradeProtocol
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

        WalletFiller.fillWithSomeTestCash(buyer.services, notary.info.identity, 1500.DOLLARS)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(1100.DOLLARS `issued by` seller.info.identity.ref(1, 2, 3), Instant.now() + 10.days, notary.info.identity)
            tx.setTime(Instant.now(), notary.info.identity, 30.seconds)
            tx.signWith(notary.storage.myLegalIdentityKey)
            tx.signWith(seller.storage.myLegalIdentityKey)
            tx.toSignedTransaction(true)
        }
        seller.services.storageService.validatedTransactions.addTransaction(issuance)

        val cashIssuerKey = generateKeyPair()
        val amount = 1000.DOLLARS `issued by` Party("Big friendly bank", cashIssuerKey.public).ref(1)
        val sessionID = random63BitValue()
        val buyerProtocol = TwoPartyTradeProtocol.Buyer(seller.net.myAddress, notary.info.identity,
                amount, CommercialPaper.State::class.java, sessionID)
        val sellerProtocol = TwoPartyTradeProtocol.Seller(buyer.net.myAddress, notary.info,
                issuance.tx.outRef(0), amount, seller.storage.myLegalIdentityKey, sessionID)

        linkConsensus(listOf(buyer, seller, notary), sellerProtocol)
        linkProtocolProgress(buyer, buyerProtocol)
        linkProtocolProgress(seller, sellerProtocol)

        val buyerFuture = buyer.smm.add("bank.$buyerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.buyer", buyerProtocol)
        val sellerFuture = seller.smm.add("bank.$sellerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.seller", sellerProtocol)

        return Futures.successfulAsList(buyerFuture, sellerFuture)
    }
}