package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AttestationErrorTest {
    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val error = AttestationError("<error-message>")
        val str = mapper.writeValueAsString(error)
        assertEquals("""{"message":"<error-message>"}""", str)
    }

    @Test
    fun testSerialiseEmpty() {
        val request = AttestationError("")
        val str = mapper.writeValueAsString(request)
        assertEquals("""{"message":""}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"message":"<error-message>"}"""
        val error = mapper.readValue(str, AttestationError::class.java)
        assertEquals("<error-message>", error.message)
    }

    @Test
    fun testDeserialiseEmpty() {
        val str = """{"message":""}"""
        val error = mapper.readValue(str, AttestationError::class.java)
        assertEquals("", error.message)
    }
}
