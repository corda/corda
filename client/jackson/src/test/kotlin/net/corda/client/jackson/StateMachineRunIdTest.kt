package net.corda.client.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.internal.CordaModule
import net.corda.core.flows.StateMachineRunId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class StateMachineRunIdTest {
    private companion object {
        private const val ID = "a9da3d32-a08d-4add-a633-66bc6bf6183d"
        private val jsonMapper: ObjectMapper = ObjectMapper().registerModule(CordaModule())
    }

    @Test
    fun `state machine run ID deserialise`() {
        val str = """"$ID""""
        val runID = jsonMapper.readValue(str, StateMachineRunId::class.java)
        assertEquals(StateMachineRunId(UUID.fromString(ID)), runID)
    }

    @Test
    fun `state machine run ID serialise`() {
        val runId = StateMachineRunId(UUID.fromString(ID))
        val str = jsonMapper.writeValueAsString(runId)
        assertEquals(""""$ID"""", str)
    }
}