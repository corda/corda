package net.corda.coretesting.internal

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread


class NettyTestServer(
        private val sslContext: SslContext?,
        val messageHandler: ChannelInboundHandlerAdapter,
        val port: Int
) : Closeable {
    internal var mainThread: Thread? = null
    internal var channel: ChannelFuture? = null

    // lock/condition to make sure that start only returns when the server is actually running
    val lock = ReentrantLock()
    val condition: Condition = lock.newCondition()

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

    fun run() {
        // Configure the server.
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(LoggingHandler(LogLevel.INFO))
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        @Throws(Exception::class)
                        public override fun initChannel(ch: SocketChannel) {
                            val p = ch.pipeline()
                            if (sslContext != null) {
                                p.addLast(sslContext.newHandler(ch.alloc()))
                            }
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(messageHandler)
                        }
                    })

            // Start the server.
            val f = b.bind(port)
            try {
                lock.lock()
                channel = f.sync()
                condition.signal()
            } finally {
                lock.unlock()
            }

            // Wait until the server socket is closed.
            channel!!.channel().closeFuture().sync()
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    fun stop() {
        channel?.channel()?.close()
        mainThread?.join()
        channel = null
        mainThread = null
    }

    override fun close() {
        stop()
    }
}