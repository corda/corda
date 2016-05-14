package demo.api

import api.*
import contracts.InterestRateSwap
import core.utilities.loggerFor
import demos.protocols.AutoOfferProtocol
import demos.protocols.ExitServerProtocol
import demos.protocols.UpdateBusinessDayProtocol
import java.net.URI
import java.time.LocalDate
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
 * GET /api/irs/demodate - return the current date as viewed by the system in YYYY-MM-DD format.
 * PUT /api/irs/demodate - put date in format YYYY-MM-DD to advance the current date as viewed by the system and
 * simulate any associated business processing (currently fixing).
 *
 * TODO: replace simulated date advancement with business event based implementation
 *
 * PUT /api/irs/restart - (empty payload) cause the node to restart for API user emergency use in case any servers become unresponsive,
 * or if the demodate or population of deals should be reset (will only work while persistence is disabled).
 */
@Path("irs")
class InterestRateSwapAPI(val api: APIServer) {

    private val logger = loggerFor<InterestRateSwapAPI>()

    private fun generateDealLink(deal: InterestRateSwap.State) = "/api/irs/deals/" + deal.common.tradeID

    private fun getDealByRef(ref: String): InterestRateSwap.State? {
        val states = api.queryStates(StatesQuery.selectDeal(ref))
        return if (states.isEmpty()) null else {
            val deals = api.fetchStates(states).values.map { it as InterestRateSwap.State }.filterNotNull()
            return if (deals.isEmpty()) null else deals[0]
        }
    }

    private fun getAllDeals(): Array<InterestRateSwap.State> {
        val states = api.queryStates(StatesQuery.selectAllDeals())
        val swaps = api.fetchStates(states).values.map { it as InterestRateSwap.State }.filterNotNull().toTypedArray()
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
        api.invokeProtocolSync(ProtocolClassRef(AutoOfferProtocol.Requester::class.java.name!!), mapOf("dealToBeOffered" to newDeal))
        return Response.created(URI.create(generateDealLink(newDeal))).build()
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

    @PUT
    @Path("demodate")
    @Consumes(MediaType.APPLICATION_JSON)
    fun storeDemoDate(newDemoDate: LocalDate): Response {
        val priorDemoDate = api.serverTime().toLocalDate()
        // Can only move date forwards
        if (newDemoDate.isAfter(priorDemoDate)) {
            api.invokeProtocolSync(ProtocolClassRef(UpdateBusinessDayProtocol.Broadcast::class.java.name!!), mapOf("date" to newDemoDate))
            return Response.ok().build()
        }
        val msg = "demodate is already $priorDemoDate and can only be updated with a later date"
        logger.info("Attempt to set demodate to $newDemoDate but $msg")
        return Response.status(Response.Status.CONFLICT).entity(msg).build()
    }

    @GET
    @Path("demodate")
    @Produces(MediaType.APPLICATION_JSON)
    fun fetchDemoDate(): LocalDate {
        return api.serverTime().toLocalDate()
    }

    @PUT
    @Path("restart")
    @Consumes(MediaType.APPLICATION_JSON)
    fun exitServer(): Response {
        api.invokeProtocolSync(ProtocolClassRef(ExitServerProtocol.Broadcast::class.java.name!!), mapOf("exitCode" to 83))
        return Response.ok().build()
    }
}
