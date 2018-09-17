package net.corda.node.services.statemachine

import net.corda.core.CordaException
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.internal.AbstractNode
import net.corda.node.utilities.registration.CertificateRequestException
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class ExceptionsSerializationTest(private val initialException: CordaException, @Suppress("UNUSED_PARAMETER") description: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun data(): Collection<Array<Any>> = listOf(
                arrayOf<Any>(SessionRejectException("test"), "SessionRejectException"),
                arrayOf<Any>(CertificateRequestException("test"), "CertificateRequestException"),
                arrayOf<Any>(UnknownAnonymousPartyException("test"), "UnknownAnonymousPartyException"),
                arrayOf<Any>(AbstractNode.DatabaseConfigurationException("test"), "DatabaseConfigurationException")
        )
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun testMarshal() {
        val fromSerialized = performRoundTripSerialization(initialException)
        assertEquals(initialException.message, fromSerialized.message)
    }

    private inline fun <reified T : Any> performRoundTripSerialization(obj: T): T {
        val serializedForm: SerializedBytes<T> = obj.serialize()
        return serializedForm.deserialize()
    }
}