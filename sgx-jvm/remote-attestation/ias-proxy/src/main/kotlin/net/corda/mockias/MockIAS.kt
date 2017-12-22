package net.corda.mockias

import net.corda.attestation.ias.message.ReportRequest
import net.corda.attestation.message.ias.ManifestStatus
import net.corda.attestation.message.ias.QuoteStatus
import net.corda.attestation.message.ias.ReportResponse
import java.time.Clock
import java.time.LocalDateTime
import javax.servlet.http.HttpServletResponse.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType.*
import javax.ws.rs.core.Response

@Path("/attestation/sgx/v2")
class MockIAS {
    private companion object {
        private const val requestID = "de305d5475b4431badb2eb6b9e546014"
        private const val QUOTE_BODY_SIZE = 432

        private val platformInfo = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte(), 0x11, 0x22)
    }

    @GET
    @Path("/sigrl/{gid}")
    fun getSigRL(@PathParam("gid") gid: String): Response {
        return Response.ok(if (gid.toLowerCase() == "00000000") "AAIADgAAAAEAAAABAAAAAGSf/es1h/XiJeCg7bXmX0S/NUpJ2jmcEJglQUI8VT5sLGU7iMFu3/UTCv9uPADal3LhbrQvhBa6+/dWbj8hnsE=" else "")
            .header("Request-ID", requestID)
            .build()
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @IASReport
    @POST
    @Path("/report")
    fun getReport(req: ReportRequest?): Response {
        val request = req ?: return Response.status(SC_BAD_REQUEST)
            .header("Request-ID", requestID)
            .build()
        val report = ReportResponse(
            id = "9497457846286849067596886882708771068",
            isvEnclaveQuoteStatus = QuoteStatus.OK,
            isvEnclaveQuoteBody = if (request.isvEnclaveQuote.size > QUOTE_BODY_SIZE)
                request.isvEnclaveQuote.copyOf(QUOTE_BODY_SIZE)
            else
                request.isvEnclaveQuote,
            pseManifestStatus = req.pseManifest?.toStatus(),
            platformInfoBlob = platformInfo,
            nonce = request.nonce,
            timestamp = LocalDateTime.now(Clock.systemUTC())
        )
        return Response.ok(report)
            .header("Request-ID", requestID)
            .build()
    }

    private fun ByteArray.toStatus(): ManifestStatus
        = if (this.isEmpty() || this[0] == 0.toByte()) ManifestStatus.INVALID else ManifestStatus.OK
}
