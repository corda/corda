package net.corda.serialization.internal.verifier

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.concurrent.openFuture
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetAttachments
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

class ExternalVerifierTypesTest {
    @get:Rule
    val testSerialization = SerializationEnvironmentRule()

    @Test(timeout=300_000)
    fun `socket channel read-write`() {
        val payload = GetAttachments(setOf(SecureHash.randomSHA256(), SecureHash.randomSHA256()))

        val serverChannel = ServerSocketChannel.open()
        serverChannel.bind(null)

        val future = openFuture<GetAttachments>()
        thread {
            SocketChannel.open().use {
                it.connect(InetSocketAddress(serverChannel.socket().localPort))
                val received = it.readCordaSerializable(GetAttachments::class)
                future.set(received)
            }
        }

        serverChannel.use { it.accept().writeCordaSerializable(payload) }

        assertThat(future.get()).isEqualTo(payload)
    }
}
