package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Message1Test {
    private companion object {
        private val gaData = byteArrayOf(0x10, 0x00, 0x22, 0x00)
        private val gaBase64 = gaData.toBase64()
    }

    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val msg1 = Message1(
            ga = gaData,
            platformGID = "2139062143"
        )
        val str = mapper.writeValueAsString(msg1)
        assertEquals("""{"ga":"$gaBase64","platformGID":"2139062143"}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"ga":"$gaBase64","platformGID":"2139062143"}"""
        val msg1 = mapper.readValue(str, Message1::class.java)
        assertArrayEquals(gaData, msg1.ga)
        assertEquals("2139062143", msg1.platformGID)
    }
}
