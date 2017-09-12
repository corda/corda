package com.r3.corda.doorman

import com.r3.corda.doorman.persistence.CertificateResponse
import com.r3.corda.doorman.persistence.CertificationRequestData
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.node.utilities.X509Utilities.CORDA_CLIENT_CA
import net.corda.node.utilities.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.node.utilities.X509Utilities.CORDA_ROOT_CA
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.codehaus.jackson.map.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.cert.Certificate
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
@Path("")
class DoormanWebService(val intermediateCACertAndKey: CertificateAndKeyPair, val rootCert: Certificate, val storage: CertificationRequestStorage, val serverStatus: DoormanServerStatus) {
    @Context lateinit var request: HttpServletRequest
    /**
     * Accept stream of [PKCS10CertificationRequest] from user and persists in [CertificationRequestStorage] for approval.
     * Server returns HTTP 200 response with random generated request Id after request has been persisted.
     */
    @POST
    @Path("certificate")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    fun submitRequest(input: InputStream): Response {
        val certificationRequest = input.use {
            JcaPKCS10CertificationRequest(it.readBytes())
        }
        // TODO: Certificate signing request verifications.
        // TODO: Use jira api / slack bot to semi automate the approval process?
        // TODO: Acknowledge to user we have received the request via email?
        val requestId = storage.saveRequest(CertificationRequestData(request.remoteHost, request.remoteAddr, certificationRequest))
        return ok(requestId).build()
    }

    /**
     * Retrieve Certificate signing request from storage using the [requestId] and create a signed certificate if request has been approved.
     * Returns HTTP 200 with DER encoded signed certificates if request has been approved else HTTP 204 No content
     */
    @GET
    @Path("certificate/{var}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun retrieveCert(@PathParam("var") requestId: String): Response {
        val response = storage.getResponse(requestId)
        return when (response) {
            is CertificateResponse.Ready -> {
                // Write certificate chain to a zip stream and extract the bit array output.
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { zip ->
                    // Client certificate must come first and root certificate should come last.
                    val entries = listOf(
                            CORDA_CLIENT_CA to response.certificate,
                            CORDA_INTERMEDIATE_CA to intermediateCACertAndKey.certificate.toX509Certificate(),
                            CORDA_ROOT_CA to rootCert
                    )
                    entries.forEach {
                        zip.putNextEntry(ZipEntry("${it.first}.cer"))
                        zip.write(it.second.encoded)
                        zip.setComment(it.first)
                        zip.closeEntry()
                    }
                }
                ok(baos.toByteArray())
                        .type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"certificates.zip\"")
            }
            is CertificateResponse.NotReady -> noContent()
            is CertificateResponse.Unauthorised -> status(UNAUTHORIZED).entity(response.message)
        }.build()
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun status(): Response {
        return ok(ObjectMapper().writeValueAsString(serverStatus)).build()
    }
}