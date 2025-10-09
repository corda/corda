package net.corda.testing.core

import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SerializationEnvironmentExtensionTests {

    @JvmField
    @RegisterExtension
    val extension = SerializationEnvironmentExtension()

    @Test
    fun `serializationFactory is injected as a parameter`(factory: net.corda.core.serialization.SerializationFactory) {
        // JUnit 5 will automatically inject the SerializationFactory here
        assertNotNull(factory, "SerializationFactory should be injected")
        val original = "Injected Factory Test"
        val bytes = original.serialize()
        val restored = bytes.deserialize<String>()
        assertEquals(original, restored)
    }

    @Test
    fun `serialization environment is available during test`() {
        // effectiveSerializationEnv is automatically set
        assertNotNull(extension.serializationFactory)
        val original = "Hello Corda"
        val bytes = original.serialize()
        val restored = bytes.deserialize<String>()
        assertEquals(original, restored)
    }
}