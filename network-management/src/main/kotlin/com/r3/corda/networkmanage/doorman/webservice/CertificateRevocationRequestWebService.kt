package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.doorman.signer.CrrHandler
import com.r3.corda.networkmanage.doorman.webservice.CertificateRevocationRequestWebService.Companion.CRR_PATH
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import java.io.InputStream
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok

@Path(CRR_PATH)
class CertificateRevocationRequestWebService(private val crrHandler: CrrHandler) {

    companion object {
        const val CRR_PATH = "certificate-revocation-request"
    }

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun submitRequest(input: InputStream): Response {
        val request = input.readBytes().deserialize<CertificateRevocationRequest>()
        val requestId = crrHandler.saveRevocationRequest(request)
        return ok(requestId).build()
    }
}