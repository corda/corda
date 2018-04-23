/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.webservice

import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.doorman.signer.CsrHandler
import net.corda.core.internal.readFully
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.isSignatureValid
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.*
import javax.ws.rs.core.Response.Status.UNAUTHORIZED

/**
 * Provides functionality for asynchronous submission of certificate signing requests and retrieval of the results.
 */
@Path("certificate")
class RegistrationWebService(private val csrHandler: CsrHandler, private val clientPollInterval: Duration) {
    @Context lateinit var request: HttpServletRequest
    /**
     * Accept stream of [PKCS10CertificationRequest] from user and persists in [CertificateRequestStorage] for approval.
     * Server returns HTTP 200 response with random generated request Id after request has been persisted.
     */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun submitRequest(input: InputStream): Response {
        val csr = JcaPKCS10CertificationRequest(input.readFully())
        if (!csr.isSignatureValid()) {
            return status(Response.Status.BAD_REQUEST).entity("Invalid CSR signature").build()
        }
        val requestId = csrHandler.saveRequest(csr)
        return ok(requestId).build()
    }

    /**
     * Retrieve Certificate signing request from storage using the [requestId] and create a signed certificate if request has been approved.
     * Returns HTTP 200 with DER encoded signed certificates if request has been approved else HTTP 204 No content
     */
    @GET
    @Path("{var}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun retrieveCert(@PathParam("var") requestId: String): Response {
        val response = csrHandler.getResponse(requestId)
        return when (response) {
            is CertificateResponse.Ready -> {
                // Write certificate chain to a zip stream and extract the bit array output.
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { zip ->
                    // Client certificate must come first and root certificate should come last.
                    val certificates = ArrayList(response.certificatePath.certificates)
                    listOf(CORDA_CLIENT_CA, CORDA_INTERMEDIATE_CA, CORDA_ROOT_CA).zip(certificates).forEach {
                        zip.putNextEntry(ZipEntry("${it.first}.cer"))
                        zip.write(it.second.encoded)
                        zip.closeEntry()
                    }
                }
                ok(baos.toByteArray())
                        .type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"certificates.zip\"")
            }
            is CertificateResponse.NotReady -> noContent().header("Cache-Control", "max-age=${clientPollInterval.seconds}")
            is CertificateResponse.Unauthorised -> status(UNAUTHORIZED).entity(response.message)
        }.build()
    }
}