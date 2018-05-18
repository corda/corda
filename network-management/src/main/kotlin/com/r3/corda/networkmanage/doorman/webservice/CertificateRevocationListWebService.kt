package com.r3.corda.networkmanage.doorman.webservice

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.doorman.webservice.CertificateRevocationListWebService.Companion.CRL_PATH
import net.corda.core.utilities.contextLogger
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.ok
import javax.ws.rs.core.Response.status

@Path(CRL_PATH)
class CertificateRevocationListWebService(private val revocationListStorage: CertificateRevocationListStorage,
                                          private val caCrlBytes: ByteArray,
                                          private val emptyCrlBytes: ByteArray,
                                          cacheTimeout: Duration) {
    companion object {
        private val logger = contextLogger()
        const val CRL_PATH = "certificate-revocation-list"
        const val CRL_DATA_TYPE = "application/pkcs7-crl"
        const val DOORMAN = "doorman"
        const val ROOT = "root"
        const val EMPTY = "empty"
    }

    private val crlCache: LoadingCache<CrlIssuer, ByteArray> = Caffeine.newBuilder()
            .expireAfterWrite(cacheTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build({ key ->
                revocationListStorage.getCertificateRevocationList(key)?.encoded
            })

    @GET
    @Path(DOORMAN)
    @Produces(CRL_DATA_TYPE)
    fun getDoormanRevocationList(): Response {
        return getCrlResponse(CrlIssuer.DOORMAN)
    }

    @GET
    @Path(ROOT)
    @Produces(CRL_DATA_TYPE)
    fun getRootRevocationList(): Response {
        return ok(caCrlBytes).build()
    }

    @GET
    @Path(EMPTY)
    @Produces(CRL_DATA_TYPE)
    fun getEmptyRevocationList(): Response {
        return ok(emptyCrlBytes).build()
    }

    private fun getCrlResponse(issuer: CrlIssuer): Response {
        return try {
            val crlBytes = crlCache.get(issuer)
            if (crlBytes != null) {
                ok(crlBytes).build()
            } else {
                status(Response.Status.NOT_FOUND).build()
            }
        } catch (e: Exception) {
            logger.error("Error when retrieving the ${issuer.name} crl.", e)
            status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
    }
}