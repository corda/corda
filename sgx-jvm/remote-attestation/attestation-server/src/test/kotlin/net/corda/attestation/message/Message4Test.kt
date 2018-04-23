package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.attestation.unsignedByteArrayOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Message4Test {
    private companion object {
        private val iso8601Time = "2017-11-09T15:03:46.345678"
        private val testTimestamp = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS").parse(iso8601Time))
        private val quoteBodyData = byteArrayOf(0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D)
        private val quoteBodyBase64 = quoteBodyData.toBase64()
        private val cmacData = byteArrayOf(0x17, 0x1F, 0x73, 0x66, 0x2E, 0x3F)
        private val cmacBase64 = cmacData.toBase64()
        private val platformInfoData = unsignedByteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF)
        private val platformInfoBase64 = platformInfoData.toBase64()
        private val pseudonymData = byteArrayOf(0x31, 0x7E, 0x4A, 0x14)
        private val pseudonymBase64 = pseudonymData.toBase64()
        private val secretData = "Mystery data".toByteArray()
        private val secretBase64 = secretData.toBase64()
        private val secretHashData = byteArrayOf(0x02, 0x08, 0x25, 0x74)
        private val secretHashBase64 = secretHashData.toBase64()
        private val secretIVData = byteArrayOf(0x62, 0x72, 0x2A, 0x4F, 0x0E, -0x44)
        private val secretIVBase64 = secretIVData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper().registerModule(JavaTimeModule())
    }

    @Test
    fun testBasicSerialise() {
        val msg4 = Message4(
            reportID = "<report-id>",
            quoteStatus = "OK",
            quoteBody = quoteBodyData,
            aesCMAC = cmacData,
            platformInfo = byteArrayOf(),
            secret = secretData,
            secretHash = secretHashData,
            secretIV = secretIVData,
            timestamp = testTimestamp
        )
        val str = mapper.writeValueAsString(msg4)
        assertEquals("{"
                + "\"reportID\":\"<report-id>\","
                + "\"quoteStatus\":\"OK\","
                + "\"quoteBody\":\"$quoteBodyBase64\","
                + "\"aesCMAC\":\"$cmacBase64\","
                + "\"secret\":\"$secretBase64\","
                + "\"secretHash\":\"$secretHashBase64\","
                + "\"secretIV\":\"$secretIVBase64\","
                + "\"timestamp\":\"$iso8601Time\""
                + "}", str)
    }

    @Test
    fun testFullSerialise() {
        val msg4 = Message4(
            reportID = "<report-id>",
            quoteStatus = "GROUP_OUT_OF_DATE",
            quoteBody = quoteBodyData,
            aesCMAC = cmacData,
            securityManifestStatus = "INVALID",
            securityManifestHash = "<hash-value>",
            platformInfo = platformInfoData,
            epidPseudonym = pseudonymData,
            nonce = "<nonce-value>",
            secret = secretData,
            secretHash = secretHashData,
            secretIV = secretIVData,
            timestamp = testTimestamp
        )
        val str = mapper.writeValueAsString(msg4)
        assertEquals("{"
                + "\"reportID\":\"<report-id>\","
                + "\"quoteStatus\":\"GROUP_OUT_OF_DATE\","
                + "\"quoteBody\":\"$quoteBodyBase64\","
                + "\"aesCMAC\":\"$cmacBase64\","
                + "\"securityManifestStatus\":\"INVALID\","
                + "\"securityManifestHash\":\"<hash-value>\","
                + "\"platformInfo\":\"$platformInfoBase64\","
                + "\"epidPseudonym\":\"$pseudonymBase64\","
                + "\"nonce\":\"<nonce-value>\","
                + "\"secret\":\"$secretBase64\","
                + "\"secretHash\":\"$secretHashBase64\","
                + "\"secretIV\":\"$secretIVBase64\","
                + "\"timestamp\":\"$iso8601Time\""
                + "}", str)
    }

    @Test
    fun testBasicDeserialise() {
        val str = """{
            "reportID":"<report-id>",
            "quoteStatus":"OK",
            "quoteBody":"$quoteBodyBase64",
            "aesCMAC":"$cmacBase64",
            "secret":"$secretBase64",
            "secretHash":"$secretHashBase64",
            "secretIV":"$secretIVBase64",
            "timestamp":"$iso8601Time"
        }"""
        val msg4 = mapper.readValue(str, Message4::class.java)
        assertEquals("<report-id>", msg4.reportID)
        assertEquals("OK", msg4.quoteStatus)
        assertArrayEquals(quoteBodyData, msg4.quoteBody)
        assertArrayEquals(cmacData, msg4.aesCMAC)
        assertNull(msg4.platformInfo)
        assertArrayEquals(secretData, msg4.secret)
        assertArrayEquals(secretHashData, msg4.secretHash)
        assertArrayEquals(secretIVData, msg4.secretIV)
        assertEquals(testTimestamp, msg4.timestamp)
    }

    @Test
    fun testFullDeserialise() {
        val str = """{
            "reportID":"<report-id>",
            "quoteStatus":"GROUP_OUT_OF_DATE",
            "quoteBody":"$quoteBodyBase64",
            "aesCMAC":"$cmacBase64",
            "securityManifestStatus":"INVALID",
            "securityManifestHash":"<hash-value>",
            "platformInfo":"$platformInfoBase64",
            "epidPseudonym":"$pseudonymBase64",
            "nonce":"<nonce-value>",
            "secret":"$secretBase64",
            "secretHash":"$secretHashBase64",
            "secretIV":"$secretIVBase64",
            "timestamp":"$iso8601Time"
        }"""
        val msg4 = mapper.readValue(str, Message4::class.java)
        assertEquals("<report-id>", msg4.reportID)
        assertEquals("GROUP_OUT_OF_DATE", msg4.quoteStatus)
        assertArrayEquals(quoteBodyData, msg4.quoteBody)
        assertArrayEquals(cmacData, msg4.aesCMAC)
        assertEquals("INVALID", msg4.securityManifestStatus)
        assertEquals("<hash-value>", msg4.securityManifestHash)
        assertArrayEquals(platformInfoData, msg4.platformInfo)
        assertArrayEquals(pseudonymData, msg4.epidPseudonym)
        assertEquals("<nonce-value>", msg4.nonce)
        assertArrayEquals(secretData, msg4.secret)
        assertArrayEquals(secretHashData, msg4.secretHash)
        assertArrayEquals(secretIVData, msg4.secretIV)
        assertEquals(testTimestamp, msg4.timestamp)
    }
}