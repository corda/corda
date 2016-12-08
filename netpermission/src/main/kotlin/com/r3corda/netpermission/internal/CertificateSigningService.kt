package com.r3corda.netpermission.internal

import com.r3corda.netpermission.internal.persistence.CertificationData
import com.r3corda.netpermission.internal.persistence.CertificationRequestStorage
import net.corda.core.crypto.X509Utilities.CACertAndKey
import net.corda.core.crypto.X509Utilities.CORDA_CLIENT_CA
import net.corda.core.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.core.crypto.X509Utilities.CORDA_ROOT_CA
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
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
import javax.ws.rs.core.Response.noContent
import javax.ws.rs.core.Response.ok

/**
 * Provides functionality for asynchronous submission of certificate signing requests and retrieval of the results.
 */
@Path("")
class CertificateSigningService(val intermediateCACertAndKey: CACertAndKey, val rootCert: Certificate, val storage: CertificationRequestStorage) {
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
        val requestId = storage.saveRequest(CertificationData(request.remoteHost, request.remoteAddr, certificationRequest))
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
        val clientCert = storage.getCertificate(requestId)
        return if (clientCert != null) {
            // Write certificate chain to a zip stream and extract the bit array output.
            ByteArrayOutputStream().use {
                ZipOutputStream(it).use {
                    zipStream ->
                    // Client certificate must come first and root certificate should come last.
                    mapOf(CORDA_CLIENT_CA to clientCert,
                            CORDA_INTERMEDIATE_CA to intermediateCACertAndKey.certificate,
                            CORDA_ROOT_CA to rootCert).forEach {
                        zipStream.putNextEntry(ZipEntry("${it.key}.cer"))
                        zipStream.write(it.value.encoded)
                        zipStream.setComment(it.key)
                        zipStream.closeEntry()
                    }
                }
                ok(it.toByteArray())
                        .type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"certificates.zip\"")
            }
        } else {
            noContent()
        }.build()
    }
}