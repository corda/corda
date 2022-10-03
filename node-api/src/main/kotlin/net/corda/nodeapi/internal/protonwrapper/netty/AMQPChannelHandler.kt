package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.SocketChannel
import io.netty.handler.proxy.ProxyConnectException
import io.netty.handler.proxy.ProxyConnectionEvent
import io.netty.handler.ssl.SniCompletionEvent
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.ssl.SslHandshakeTimeoutException
import io.netty.util.ReferenceCountUtil
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.nodeapi.internal.ArtemisConstants.MESSAGE_ID_KEY
import net.corda.nodeapi.internal.crypto.x509
import net.corda.nodeapi.internal.protonwrapper.engine.EventProcessor
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import net.corda.nodeapi.internal.protonwrapper.messages.impl.ReceivedMessageImpl
import net.corda.nodeapi.internal.protonwrapper.messages.impl.SendableMessageImpl
import org.apache.qpid.proton.engine.ProtonJTransport
import org.apache.qpid.proton.engine.Transport
import org.apache.qpid.proton.engine.impl.ProtocolTracer
import org.apache.qpid.proton.framing.TransportFrame
import org.slf4j.MDC
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.security.cert.X509Certificate
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLException

/**
 *  An instance of AMQPChannelHandler sits inside the netty pipeline and controls the socket level lifecycle.
 *  It also add some extra checks to the SSL handshake to support our non-standard certificate checks of legal identity.
 *  When a valid SSL connections is made then it initialises a proton-j engine instance to handle the protocol layer.
 */
