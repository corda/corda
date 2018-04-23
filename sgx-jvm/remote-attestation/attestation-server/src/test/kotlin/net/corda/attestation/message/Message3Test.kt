package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Message3Test {
    private companion object {
        private val cmacData = byteArrayOf(0x50, 0x60, 0x70, 0x7F)
        private val cmacBase64 = cmacData.toBase64()
        private val gaData = byteArrayOf(0x10, 0x00, 0x22, 0x00)
        private val gaBase64 = gaData.toBase64()
        private val quoteData = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F, 0x7F, 0x7F, 0x7F, 0x7F)
        private val quoteBase64 = quoteData.toBase64()
        private val manifestData = byteArrayOf(0x44, 0x44, 0x44, 0x44, 0x44, 0x44)
        private val manifestBase64 = manifestData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testBasicSerialise() {
        val msg3 = Message3(
            aesCMAC = cmacData,
            ga = gaData,
            quote = quoteData
        )
        val str = mapper.writeValueAsString(msg3)
        assertEquals("{"
                + "\"aesCMAC\":\"$cmacBase64\","
                + "\"ga\":\"$gaBase64\","
                + "\"quote\":\"$quoteBase64\""
                + "}", str)
    }

    @Test
    fun testFullSerialise() {
        val msg3 = Message3(
            aesCMAC = cmacData,
            ga = gaData,
            quote = quoteData,
            securityManifest = manifestData,
            nonce = "<nonce-value>"
        )
        val str = mapper.writeValueAsString(msg3)
        assertEquals("{"
                + "\"aesCMAC\":\"$cmacBase64\","
                + "\"ga\":\"$gaBase64\","
                + "\"quote\":\"$quoteBase64\","
                + "\"securityManifest\":\"$manifestBase64\","
                + "\"nonce\":\"<nonce-value>\""
                + "}", str)
    }

    @Test
    fun testBasicDeserialise() {
        val str = """{
            "aesCMAC":"$cmacBase64",
            "ga":"$gaBase64",
            "quote":"$quoteBase64"
        }"""
        val msg3 = mapper.readValue(str, Message3::class.java)
        assertArrayEquals(cmacData, msg3.aesCMAC)
        assertArrayEquals(gaData, msg3.ga)
        assertArrayEquals(quoteData, msg3.quote)
    }

    @Test
    fun testFullDeserialise() {
        val str = """{
            "aesCMAC":"$cmacBase64",
            "ga":"$gaBase64",
            "quote":"$quoteBase64",
            "securityManifest":"$manifestBase64",
            "nonce":"<nonce-value>"
        }"""
        val msg3 = mapper.readValue(str, Message3::class.java)
        assertArrayEquals(cmacData, msg3.aesCMAC)
        assertArrayEquals(gaData, msg3.ga)
        assertArrayEquals(quoteData, msg3.quote)
        assertArrayEquals(manifestData, msg3.securityManifest)
        assertEquals("<nonce-value>", msg3.nonce)
    }
}
