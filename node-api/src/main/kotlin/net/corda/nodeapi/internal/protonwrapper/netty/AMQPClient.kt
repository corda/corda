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

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.proxy.Socks4ProxyHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.Slf4JLoggerFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.nodeapi.internal.requireMessageSize
import rx.Observable
import rx.subjects.PublishSubject
import java.net.InetSocketAddress
import java.lang.Long.min
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

enum class SocksProxyVersion {
    SOCKS4,
    SOCKS5
}

data class SocksProxyConfig(val version: SocksProxyVersion, val proxyAddress: NetworkHostAndPort, val userName: String? = null, val password: String? = null) {
    init {
        if (version == SocksProxyVersion.SOCKS4) {
            require(password == null) { "SOCKS4 does not support a password" }
        }
    }
}

/**
 * The AMQPClient creates a connection initiator that will try to connect in a round-robin fashion
 * to the first open SSL socket. It will keep retrying until it is stopped.
 * To allow thread resource control it can accept a shared thread pool as constructor input,
 * otherwise it creates a self-contained Netty thraed pool and socket objects.
 * Once connected it can accept application packets to send via the AMQP protocol.
 */
class AMQPClient(val targets: List<NetworkHostAndPort>,
                 val allowedRemoteLegalNames: Set<CordaX500Name>,
                 private val userName: String?,
                 private val password: String?,
                 private val keyStore: KeyStore,
                 private val keyStorePrivateKeyPassword: String,
                 private val trustStore: KeyStore,
                 private val crlCheckSoftFail: Boolean,
                 private val trace: Boolean = false,
                 private val sharedThreadPool: EventLoopGroup? = null,
                 private val socksProxyConfig: SocksProxyConfig? = null,
                 private val maxMessageSize: Int) : AutoCloseable {
    companion object {
        init {
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE)
        }

        val log = contextLogger()
        const val MIN_RETRY_INTERVAL = 1000L
        const val MAX_RETRY_INTERVAL = 60000L
        const val BACKOFF_MULTIPLIER = 2L
        const val NUM_CLIENT_THREADS = 2
    }

    private val lock = ReentrantLock()
    @Volatile
    private var stopping: Boolean = false
    private var workerGroup: EventLoopGroup? = null
    @Volatile
    private var clientChannel: Channel? = null
    // Offset into the list of targets, so that we can implement round-robin reconnect logic.
    private var targetIndex = 0
    private var currentTarget: NetworkHostAndPort = targets.first()
    private var retryInterval = MIN_RETRY_INTERVAL

    private fun nextTarget() {
        targetIndex = (targetIndex + 1).rem(targets.size)
        log.info("Retry connect to ${targets[targetIndex]}")
        retryInterval = min(MAX_RETRY_INTERVAL, retryInterval * BACKOFF_MULTIPLIER)
    }

    private val connectListener = object : ChannelFutureListener {
        override fun operationComplete(future: ChannelFuture) {
            if (!future.isSuccess) {
                log.info("Failed to connect to $currentTarget")

                if (!stopping) {
                    workerGroup?.schedule({
                        nextTarget()
                        restart()
                    }, retryInterval, TimeUnit.MILLISECONDS)
                }
            } else {
                log.info("Connected to $currentTarget")
                // Connection established successfully
                clientChannel = future.channel()
                clientChannel?.closeFuture()?.addListener(closeListener)
            }
        }
    }

    private val closeListener = ChannelFutureListener { future ->
        log.info("Disconnected from $currentTarget")
        future.channel()?.disconnect()
        clientChannel = null
        if (!stopping) {
            workerGroup?.schedule({
                nextTarget()
                restart()
            }, retryInterval, TimeUnit.MILLISECONDS)
        }
    }

    private class ClientChannelInitializer(val parent: AMQPClient) : ChannelInitializer<SocketChannel>() {
        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        init {
            keyManagerFactory.init(parent.keyStore, parent.keyStorePrivateKeyPassword.toCharArray())
            trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(parent.trustStore, parent.crlCheckSoftFail))
        }

        override fun initChannel(ch: SocketChannel) {
            val pipeline = ch.pipeline()
            val socksConfig = parent.socksProxyConfig
            if (socksConfig != null) {
                val proxyAddress = InetSocketAddress(socksConfig.proxyAddress.host, socksConfig.proxyAddress.port)
                val proxy = when (parent.socksProxyConfig!!.version) {
                    SocksProxyVersion.SOCKS4 -> {
                        Socks4ProxyHandler(proxyAddress, socksConfig.userName)
                    }
                    SocksProxyVersion.SOCKS5 -> {
                        Socks5ProxyHandler(proxyAddress, socksConfig.userName, socksConfig.password)
                    }
                }
                pipeline.addLast("SocksPoxy", proxy)
                proxy.connectFuture().addListener {
                    if (!it.isSuccess) {
                        ch.disconnect()
                    }
                }
            }

            val handler = createClientSslHelper(parent.currentTarget, keyManagerFactory, trustManagerFactory)
            pipeline.addLast("sslHandler", handler)
            if (parent.trace) pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            pipeline.addLast(AMQPChannelHandler(false,
                    parent.allowedRemoteLegalNames,
                    parent.userName,
                    parent.password,
                    parent.trace,
                    {
                        parent.retryInterval = MIN_RETRY_INTERVAL // reset to fast reconnect if we connect properly
                        parent._onConnection.onNext(it.second)
                    },
                    { parent._onConnection.onNext(it.second) },
                    { rcv -> parent._onReceive.onNext(rcv) }))
        }
    }

    fun start() {
        lock.withLock {
            log.info("connect to: $currentTarget")
            workerGroup = sharedThreadPool ?: NioEventLoopGroup(NUM_CLIENT_THREADS)
            restart()
        }
    }

    private fun restart() {
        val bootstrap = Bootstrap()
        // TODO Needs more configuration control when we profile. e.g. to use EPOLL on Linux
        bootstrap.group(workerGroup).channel(NioSocketChannel::class.java).handler(ClientChannelInitializer(this))
        currentTarget = targets[targetIndex]
        val clientFuture = bootstrap.connect(currentTarget.host, currentTarget.port)
        clientFuture.addListener(connectListener)
    }

    fun stop() {
        lock.withLock {
            log.info("disconnect from: $currentTarget")
            stopping = true
            try {
                if (sharedThreadPool == null) {
                    workerGroup?.shutdownGracefully()
                    workerGroup?.terminationFuture()?.sync()
                } else {
                    clientChannel?.close()?.sync()
                }
                clientChannel = null
                workerGroup = null
            } finally {
                stopping = false
            }
            log.info("stopped connection to $currentTarget")
        }
    }

    override fun close() = stop()

    val connected: Boolean
        get() {
            val channel = lock.withLock { clientChannel }
            return channel?.isActive ?: false
        }

    fun createMessage(payload: ByteArray,
                      topic: String,
                      destinationLegalName: String,
                      properties: Map<String, Any?>): SendableMessage {
        requireMessageSize(payload.size, maxMessageSize)
        return SendableMessageImpl(payload, topic, destinationLegalName, currentTarget, properties)
    }

    fun write(msg: SendableMessage) {
        val channel = clientChannel
        if (channel == null) {
            throw IllegalStateException("Connection to $targets not active")
        } else {
            channel.writeAndFlush(msg)
        }
    }

    private val _onReceive = PublishSubject.create<ReceivedMessage>().toSerialized()
    val onReceive: Observable<ReceivedMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChange>().toSerialized()
    val onConnection: Observable<ConnectionChange>
        get() = _onConnection
}