@Suppress("TooManyFunctions")
internal class AMQPChannelHandler(private val serverMode: Boolean,
                                  private val allowedRemoteLegalNames: Set<CordaX500Name>?,
                                  private val keyManagerFactoriesMap: Map<String, CertHoldingKeyManagerFactoryWrapper>,
                                  private val userName: String?,
                                  private val password: String?,
                                  private val trace: Boolean,
                                  private val suppressLogs: Boolean,
                                  private val onOpen: (SocketChannel, ConnectionChange) -> Unit,
                                  private val onClose: (SocketChannel, ConnectionChange) -> Unit,
                                  private val onReceive: (ReceivedMessage) -> Unit) : ChannelDuplexHandler() {
    companion object {
        private val log = contextLogger()
        const val PROXY_LOGGER_NAME = "preProxyLogger"
    }

    private lateinit var remoteAddress: InetSocketAddress
    private var remoteCert: X509Certificate? = null
    private var eventProcessor: EventProcessor? = null
    private var suppressClose: Boolean = false
    private var connectionResult: ConnectionResult = ConnectionResult.NO_ERROR
    private var localCert: X509Certificate? = null
    private var requestedServerName: String? = null

    private fun withMDC(block: () -> Unit) {
        val oldMDC = MDC.getCopyOfContextMap() ?: emptyMap<String, String>()
        try {
            MDC.put("serverMode", serverMode.toString())
            MDC.put("remoteAddress", if (::remoteAddress.isInitialized) remoteAddress.toString() else null)
            MDC.put("localCert", localCert?.subjectDN?.toString())
            MDC.put("remoteCert", remoteCert?.subjectDN?.toString())
            MDC.put("allowedRemoteLegalNames", allowedRemoteLegalNames?.joinToString(separator = ";") { it.toString() })
            block()
        } finally {
            MDC.setContextMap(oldMDC)
        }
    }

    private fun logDebugWithMDC(msgFn: () -> String) {
        if (!suppressLogs) {
            if (log.isDebugEnabled) {
                withMDC { log.debug(msgFn()) }
            }
        } else {
            withMDC { log.trace(msgFn) }
        }
    }

    private fun logInfoWithMDC(msgFn: () -> String) {
        if (!suppressLogs) {
            if (log.isInfoEnabled) {
                withMDC { log.info(msgFn()) }
            }
        } else {
            withMDC { log.trace(msgFn) }
        }
    }

    private fun logWarnWithMDC(msg: String) = withMDC { if (!suppressLogs) log.warn(msg) else log.trace { msg } }

    private fun logErrorWithMDC(msg: String, ex: Throwable? = null) = withMDC { if (!suppressLogs) log.error(msg, ex) else log.trace(msg, ex) }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        remoteAddress = ch.remoteAddress() as InetSocketAddress
        val localAddress = ch.localAddress() as InetSocketAddress
        logInfoWithMDC { "New client connection ${ch.id()} from $remoteAddress to $localAddress" }
    }

    private fun createAMQPEngine(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        eventProcessor = EventProcessor(ch, serverMode, localCert!!.subjectX500Principal.toString(), remoteCert!!.subjectX500Principal.toString(), userName, password)
        if (trace) {
            val connection = eventProcessor!!.connection
            val transport = connection.transport as ProtonJTransport
            transport.protocolTracer = object : ProtocolTracer {
                override fun sentFrame(transportFrame: TransportFrame) {
                    logInfoWithMDC { "${transportFrame.body}" }
                }

                override fun receivedFrame(transportFrame: TransportFrame) {
                    logInfoWithMDC { "${transportFrame.body}" }
                }
            }
        }
        ctx.fireChannelActive()
        eventProcessor!!.processEventsAsync()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val ch = ctx.channel()
        logInfoWithMDC { "Closed client connection ${ch.id()} from $remoteAddress to ${ch.localAddress()}" }
        if (!suppressClose) {
            onClose(ch as SocketChannel, ConnectionChange(remoteAddress, remoteCert, false, connectionResult))
        }
        eventProcessor?.close()
        ctx.fireChannelInactive()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        when (evt) {
            is ProxyConnectionEvent -> {
                if (trace) {
                    log.info("ProxyConnectionEvent received: $evt")
                    try {
                        ctx.pipeline().remove(PROXY_LOGGER_NAME)
                    } catch (ex: NoSuchElementException) {
                        // ignore
                    }
                }
                // update address to the real target address
                remoteAddress = evt.destinationAddress()
            }
            is SniCompletionEvent -> {
                if (evt.isSuccess) {
                    // The SniCompletionEvent is fired up before context is switched (after SslHandshakeCompletionEvent)
                    // so we save the requested server name now to be able log it once the handshake is completed successfully
                    // Note: this event is only triggered when using OpenSSL.
                    requestedServerName = evt.hostname()
                    logInfoWithMDC { "SNI completion success." }
                } else {
                    logErrorWithMDC("SNI completion failure: ${evt.cause().message}")
                }
            }
            is SslHandshakeCompletionEvent -> {
                if (evt.isSuccess) {
                    handleSuccessfulHandshake(ctx)
                } else {
                    handleFailedHandshake(ctx, evt)
                }
            }
        }
    }

    private fun SslHandler.getRequestedServerName(): String? {
        return if (serverMode) {
            val session = engine().session
            when (session) {
                // Server name can be obtained from SSL session when using JavaSSL.
                is ExtendedSSLSession -> (session.requestedServerNames.firstOrNull() as? SNIHostName)?.asciiName
                // For Open SSL server name is obtained from SniCompletionEvent
                else -> requestedServerName
            }
        } else {
            (engine().sslParameters?.serverNames?.firstOrNull() as? SNIHostName)?.asciiName
        }
    }

    @Suppress("OverridingDeprecatedMember")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logWarnWithMDC("Closing channel due to nonrecoverable exception ${cause.message}")
        if (log.isTraceEnabled) {
            withMDC { log.trace("Pipeline uncaught exception", cause) }
        }
        if (cause is ProxyConnectException) {
            log.warn("Proxy connection failed ${cause.message}")
            suppressClose = true // The pipeline gets marked as active on connection to the proxy rather than to the target, which causes excess close events
        }
        ctx.close()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        try {
            if (msg is ByteBuf) {
                eventProcessor!!.transportProcessInput(msg)
            }
        } finally {
            ReferenceCountUtil.release(msg)
        }
        eventProcessor!!.processEventsAsync()
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        try {
            try {
                when (msg) {
                    // Transfers application packet into the AMQP engine.
                    is SendableMessageImpl -> {
                        val inetAddress = InetSocketAddress(msg.destinationLink.host, msg.destinationLink.port)
                        logDebugWithMDC { "Message for endpoint $inetAddress , expected $remoteAddress " }

                        require(CordaX500Name.parse(msg.destinationLegalName) == CordaX500Name.build(remoteCert!!.subjectX500Principal)) {
                            "Message for incorrect legal identity ${msg.destinationLegalName} expected ${remoteCert!!.subjectX500Principal}"
                        }
                        logDebugWithMDC { "channel write ${msg.applicationProperties[MESSAGE_ID_KEY]}" }
                        eventProcessor!!.transportWriteMessage(msg)
                    }
                    // A received AMQP packet has been completed and this self-posted packet will be signalled out to the
                    // external application.
                    is ReceivedMessage -> {
                        onReceive(msg)
                    }
                    // A general self-posted event that triggers creation of AMQP frames when required.
                    is Transport -> {
                        eventProcessor!!.transportProcessOutput(ctx)
                    }
                    // A self-posted event that forwards status updates for delivered packets to the application.
                    is ReceivedMessageImpl.MessageCompleter -> {
                        eventProcessor!!.complete(msg)
                    }
                }
            } catch (ex: Exception) {
                logErrorWithMDC("Error in AMQP write processing", ex)
                throw ex
            }
        } finally {
            ReferenceCountUtil.release(msg)
        }
        eventProcessor!!.processEventsAsync()
    }

    private fun handleSuccessfulHandshake(ctx: ChannelHandlerContext) {
        val sslHandler = ctx.pipeline().get(SslHandler::class.java)
        val sslSession = sslHandler.engine().session
        // Depending on what matching method is used, getting the local certificate is done by selecting the
        // appropriate keyManagerFactory
        val keyManagerFactory = requestedServerName?.let {
            keyManagerFactoriesMap[it]
        } ?: keyManagerFactoriesMap.values.single()

        localCert = keyManagerFactory.getCurrentCertChain()?.first()

        if (localCert == null) {
            log.error("SSL KeyManagerFactory failed to provide a local cert")
            ctx.close()
            return
        }
        if (sslSession.peerCertificates == null || sslSession.peerCertificates.isEmpty()) {
            log.error("No peer certificates")
            ctx.close()
            return
        }
        remoteCert = sslHandler.engine().session.peerCertificates.first().x509
        val remoteX500Name = try {
            CordaX500Name.build(remoteCert!!.subjectX500Principal)
        } catch (ex: IllegalArgumentException) {
            connectionResult = ConnectionResult.HANDSHAKE_FAILURE
            logErrorWithMDC("Certificate subject not a valid CordaX500Name", ex)
            ctx.close()
            return
        }
        if (allowedRemoteLegalNames != null && remoteX500Name !in allowedRemoteLegalNames) {
            connectionResult = ConnectionResult.HANDSHAKE_FAILURE
            logErrorWithMDC("Provided certificate subject $remoteX500Name not in expected set $allowedRemoteLegalNames")
            ctx.close()
            return
        }

        logInfoWithMDC { "Handshake completed with subject: $remoteX500Name, requested server name: ${sslHandler.getRequestedServerName()}." }
        createAMQPEngine(ctx)
        onOpen(ctx.channel() as SocketChannel, ConnectionChange(remoteAddress, remoteCert, connected = true, connectionResult = ConnectionResult.NO_ERROR))
    }

    private fun handleFailedHandshake(ctx: ChannelHandlerContext, evt: SslHandshakeCompletionEvent) {
        val cause = evt.cause()
        // This happens when the peer node is closed during SSL establishment.
        when {
            cause is ClosedChannelException -> logWarnWithMDC("SSL Handshake closed early.")
            cause is SslHandshakeTimeoutException -> logWarnWithMDC("SSL Handshake timed out")
            // Sadly the exception thrown by Netty wrapper requires that we check the message.
            cause is SSLException && (cause.message?.contains("close_notify") == true)
                                                                           -> logWarnWithMDC("Received close_notify during handshake")
            // io.netty.handler.ssl.SslHandler.setHandshakeFailureTransportFailure()
            cause is SSLException && (cause.message?.contains("writing TLS control frames") == true) -> logWarnWithMDC(cause.message!!)
            cause is SSLException && (cause.message?.contains("internal_error") == true) -> logWarnWithMDC("Received internal_error during handshake")
            else -> connectionResult = ConnectionResult.HANDSHAKE_FAILURE
        }
        logWarnWithMDC("Handshake failure: ${evt.cause().message}")
        if (log.isTraceEnabled) {
            withMDC { log.trace("Handshake failure", evt.cause()) }
        }
        ctx.close()
    }
}