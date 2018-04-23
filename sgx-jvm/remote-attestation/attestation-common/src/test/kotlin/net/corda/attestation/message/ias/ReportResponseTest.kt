package net.corda.attestation.message.ias

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.message.ias.ManifestStatus.*
import net.corda.attestation.message.ias.QuoteStatus.*
import net.corda.attestation.toBase64
import net.corda.attestation.unsignedByteArrayOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReportResponseTest {
    private companion object {
        private val iso8601Time = "2017-11-08T18:19:27.123456"
        private val testTimestamp = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS").parse(iso8601Time))

        private val quoteBodyData = byteArrayOf(0x61, 0x62, 0x63, 0x64, 0x65, 0x66)
        private val quoteBodyBase64 = quoteBodyData.toBase64()

        private val platformInfoData = unsignedByteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89, 0xab, 0xcd, 0xef)

        private val pseudonymData = byteArrayOf(0x63, 0x18, 0x33, 0x72)
        private val pseudonymBase64 = pseudonymData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper().registerModule(JavaTimeModule())
    }

    @Test
    fun testSerialiseBasic() {
        val response = ReportResponse(
            id = "197283916372863387388037565359257649452",
            isvEnclaveQuoteStatus = QuoteStatus.OK,
            isvEnclaveQuoteBody = quoteBodyData,
            timestamp = testTimestamp
        )
        val str = mapper.writeValueAsString(response)
        assertEquals("{"
                + "\"id\":\"197283916372863387388037565359257649452\","
                + "\"timestamp\":\"$iso8601Time\","
                + "\"isvEnclaveQuoteStatus\":\"OK\","
                + "\"isvEnclaveQuoteBody\":\"$quoteBodyBase64\""
                + "}", str)
    }

    @Test
    fun testSerialiseFull() {
        val response = ReportResponse(
            id = "197283916372863387388037565359257649452",
            isvEnclaveQuoteStatus = GROUP_OUT_OF_DATE,
            isvEnclaveQuoteBody = quoteBodyData,
            platformInfoBlob = platformInfoData,
            revocationReason = 1,
            pseManifestStatus = INVALID,
            pseManifestHash = "<manifest-hash>",
            nonce = "<nonce>",
            epidPseudonym = pseudonymData,
            timestamp = testTimestamp
        )
        val str = mapper.writeValueAsString(response)
        assertEquals("{"
                + "\"nonce\":\"<nonce>\","
                + "\"id\":\"197283916372863387388037565359257649452\","
                + "\"timestamp\":\"$iso8601Time\","
                + "\"epidPseudonym\":\"$pseudonymBase64\","
                + "\"isvEnclaveQuoteStatus\":\"GROUP_OUT_OF_DATE\","
                + "\"isvEnclaveQuoteBody\":\"$quoteBodyBase64\","
                + "\"pseManifestStatus\":\"INVALID\","
                + "\"pseManifestHash\":\"<manifest-hash>\","
                + "\"platformInfoBlob\":\"123456789abcdef\","
                + "\"revocationReason\":1"
                + "}", str)
    }

    @Test
    fun testDeserialiseBasic() {
        val str = """{
            "id":"197283916372863387388037565359257649452",
            "isvEnclaveQuoteStatus":"OK",
            "isvEnclaveQuoteBody":"$quoteBodyBase64",
            "timestamp":"$iso8601Time"
            }"""
        val response = mapper.readValue(str, ReportResponse::class.java)
        assertEquals("197283916372863387388037565359257649452", response.id)
        assertEquals(QuoteStatus.OK, response.isvEnclaveQuoteStatus)
        assertArrayEquals(quoteBodyData, response.isvEnclaveQuoteBody)
        assertNull(response.platformInfoBlob)
        assertNull(response.revocationReason)
        assertNull(response.pseManifestStatus)
        assertNull(response.pseManifestHash)
        assertNull(response.nonce)
        assertNull(response.epidPseudonym)
        assertEquals(testTimestamp, response.timestamp)
    }

    @Test
    fun testDeserialiseFull() {
        val str = """{
            "id":"197283916372863387388037565359257649452",
            "isvEnclaveQuoteStatus":"GROUP_OUT_OF_DATE",
            "isvEnclaveQuoteBody":"$quoteBodyBase64",
            "platformInfoBlob":"0123456789ABCDEF",
            "revocationReason":1,
            "pseManifestStatus":"OK",
            "pseManifestHash":"<manifest-hash>",
            "nonce":"<nonce>",
            "epidPseudonym":"$pseudonymBase64",
            "timestamp":"$iso8601Time"
        }"""
        val response = mapper.readValue(str, ReportResponse::class.java)
        assertEquals("197283916372863387388037565359257649452", response.id)
        assertEquals(QuoteStatus.GROUP_OUT_OF_DATE, response.isvEnclaveQuoteStatus)
        assertArrayEquals(quoteBodyData, response.isvEnclaveQuoteBody)
        assertArrayEquals(platformInfoData, response.platformInfoBlob)
        assertEquals(1, response.revocationReason)
        assertEquals(ManifestStatus.OK, response.pseManifestStatus)
        assertEquals("<manifest-hash>", response.pseManifestHash)
        assertEquals("<nonce>", response.nonce)
        assertArrayEquals(pseudonymData, response.epidPseudonym)
        assertEquals(testTimestamp, response.timestamp)
    }
}