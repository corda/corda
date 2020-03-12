package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
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
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.SendableMessage
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import net.corda.nodeapi.internal.requireMessageSize
import org.apache.qpid.proton.engine.Delivery
import rx.Observable
import rx.subjects.PublishSubject
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.withLock

/**
 * This create a socket acceptor instance that can receive possibly multiple AMQP connections.
 */
class AMQPServer(val hostName: String,
                 val port: Int,
                 private val configuration: AMQPConfiguration) : AutoCloseable {

    companion object {
        init {
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE)
        }

        private const val CORDA_AMQP_NUM_SERVER_THREAD_PROP_NAME = "net.corda.nodeapi.amqpserver.NumServerThreads"

        private val log = contextLogger()
        private val NUM_SERVER_THREADS = Integer.getInteger(CORDA_AMQP_NUM_SERVER_THREAD_PROP_NAME, 4)
    }

    private val lock = ReentrantLock()
    @Volatile
    private var stopping: Boolean = false
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private val clientChannels = ConcurrentHashMap<InetSocketAddress, SocketChannel>()

    private class ServerChannelInitializer(val parent: AMQPServer) : ChannelInitializer<SocketChannel>() {
        private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        private val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        private val conf = parent.configuration

        init {
            keyManagerFactory.init(conf.keyStore.value.internal, conf.keyStore.entryPassword.toCharArray())
            trustManagerFactory.init(initialiseTrustStoreAndEnableCrlChecking(conf.trustStore, conf.revocationConfig))
        }

        override fun initChannel(ch: SocketChannel) {
            val amqpConfiguration = parent.configuration
            val pipeline = ch.pipeline()
            amqpConfiguration.healthCheckPhrase?.let { pipeline.addLast(ModeSelectingChannel.NAME, ModeSelectingChannel(it)) }
            val (sslHandler, keyManagerFactoriesMap) = createSSLHandler(amqpConfiguration, ch)
            pipeline.addLast("sslHandler", sslHandler)
            if (conf.trace) pipeline.addLast("logger", LoggingHandler(LogLevel.INFO))
            val suppressLogs = ch.remoteAddress()?.hostString in amqpConfiguration.silencedIPs
            pipeline.addLast(AMQPChannelHandler(true,
                    null,
                    // Passing a mapping of legal names to key managers to be able to pick the correct one after
                    // SNI completion event is fired up.
                    keyManagerFactoriesMap,
                    conf.userName,
                    conf.password,
                    conf.trace,
                    suppressLogs,
                    onOpen = { channel, change ->
                        parent.run {
                            clientChannels[channel.remoteAddress()] = channel
                            _onConnection.onNext(change)
                        }
                    },
                    onClose = { channel, change ->
                        parent.run {
                            val remoteAddress = channel.remoteAddress()
                            clientChannels.remove(remoteAddress)
                            _onConnection.onNext(change)
                        }
                    },
                    onReceive = { rcv -> parent._onReceive.onNext(rcv) }))
        }

        private fun createSSLHandler(amqpConfig: AMQPConfiguration, ch: SocketChannel): Pair<ChannelHandler, Map<String, CertHoldingKeyManagerFactoryWrapper>> {
            return if (amqpConfig.useOpenSsl && amqpConfig.enableSNI && amqpConfig.keyStore.aliases().size > 1) {
                val keyManagerFactoriesMap = splitKeystore(amqpConfig)
                // SNI matching needed only when multiple nodes exist behind the server.
                Pair(createServerSNIOpenSslHandler(keyManagerFactoriesMap, trustManagerFactory), keyManagerFactoriesMap)
            } else {
                val keyManagerFactory = CertHoldingKeyManagerFactoryWrapper(keyManagerFactory, amqpConfig)
                val handler = if (amqpConfig.useOpenSsl) {
                    createServerOpenSslHandler(keyManagerFactory, trustManagerFactory, ch.alloc())
                } else {
                    // For javaSSL, SNI matching is handled at key manager level.
                    createServerSslHandler(amqpConfig.keyStore, keyManagerFactory, trustManagerFactory)
                }
                handler.handshakeTimeoutMillis = amqpConfig.sslHandshakeTimeout
                Pair(handler, mapOf(DEFAULT to keyManagerFactory))
            }
        }
    }

    fun start() {
        lock.withLock {
            stop()

            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup(NUM_SERVER_THREADS)

            val server = ServerBootstrap()
            // TODO Needs more configuration control when we profile. e.g. to use EPOLL on Linux
            server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(NettyServerEventLogger(LogLevel.INFO, configuration.silencedIPs))
                    .childHandler(ServerChannelInitializer(this))

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
        requireMessageSize(payload.size, configuration.maxMessageSize)
        val dest = InetSocketAddress(destinationLink.host, destinationLink.port)
        require(dest in clientChannels.keys) {
            "Destination $dest is not available"
        }
        return SendableMessageImpl(payload, topic, destinationLegalName, destinationLink, properties)
    }

    fun write(msg: SendableMessage) {
        val dest = InetSocketAddress(msg.destinationLink.host, msg.destinationLink.port)
        val channel = clientChannels[dest]
        if (channel == null) {
            throw IllegalStateException("Connection to ${msg.destinationLink} not active")
        } else {
            log.debug { "Writing message with payload of size ${msg.payload.size} into channel $channel" }
            channel.writeAndFlush(msg)
            log.debug { "Done writing message with payload of size ${msg.payload.size} into channel $channel" }
        }
    }

    fun dropConnection(connectionRemoteHost: InetSocketAddress) {
        clientChannels[connectionRemoteHost]?.close()
    }

    fun complete(delivery: Delivery, target: InetSocketAddress) {
        val channel = clientChannels[target]
        channel?.apply {
            log.debug { "Writing delivery $delivery into channel $channel" }
            writeAndFlush(delivery)
            log.debug { "Done writing delivery $delivery into channel $channel" }
        }
    }

    private val _onReceive = PublishSubject.create<ReceivedMessage>().toSerialized()
    val onReceive: Observable<ReceivedMessage>
        get() = _onReceive

    private val _onConnection = PublishSubject.create<ConnectionChange>().toSerialized()
    val onConnection: Observable<ConnectionChange>
        get() = _onConnection
}