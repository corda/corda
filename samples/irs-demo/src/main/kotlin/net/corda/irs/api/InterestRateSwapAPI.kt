package net.corda.irs.api

import net.corda.client.rpc.notUsed
import net.corda.core.contracts.filterStatesOfType
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.loggerFor
import net.corda.irs.contract.InterestRateSwap
import net.corda.irs.flows.AutoOfferFlow
import java.net.URI
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
@Path("irs")
class InterestRateSwapAPI(val rpc: CordaRPCOps) {

    private val logger = loggerFor<InterestRateSwapAPI>()

    private fun generateDealLink(deal: InterestRateSwap.State) = "/api/irs/deals/" + deal.common.tradeID

    private fun getDealByRef(ref: String): InterestRateSwap.State? {
        val vault = rpc.vaultQueryBy<InterestRateSwap.State>().states
        val states = vault.filterStatesOfType<InterestRateSwap.State>().filter { it.state.data.ref == ref }
        return if (states.isEmpty()) null else {
            val deals = states.map { it.state.data }
            return if (deals.isEmpty()) null else deals[0]
        }
    }

    private fun getAllDeals(): Array<InterestRateSwap.State> {
        val vault = rpc.vaultQueryBy<InterestRateSwap.State>().states
        val states = vault.filterStatesOfType<InterestRateSwap.State>()
        val swaps = states.map { it.state.data }.toTypedArray()
        return swaps
    }

    @GET
    @Path("deals")
    @Produces(MediaType.APPLICATION_JSON)
    fun fetchDeals(): Array<InterestRateSwap.State> = getAllDeals()

    @POST
    @Path("deals")
    @Consumes(MediaType.APPLICATION_JSON)
    fun storeDeal(newDeal: InterestRateSwap.State): Response {
        return try {
            rpc.startFlow(AutoOfferFlow::Requester, newDeal).returnValue.getOrThrow()
            Response.created(URI.create(generateDealLink(newDeal))).build()
        } catch (ex: Throwable) {
            logger.info("Exception when creating deal: $ex")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.toString()).build()
        }
    }

    @GET
    @Path("deals/{ref}")
    @Produces(MediaType.APPLICATION_JSON)
    fun fetchDeal(@PathParam("ref") ref: String): Response {
        val deal = getDealByRef(ref)
        if (deal == null) {
            return Response.status(Response.Status.NOT_FOUND).build()
        } else {
            return Response.ok().entity(deal).build()
        }
    }
}
