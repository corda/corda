/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.Slf4JLoggerFactory
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.nodeapi.internal.requireMessageSize
import org.apache.qpid.proton.engine.Delivery
import rx.Observable
import rx.subjects.PublishSubject
import java.net.BindException
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

/**
 * This create a socket acceptor instance that can receive possibly multiple AMQP connections.
 * As of now this is not used outside of testing, but in future it will be used for standalone bridging components.
 */
class AMQPServer(val hostName: String,
                 val port: Int,
                 private val userName: String?,
                 private val password: String?,
                 private val keyStore: KeyStore,
                 private val keyStorePrivateKeyPassword: CharArray,
                 private val trustStore: KeyStore,
                 private val crlCheckSoftFail: Boolean,
                 private val trace: Boolean = false,
                 private val maxMessageSize: Int) : AutoCloseable {

    companion object {
        init {
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE)
        }

        private val log = contextLogger()
        const val NUM_SERVER_THREADS = 4
    }

    private val lock = ReentrantLock()
    @Volatile
    private var stopping: Boolean = false
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private val clientChannels = ConcurrentHashMap<InetSocketAddress, SocketChannel>()

    constructor(hostName: String,
                port: Int,
                userName: String?,
                password: String?,
                keyStore: KeyStore,
                keyStorePrivateKeyPassword: String,
                trustStore: KeyStore,
                crlCheckSoftFail: Boolean,
                trace: Boolean = false,
                maxMessageSize: Int) : this(hostName, port, userName, password, keyStore, keyStorePrivateKeyPassword.toCharArray(), trustStore, crlCheckSoftFail, trace, maxMessageSize)

    private class ServerChannelInitializer(val parent: AMQPServer) : ChannelInitializer<SocketChannel>() {
        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            keyManagerFactory.init(parent.keyStore, parent.keyStorePrivateKeyPassword)
            trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(parent.trustStore, parent.crlCheckSoftFail))
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            val handler = createServerSslHelper(keyManagerFactory, trustManagerFactory)
            pipeline.addLast("sslHandler", handler)
            if (parent.trace) pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            pipeline.addLast(AMQPChannelHandler(true,
                    null,
                    parent.userName,
                    parent.password,
                    parent.trace,
                    {
                        parent.clientChannels[it.first.remoteAddress()] = it.first
                        parent._onConnection.onNext(it.second)
                    },
                    {
                        parent.clientChannels.remove(it.first.remoteAddress())
                        parent._onConnection.onNext(it.second)
                    },
                    { rcv -> parent._onReceive.onNext(rcv) }))
        }
    }

    fun start() {
        lock.withLock {
            stop()

            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS)

            val server = ServerBootstrap()
            // TODO Needs more configuration control when we profile. e.g. to use EPOLL on Linux
            server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java).option(ChannelOption.SO_BACKLOG, 100).handler(LoggingHandler(LogLevel.INFO)).childHandler(ServerChannelInitializer(this))

            log.info("Try to bind $port")
            val channelFuture = server.bind(hostName, port).sync() // block/throw here as better to know we failed to claim port than carry on
            if (!channelFuture.isDone || !channelFuture.isSuccess) {
                throw BindException("Failed to bind port $port")
            }
            log.info("Listening on port $port")
            serverChannel = channelFuture.channel()
        }
    }

    fun stop() {
        lock.withLock {
            try {
                stopping = true
                serverChannel?.apply { close() }
                serverChannel = null

                workerGroup?.shutdownGracefully()
                workerGroup?.terminationFuture()?.sync()

                bossGroup?.shutdownGracefully()
                bossGroup?.terminationFuture()?.sync()

                workerGroup = null
                bossGroup = null
            } finally {
                stopping = false
            }
        }
    }

    override fun close() = stop()

    val listening: Boolean
        get() {
            val channel = lock.withLock { serverChannel }
            return channel?.isActive ?: false
        }

    fun createMessage(payload: ByteArray,
                      topic: String,
                      destinationLegalName: String,
                      destinationLink: NetworkHostAndPort,
                      properties: Map<String, Any?>): SendableMessage {
        requireMessageSize(payload.size, maxMessageSize)
        val dest = InetSocketAddress(destinationLink.host, destinationLink.port)
        require(dest in clientChannels.keys) {
            "Destination not available"
        }
        return SendableMessageImpl(payload, topic, destinationLegalName, destinationLink, properties)
    }

    fun write(msg: SendableMessage) {
        val dest = InetSocketAddress(msg.destinationLink.host, msg.destinationLink.port)
        val channel = clientChannels[dest]
        if (channel == null) {
            throw IllegalStateException("Connection to ${msg.destinationLink} not active")
        } else {
            channel.writeAndFlush(msg)
        }
    }

    fun dropConnection(connectionRemoteHost: InetSocketAddress) {
        val channel = clientChannels[connectionRemoteHost]
        if (channel != null) {
            channel.close()
        }
    }

    fun complete(delivery: Delivery, target: InetSocketAddress) {
        val channel = clientChannels[target]
        channel?.apply {
            writeAndFlush(delivery)
        }
    }

    private val _onReceive = PublishSubject.create<ReceivedMessage>().toSerialized()
    val onReceive: Observable<ReceivedMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChange>().toSerialized()
    val onConnection: Observable<ConnectionChange>
        get() = _onConnection
}