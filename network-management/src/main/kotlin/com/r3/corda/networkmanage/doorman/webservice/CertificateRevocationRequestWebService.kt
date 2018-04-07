package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.doorman.signer.CrrHandler
import com.r3.corda.networkmanage.doorman.webservice.CertificateRevocationRequestWebService.Companion.CRR_PATH
import net.corda.core.internal.readObject
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import java.io.InputStream
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status

@Path(CRR_PATH)
class CertificateRevocationRequestWebService(private val crrHandler: CrrHandler) {

    companion object {
        const val CRR_PATH = "certificate-revocation-request"
        val logger = contextLogger()
    }

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun submitRequest(input: InputStream): Response {
        return try {
            val request = input.readObject<CertificateRevocationRequest>()
            val requestId = crrHandler.saveRevocationRequest(request)
            ok(requestId)
        } catch (e: Exception) {
            logger.warn("Unable to process the revocation request.", e)
            when (e) {
                is IllegalArgumentException -> status(Response.Status.BAD_REQUEST).entity(e.message)
                else -> throw e
            }
        }.build()
    }
}