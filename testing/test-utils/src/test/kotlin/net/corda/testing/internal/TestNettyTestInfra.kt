package net.corda.testing.internal

import io.netty.channel.ChannelInboundHandlerAdapter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestNettyTestInfra {
    @Test
    fun testStartAndStopServer() {
        val testHandler = rigorousMock<ChannelInboundHandlerAdapter>()
        NettyTestServer(null, testHandler, 56234).use { server ->
            server.start()
            assertNotNull(server.mainThread)
            assertNotNull(server.channel)
        }
    }

    @Test
    fun testStartAndStopClient() {
        val serverHandler = ChannelInboundHandlerAdapter()
        val clientHandler = ChannelInboundHandlerAdapter()

        NettyTestServer(null, serverHandler, 56234).use { server ->
            server.start()

            NettyTestClient(null, "localhost", 56234, clientHandler).use { client ->
                client.start()
                assertNotNull(client.mainThread)
                assertNotNull(client.channelFuture)
            }
        }
    }

    @Test
    fun testPingPong() {
        val serverHandler = NettyTestHandler { ctx, msg ->
            ctx?.writeAndFlush(msg)
        }
        val clientHandler = NettyTestHandler { _, msg ->
            assertEquals("ping", NettyTestHandler.readString(msg))
        }
        NettyTestServer(null, serverHandler, 56234).use { server ->
            server.start()

            NettyTestClient(null, "localhost", 56234, clientHandler).use { client ->
                client.start()

                clientHandler.writeString("ping")
                assertTrue(clientHandler.waitForReadCalled(1))
                clientHandler.rethrowIfFailed()
                assertEquals(1, clientHandler.readCalledCounter)
                assertEquals(1, serverHandler.readCalledCounter)
            }
        }
    }

    @Test
    fun testFailureHandling() {
        val serverHandler = NettyTestHandler { ctx, msg ->
            ctx?.writeAndFlush(msg)
        }
        val clientHandler = NettyTestHandler { _, msg ->
            assertEquals("pong", NettyTestHandler.readString(msg))
        }
        NettyTestServer(null, serverHandler, 56234).use { server ->
            server.start()

            NettyTestClient(null, "localhost", 56234, clientHandler).use { client ->
                client.start()

                clientHandler.writeString("ping")
                assertTrue(clientHandler.waitForReadCalled(1))
                var exceptionThrown = false
                try {
                    clientHandler.rethrowIfFailed()
                } catch (e: AssertionError) {
                    exceptionThrown = true
                }
                assertTrue(exceptionThrown, "Expected assertion failure has not been thrown")
                assertEquals(1, serverHandler.readCalledCounter)
                assertEquals(1, clientHandler.readCalledCounter)
            }
        }
    }

}