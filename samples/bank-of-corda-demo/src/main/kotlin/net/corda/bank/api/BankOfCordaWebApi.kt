package net.corda.bank.api

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// API is accessible from /api/bank. All paths specified below are relative to it.
@Path("bank")
class BankOfCordaWebApi(private val rpc: CordaRPCOps) {
    data class IssueRequestParams(
            val amount: Amount<Currency>,
            val issueToPartyName: CordaX500Name,
            val issuerBankPartyRef: String,
            val issuerBankName: CordaX500Name,
            val notaryName: CordaX500Name
    )

    private companion object {
        private val logger = contextLogger()
    }

    @GET
    @Path("date")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }

    /**
     *  Request asset issuance
     */
    @POST
    @Path("issue-asset-request")
    @Consumes(MediaType.APPLICATION_JSON)
    fun issueAssetRequest(params: IssueRequestParams): Response {
        // Resolve parties via RPC
        val issueToParty = rpc.wellKnownPartyFromX500Name(params.issueToPartyName)
                ?: return Response.status(Response.Status.FORBIDDEN).entity("Unable to locate ${params.issueToPartyName} in identity service").build()
        rpc.wellKnownPartyFromX500Name(params.issuerBankName) ?: return Response.status(Response.Status.FORBIDDEN).entity("Unable to locate ${params.issuerBankName} in identity service").build()
        val notaryParty = rpc.notaryIdentities().firstOrNull { it.name == params.notaryName }
                ?: return Response.status(Response.Status.FORBIDDEN).entity("Unable to locate notary ${params.notaryName} in network map").build()

        val anonymous = true
        val issuerBankPartyRef = OpaqueBytes.of(params.issuerBankPartyRef.toByte())

        // invoke client side of Issuer Flow: IssuanceRequester
        // The line below blocks and waits for the future to resolve.
        return try {
            rpc.startFlow(::CashIssueAndPaymentFlow, params.amount, issuerBankPartyRef, issueToParty, anonymous, notaryParty).returnValue.getOrThrow()
            logger.info("Issue and payment request completed successfully: $params")
            Response.status(Response.Status.CREATED).build()
        } catch (e: Exception) {
            logger.error("Issue and payment request failed", e)
            Response.status(Response.Status.FORBIDDEN).build()
        }
    }
}