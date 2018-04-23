/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.irs.web.api

import net.corda.core.contracts.filterStatesOfType
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.irs.contract.InterestRateSwap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.net.URI
import net.corda.irs.flows.AutoOfferFlow

/**
 * This provides a simplified API, currently for demonstration use only.
 *
 * It provides several JSON REST calls as follows:
 *
 * GET /api/irs/deals - returns an array of all deals tracked by the wallet of this node.
 * GET /api/irs/deals/{ref} - return the deal referenced by the externally provided refence that was previously uploaded.
 * POST /api/irs/deals - Payload is a JSON formatted [InterestRateSwap.State] create a new deal (includes an externally provided reference for use above).
 *
 * TODO: where we currently refer to singular external deal reference, of course this could easily be multiple identifiers e.g. CUSIP, ISIN.
 *
 * simulate any associated business processing (currently fixing).
 *
 * TODO: replace simulated date advancement with business event based implementation
 */

@RestController
@RequestMapping("/api/irs")
class InterestRateSwapAPI {
    companion object {
        private val logger = contextLogger()
    }

    private fun generateDealLink(deal: InterestRateSwap.State) = "/api/irs/deals/" + deal.common.tradeID

    private fun getDealByRef(ref: String): InterestRateSwap.State? {
        val vault = rpc.vaultQueryBy<InterestRateSwap.State>().states
        val states = vault.filterStatesOfType<InterestRateSwap.State>().filter { it.state.data.linearId.externalId == ref }
        return if (states.isEmpty()) null else {
            val deals = states.map { it.state.data }
            return if (deals.isEmpty()) null else deals[0]
        }
    }


    @Autowired
    lateinit var rpc: CordaRPCOps

    private fun getAllDeals(): Array<InterestRateSwap.State> {
        val vault = rpc.vaultQueryBy<InterestRateSwap.State>().states
        val states = vault.filterStatesOfType<InterestRateSwap.State>()
        val swaps = states.map { it.state.data }.toTypedArray()
        return swaps
    }

    @GetMapping("/deals")
    fun fetchDeals(): Array<InterestRateSwap.State> = getAllDeals()

    @PostMapping("/deals")
    fun storeDeal(@RequestBody newDeal: InterestRateSwap.State): ResponseEntity<Any?> {
        return try {
            rpc.startFlow(AutoOfferFlow::Requester, newDeal).returnValue.getOrThrow()
            ResponseEntity.created(URI.create(generateDealLink(newDeal))).build()
        } catch (ex: Throwable) {
            logger.info("Exception when creating deal: $ex", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString())
        }
    }

    @GetMapping("/deals/{ref:.+}")
    fun fetchDeal(@PathVariable ref: String?): ResponseEntity<Any?> {
        val deal = getDealByRef(ref!!)
        return if (deal == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(deal)
        }
    }

    @GetMapping("/deals/networksnapshot")
    fun fetchDeal() = rpc.networkMapSnapshot().toString()
}
