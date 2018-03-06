/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.traderdemo

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.Emoji
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.testing.internal.vault.VaultFiller.Companion.calculateRandomlySizedAmounts
import net.corda.traderdemo.flow.CommercialPaperIssueFlow
import net.corda.traderdemo.flow.SellerFlow
import java.util.*

/**
 * Interface for communicating with nodes running the trader demo.
 */
class TraderDemoClientApi(val rpc: CordaRPCOps) {
    val cashCount: Long
        get() {
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count)
            return rpc.vaultQueryBy<Cash.State>(countCriteria).otherResults.single() as Long
        }

    val dollarCashBalance: Amount<Currency> get() = rpc.getCashBalance(USD)

    val commercialPaperCount: Long
        get() {
            val count = builder { VaultSchemaV1.VaultStates::recordedTime.count() }
            val countCriteria = QueryCriteria.VaultCustomQueryCriteria(count)
            return rpc.vaultQueryBy<CommercialPaper.State>(countCriteria).otherResults.single() as Long
        }

    fun runIssuer(amount: Amount<Currency>, buyerName: CordaX500Name, sellerName: CordaX500Name) {
        val ref = OpaqueBytes.of(1)
        val buyer = rpc.wellKnownPartyFromX500Name(buyerName) ?: throw IllegalStateException("Don't know $buyerName")
        val seller = rpc.wellKnownPartyFromX500Name(sellerName) ?: throw IllegalStateException("Don't know $sellerName")
        val notaryIdentity = rpc.notaryIdentities().first()

        val amounts = calculateRandomlySizedAmounts(amount, 3, 10, Random())
        rpc.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(1), notaryIdentity).returnValue.getOrThrow()
        // Pay random amounts of currency up to the requested amount
        amounts.forEach { pennies ->
            // TODO This can't be done in parallel, perhaps due to soft-locking issues?
            rpc.startFlow(::CashPaymentFlow, amount.copy(quantity = pennies), buyer).returnValue.getOrThrow()
        }
        println("Cash issued to buyer")

        // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
        // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
        //
        // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                check(SellerFlow.PROSPECTUS_HASH == id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        rpc.startFlow(::CommercialPaperIssueFlow, amount, ref, seller, notaryIdentity).returnValue.getOrThrow()
        println("Commercial paper issued to seller")
    }

    fun runSeller(amount: Amount<Currency> = 1000.0.DOLLARS, buyerName: CordaX500Name) {
        val otherParty = rpc.wellKnownPartyFromX500Name(buyerName) ?: throw IllegalStateException("Don't know $buyerName")
        // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
        //
        // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
        // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
        //
        // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                check(SellerFlow.PROSPECTUS_HASH == id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        val stx = rpc.startFlow(::SellerFlow, otherParty, amount).returnValue.getOrThrow()
        println("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(stx.tx)}")
    }
}
