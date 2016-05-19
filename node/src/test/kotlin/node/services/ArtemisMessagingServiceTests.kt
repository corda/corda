package node.services

import core.messaging.Message
import core.messaging.MessageRecipients
import core.testing.freeLocalHostAndPort
import node.services.messaging.ArtemisMessagingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

class ArtemisMessagingServiceTests {

    @Rule @JvmField val temporaryFolder = TemporaryFolder()

    val topic = "platform.self"
    lateinit var messagingNetwork: ArtemisMessagingService

    @Before
    fun setUp() {
        messagingNetwork = ArtemisMessagingService(temporaryFolder.newFolder().toPath(), freeLocalHostAndPort())
        messagingNetwork.start()
    }

    @After
    fun tearDown() {
        messagingNetwork.stop()
    }

    @Test
    fun `sending message to self`() {
        val receivedMessages = LinkedBlockingQueue<Message>()

        messagingNetwork.addMessageHandler(topic) { message, r ->
            receivedMessages.add(message)
        }

        sendMessage("first msg", messagingNetwork.myAddress)

        assertThat(String(receivedMessages.poll(2, SECONDS).data)).isEqualTo("first msg")
        assertThat(receivedMessages.poll(200, MILLISECONDS)).isNull()
    }

    private fun sendMessage(body: String, address: MessageRecipients) {
        messagingNetwork.send(messagingNetwork.createMessage(topic, body.toByteArray()), address)
    }

}