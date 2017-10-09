package net.corda.node.services.messaging

import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class MessagingSerializationTests : TestDependencyInjectionBase() {

    @Test
    fun `test anonymous message serialization`() {
        val instance = createMessage(TopicSession("foo"), ByteArray(100), UUID.randomUUID())

        val serializedForm = instance.serialize()
        assertEquals(instance, serializedForm.deserialize())
    }

    /**
     * From [net.corda.node.services.messaging.NodeMessagingClient.createMessage]
     */
    private fun createMessage(topicSession: TopicSession, data: ByteArray, uuid: UUID): Message {
        return object : Message {
            override val topicSession: TopicSession = topicSession
            override val data: ByteArray = data
            override val debugTimestamp: Instant = Instant.now()
            override val uniqueMessageId: UUID = uuid
            override fun toString() = "$topicSession#${String(data)}"
        }
    }
}