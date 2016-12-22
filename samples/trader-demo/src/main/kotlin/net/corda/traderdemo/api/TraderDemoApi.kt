package net.corda.traderdemo.api

import net.corda.contracts.testing.calculateRandomlySizedAmounts
import net.corda.core.contracts.DOLLARS
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.BOC
import net.corda.traderdemo.flow.SellerFlow
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

// API is accessible from /api/traderdemo. All paths specified below are relative to it.
@Path("traderdemo")
class TraderDemoApi(val rpc: CordaRPCOps) {
    data class TestCashParams(val amount: Int, val notary: String)
    data class SellParams(val amount: Int)
    private companion object {
        val logger = loggerFor<TraderDemoApi>()
    }

    /**
     * Uses a central bank node (Bank of Corda) to request issuance of some cash.
     */
    @PUT
    @Path("create-test-cash")
    @Consumes(MediaType.APPLICATION_JSON)
    fun createTestCash(params: TestCashParams): Response {
        val bankOfCordaParty = rpc.partyFromName(BOC.name)
                ?: throw Exception("Unable to locate ${BOC.name} in Network Map Service")
        val me = rpc.nodeIdentity()
        // TODO: revert back to multiple issue request amounts (3,10) when soft locking implemented
        val amounts = calculateRandomlySizedAmounts(params.amount.DOLLARS, 1, 1, Random())
        val handles = amounts.map {
            rpc.startFlow(::IssuanceRequester, params.amount.DOLLARS, me.legalIdentity, OpaqueBytes.of(1), bankOfCordaParty)
        }
        handles.forEach {
            require(it.returnValue.toBlocking().first() is SignedTransaction)
        }
        return Response.status(Response.Status.CREATED).build()
    }

    @POST
    @Path("{party}/sell-cash")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sellCash(params: SellParams, @PathParam("party") partyName: String): Response {
        val otherParty = rpc.partyFromName(partyName)
        if (otherParty != null) {
            // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
            //
            // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
            // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
            //
            // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
            if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
                javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                    val id = rpc.uploadAttachment(it)
                    assertEquals(SellerFlow.PROSPECTUS_HASH, id)
                }
            }

            // The line below blocks and waits for the future to resolve.
            val stx = rpc.startFlow(::SellerFlow, otherParty, params.amount.DOLLARS).returnValue.toBlocking().first()
            logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(stx.tx)}")
            return Response.status(Response.Status.OK).build()
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }
    }
}
