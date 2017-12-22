package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.attestation.toBase64
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AttestationRequestTest {
    private companion object {
        private val publicKeyData = byteArrayOf(0x3F, 0x2B, 0x52, 0x31)
        private val publicKeyBase64 = publicKeyData.toBase64()
        private val signatureData = byteArrayOf(0x31, 0x35, 0x5D, 0x1A, 0x27, 0x44)
        private val signatureBase64 = signatureData.toBase64()
        private val aesCMACData = byteArrayOf(0x7C, 0x62, 0x50, 0x2B, 0x47, 0x0E)
        private val aesCMACBase64 = aesCMACData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val attestation = AttestationRequest(
            gb = publicKeyData,
            signatureGbGa = signatureData,
            aesCMAC = aesCMACData
        )
        val str = mapper.writeValueAsString(attestation)
        assertEquals("""{"gb":"$publicKeyBase64","signatureGbGa":"$signatureBase64","aesCMAC":"$aesCMACBase64"}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"gb":"$publicKeyBase64","signatureGbGa":"$signatureBase64","aesCMAC":"$aesCMACBase64"}"""
        val attestation = mapper.readValue(str, AttestationRequest::class.java)
        assertArrayEquals(publicKeyData, attestation.gb)
        assertArrayEquals(signatureData, attestation.signatureGbGa)
        assertArrayEquals(aesCMACData, attestation.aesCMAC)
    }
}