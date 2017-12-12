package net.corda.attestation.message

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Message0Test {
    private lateinit var mapper: ObjectMapper

    @Before
    fun setup() {
        mapper = ObjectMapper()
    }

    @Test
    fun testSerialise() {
        val msg0 = Message0(0x000F207F)
        val str = mapper.writeValueAsString(msg0)
        assertEquals("""{"extendedGID":991359}""", str)
    }

    @Test
    fun testDeserialise() {
        val str = """{"extendedGID":991359}"""
        val msg0 = mapper.readValue(str, Message0::class.java)
        assertEquals(0x000F207F, msg0.extendedGID)
    }
}