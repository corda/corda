package net.corda.node.shell

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.util.*
import kotlin.test.assertEquals

class CustomTypeJsonParsingTests {
    @Rule
    @JvmField
    val thrown : ExpectedException = ExpectedException.none()
    lateinit var objectMapper : ObjectMapper

    //dummy classes for testing
    data class State(val linearId: UniqueIdentifier){
        constructor() : this(UniqueIdentifier("required-for-json-deserializer"))
    }
    data class UuidState(val uuid: UUID){
        //default constructor required for json deserializer
        constructor() : this(UUID.randomUUID())
    }

    @Before
    fun setup(){
        objectMapper=ObjectMapper()
        val simpleModule=SimpleModule()
        simpleModule.addDeserializer(UniqueIdentifier::class.java, InteractiveShell.UniqueIdentifierDeserializer)
        simpleModule.addDeserializer(UUID::class.java, InteractiveShell.UUIDDeserializer)
        objectMapper.registerModule(simpleModule)
    }

    @Test
    fun `Deserializing UniqueIdentifier by parsing string`(){
        val json="{\"linearId\":\"26b37265-a1fd-4c77-b2e0-715917ef619f\"}"
        val state=objectMapper.readValue<State>(json)

        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f",state.linearId.externalId)
    }

    @Test
    fun `Deserializing UniqueIdentifier by parsing string with underscore`(){
        val json="{\"linearId\":\"extkey564_26b37265-a1fd-4c77-b2e0-715917ef619f\"}"
        val state=objectMapper.readValue<State>(json)

        assertEquals("extkey564",state.linearId.externalId)
        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f",state.linearId.id.toString())
    }

    @Test
    fun `Deserializing by parsing string contain invalid uuid with underscore`(){
        thrown.expect(JsonMappingException::class.java)
        thrown.expectMessage("Invalid UUID string: 26b37265-a1fd-4c77-b2e0")

        val json="{\"linearId\":\"extkey564_26b37265-a1fd-4c77-b2e0\"}"
        objectMapper.readValue<State>(json)
        //assertEquals("extkey564_26b37265-a1fd-4c77-b2e0",state.linearId.externalId)
    }

    @Test
    fun `Deserializing UUID by parsing string`(){
        val json="{\"uuid\":\"26b37265-a1fd-4c77-b2e0-715917ef619f\"}"
        val state=objectMapper.readValue<UuidState>(json)

        assertEquals("26b37265-a1fd-4c77-b2e0-715917ef619f",state.uuid.toString())
    }

    @Test
    fun `Deserializing UUID by parsing invalid uuid string`(){
       thrown.expect(JsonMappingException::class.java)
       thrown.expectMessage("Invalid UUID string: 26b37265-a1fd-4c77-b2e0")

       val json="{\"uuid\":\"26b37265-a1fd-4c77-b2e0\"}"
       objectMapper.readValue<UuidState>(json)
    }
}