package com.r3.ha.utilities

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.readFully
import net.corda.core.internal.toSynchronised
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.node.internal.network.NetworkMapServer
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * A simple registration web server implementing the "doorman" protocol using [X509Utilities] and NetworkMap capable of vending NetworkParams.
 * This server is intended for integration testing only.
 */
class RegistrationServer(hostAndPort: NetworkHostAndPort = NetworkHostAndPort("localhost", 0)) : NetworkMapServer(10.seconds, hostAndPort, additionalServices = *arrayOf(SimpleDoormanService())) {

    @Path("certificate")
    internal class SimpleDoormanService {
        val csrMap = mutableMapOf<String, JcaPKCS10CertificationRequest>()
        val certificates = mutableMapOf<String, X509Certificate>().toSynchronised()

        @POST
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        @Produces(MediaType.TEXT_PLAIN)
        fun submitRequest(input: InputStream): Response {
            val csr = JcaPKCS10CertificationRequest(input.readFully())
            val requestId = SecureHash.randomSHA256().toString()
            csrMap[requestId] = csr
            certificates[requestId] = X509Utilities.createCertificate(CertificateType.NODE_CA, DEV_INTERMEDIATE_CA.certificate, DEV_INTERMEDIATE_CA.keyPair, X500Principal(csr.subject.toString()), csr.publicKey)
            return Response.ok(requestId).build()
        }

        @GET
        @Path("{var}")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        fun retrieveCert(@PathParam("var") requestId: String): Response {
            val cert = requireNotNull(certificates[requestId])
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zip ->
                val certificates = arrayListOf(cert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate)
                listOf(X509Utilities.CORDA_CLIENT_CA, X509Utilities.CORDA_INTERMEDIATE_CA, X509Utilities.CORDA_ROOT_CA).zip(certificates).forEach {
                    zip.putNextEntry(ZipEntry("${it.first}.cer"))
                    zip.write(it.second.encoded)
                    zip.closeEntry()
                }
            }
            return Response.ok(baos.toByteArray())
                    .type("application/zip")
                    .header("Content-Disposition", "attachment; filename=\"certificates.zip\"").build()
        }
    }
}