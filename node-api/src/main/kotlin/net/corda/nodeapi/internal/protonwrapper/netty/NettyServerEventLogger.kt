package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.logging.LogLevel
import io.netty.util.internal.logging.InternalLogLevel
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.SocketAddress

@ChannelHandler.Sharable
class NettyServerEventLogger(level: LogLevel = DEFAULT_LEVEL, val silencedIPs: Set<String> = emptySet()) : ChannelDuplexHandler() {
    companion object {
        val DEFAULT_LEVEL: LogLevel = LogLevel.DEBUG
    }

    private val logger: InternalLogger = InternalLoggerFactory.getInstance(javaClass)
    private val internalLevel: InternalLogLevel = level.toInternalLevel()

    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, "Server socket ${ctx.channel()} ACTIVE")
        }
        ctx.fireChannelActive()
    }

    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, "Server socket ${ctx.channel()} INACTIVE")
        }
        ctx.fireChannelInactive()
    }

    @Suppress("OverridingDeprecatedMember")
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, "Server socket ${ctx.channel()}  EXCEPTION ${cause.message}", cause)
        }
        ctx.fireExceptionCaught(cause)
    }

    @Throws(Exception::class)
    override fun bind(ctx: ChannelHandlerContext, localAddress: SocketAddress, promise: ChannelPromise) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, "Server socket ${ctx.channel()} BIND $localAddress")
        }
        ctx.bind(localAddress, promise)
    }

    @Throws(Exception::class)
    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, "Server socket ${ctx.channel()} CLOSE")
        }
        ctx.close(promise)
    }

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val level = if (msg is io.netty.channel.socket.SocketChannel) { // Should always be the case as this is a server socket, but be defensive
            if (msg.remoteAddress()?.hostString !in silencedIPs) internalLevel else InternalLogLevel.TRACE
        } else internalLevel
        if (logger.isEnabled(level)) {
            logger.log(level, "Server socket ${ctx.channel()} ACCEPTED $msg")
        }
        ctx.fireChannelRead(msg)
    }
}