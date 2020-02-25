package net.corda.coretesting.internal

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SslContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.ssl.SslHandler
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLEngine
import kotlin.concurrent.thread


class NettyTestClient(
        val sslContext: SslContext?,
        val targetHost: String,
        val targetPort: Int,
        val handler: ChannelInboundHandlerAdapter
) : Closeable {
    internal var mainThread: Thread? = null
    internal var channelFuture: ChannelFuture? = null

    // lock/condition to make sure that start only returns when the server is actually running
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    var engine: SSLEngine? = null
        private set

    fun start() {
        try {
            lock.lock()
            mainThread = thread(start = true) { run() }
            if (!condition.await(5, TimeUnit.SECONDS)) {
                throw TimeoutException("Netty test server failed to start")
            }
        } finally {
            lock.unlock()

        }
    }

    private fun run() {
        // Configure the client.
        val group = NioEventLoopGroup()
        try {
            val b = Bootstrap()
            b.group(group)
                    .channel(NioSocketChannel::class.java)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        @Throws(Exception::class)
                        public override fun initChannel(ch: SocketChannel) {
                            val p = ch.pipeline()
                            if (sslContext != null) {
                                engine = sslContext.newEngine(ch.alloc(), targetHost, targetPort)
                                p.addLast(SslHandler(engine))
                            }
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(handler)
                        }
                    })

            // Start the client.
            val f = b.connect(targetHost, targetPort)
            try {
                lock.lock()
                condition.signal()
                channelFuture = f.sync()
            } finally {
                lock.unlock()
            }

            // Wait until the connection is closed.
            f.channel().closeFuture().sync()
        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully()
        }
    }

    fun stop() {
        channelFuture?.channel()?.close()
        mainThread?.join()
        mainThread = null
        channelFuture = null
    }

    override fun close() {
        stop()
    }
}