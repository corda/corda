package net.corda.tools.shell

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class CustomTypeJsonParsingTests {
    lateinit var objectMapper: ObjectMapper

    //Dummy classes for testing.
    data class State(val linearId: UniqueIdentifier) {
        constructor() : this(UniqueIdentifier("required-for-json-deserializer"))
    }

    data class UuidState(val uuid: UUID) {
        //Default constructor required for json deserializer.
        constructor() : this(UUID.randomUUID())
    }

    @Before
    fun setup() {
        objectMapper = ObjectMapper()
        val simpleModule = SimpleModule()
        simpleModule.addDeserializer(UniqueIdentifier::class.java, UniqueIdentifierDeserializer)
        objectMapper.registerModule(simpleModule)
    }

    @Test
    fun `Deserializing UniqueIdentifier by parsing string`() {
        val id = "26b37265-a1fd-4c77-b2e0-715917ef619f"
        val json = """{"linearId":"$id"}"""
        val state = objectMapper.readValue<State>(json)

        assertEquals(id, state.linearId.id.toString())
    }

    @Test
    fun `Deserializing UniqueIdentifier by parsing string with underscore`() {
        val json = """{"linearId":"extkey564_26b37265-a1fd-4c77-b2e0-715917ef619f"}"""
        val state = objectMapper.readValue<State>(json)

        assertEquals("extkey564", state.linearId.externalId)
        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f", state.linearId.id.toString())
    }

    @Test(expected = JsonMappingException::class)
    fun `Deserializing by parsing string contain invalid uuid with underscore`() {
        val json = """{"linearId":"extkey564_26b37265-a1fd-4c77-b2e0"}"""
        objectMapper.readValue<State>(json)
    }

    @Test
    fun `Deserializing UUID by parsing string`() {
        val json = """{"uuid":"26b37265-a1fd-4c77-b2e0-715917ef619f"}"""
        val state = objectMapper.readValue<UuidState>(json)

        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f", state.uuid.toString())
    }

    @Test(expected = JsonMappingException::class)
    fun `Deserializing UUID by parsing invalid uuid string`() {
        val json = """{"uuid":"26b37265-a1fd-4c77-b2e0"}"""
        objectMapper.readValue<UuidState>(json)
    }
}