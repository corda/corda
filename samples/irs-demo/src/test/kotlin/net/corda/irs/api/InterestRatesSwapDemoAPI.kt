package net.corda.irs.api

import net.corda.core.internal.concurrent.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.loggerFor
import net.corda.irs.flows.UpdateBusinessDayFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * GET /api/irs/demodate - return the current date as viewed by the system in YYYY-MM-DD format.
 * PUT /api/irs/demodate - put date in format YYYY-MM-DD to advance the current date as viewed by the system and
 * POST /api/irs/fixes - store the fixing data as a text file
 */
@Path("irs")
class InterestRatesSwapDemoAPI(val rpc: CordaRPCOps) {
    companion object {
        private val logger = loggerFor<InterestRatesSwapDemoAPI>()
    }

    @PUT
    @Path("demodate")
    @Consumes(MediaType.APPLICATION_JSON)
    fun storeDemoDate(newDemoDate: LocalDate): Response {
        val priorDemoDate = fetchDemoDate()
        // Can only move date forwards
        if (newDemoDate.isAfter(priorDemoDate)) {
            rpc.startFlow(UpdateBusinessDayFlow::Broadcast, newDemoDate).returnValue.getOrThrow()
            return Response.ok().build()
        }
        val msg = "demodate is already $priorDemoDate and can only be updated with a later date"
        logger.error("Attempt to set demodate to $newDemoDate but $msg")
        return Response.status(Response.Status.CONFLICT).entity(msg).build()
    }

    @GET
    @Path("demodate")
    @Produces(MediaType.APPLICATION_JSON)
    fun fetchDemoDate(): LocalDate {
        return LocalDateTime.ofInstant(rpc.currentNodeTime(), ZoneId.systemDefault()).toLocalDate()
    }

    @POST
    @Path("fixes")
    @Consumes(MediaType.TEXT_PLAIN)
    fun storeFixes(file: String): Response {
        rpc.startFlow(NodeInterestRates::UploadFixesFlow, file).returnValue.getOrThrow()
        return Response.ok().build()
    }
}