package net.corda.attestation.message.ias

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.message.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReportRequestTest {
    private companion object {
        private val quoteData = byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46)
        private val quoteBase64 = quoteData.toBase64()

        private val manifestData = byteArrayOf(0x55, 0x72, 0x19, 0x5B)
        private val manifestBase64 = manifestData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val request = ReportRequest(
            isvEnclaveQuote = quoteData,
            pseManifest = manifestData,
            nonce = "<my-nonce>"
        )
        val str = mapper.writeValueAsString(request)
        assertEquals("{\"isvEnclaveQuote\":\"$quoteBase64\",\"pseManifest\":\"$manifestBase64\",\"nonce\":\"<my-nonce>\"}", str)
    }

    @Test
    fun testSerialiseEmpty() {
        val request = ReportRequest(
            isvEnclaveQuote = byteArrayOf()
        )
        val str = mapper.writeValueAsString(request)
        assertEquals("{\"isvEnclaveQuote\":\"\"}", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"isvEnclaveQuote":"$quoteBase64","pseManifest":"$manifestBase64","nonce":"<my-nonce>"}"""
        val request = mapper.readValue(str, ReportRequest::class.java)
        assertArrayEquals(quoteData, request.isvEnclaveQuote)
        assertArrayEquals(manifestData, request.pseManifest)
        assertEquals("<my-nonce>", request.nonce)
    }

    @Test
    fun testDeserialiseQuoteOnly() {
        val str = """{"isvEnclaveQuote":"$quoteBase64"}"""
        val request = mapper.readValue(str, ReportRequest::class.java)
        assertArrayEquals(quoteData, request.isvEnclaveQuote)
        assertNull(request.pseManifest)
        assertNull(request.nonce)
    }
}