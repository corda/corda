package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Message2Test {
    private companion object {
        private const val SPID = "8F42710C36029FA11744"

        private val gbData = byteArrayOf(0x10, 0x00, 0x22, 0x00)
        private val gbBase64 = gbData.toBase64()
        private val revocationListData = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F)
        private val revocationListBase64 = revocationListData.toBase64()
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
        val msg2 = Message2(
            gb = gbData,
            spid = SPID,
            linkableQuote = true,
            keyDerivationFuncId = 1,
            signatureGbGa = signatureData,
            aesCMAC = aesCMACData,
            revocationList = revocationListData
        )
        val str = mapper.writeValueAsString(msg2)
        assertEquals("{"
                + "\"gb\":\"$gbBase64\","
                + "\"spid\":\"$SPID\","
                + "\"linkableQuote\":true,"
                + "\"keyDerivationFuncId\":1,"
                + "\"signatureGbGa\":\"$signatureBase64\","
                + "\"aesCMAC\":\"$aesCMACBase64\","
                + "\"revocationList\":\"$revocationListBase64\""
                + "}", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{
            "gb":"$gbBase64",
            "spid":"$SPID",
            "linkableQuote":true,
            "keyDerivationFuncId":1,
            "signatureGbGa":"$signatureBase64",
            "aesCMAC":"$aesCMACBase64",
            "revocationList":"$revocationListBase64"
        }"""
        val msg2 = mapper.readValue(str, Message2::class.java)
        assertArrayEquals(gbData, msg2.gb)
        assertEquals(SPID, msg2.spid)
        assertTrue(msg2.linkableQuote)
        assertEquals(1, msg2.keyDerivationFuncId)
        assertArrayEquals(signatureData, msg2.signatureGbGa)
        assertArrayEquals(aesCMACData, msg2.aesCMAC)
        assertArrayEquals(revocationListData, msg2.revocationList)
    }
}