package net.corda.nodeapi.internal.protonwrapper.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.SslHandler
import net.corda.core.utilities.contextLogger

/**
 * Responsible for deciding whether we are likely to be processing health probe request
 * or this is a normal SSL/AMQP processing pipeline
 */
internal class ModeSelectingChannel(healthCheckPhrase: String) : ByteToMessageDecoder() {

    companion object {
        const val NAME = "modeSelector"
        private val log = contextLogger()
    }

    private enum class TriState {
        UNDECIDED,
        ECHO_MODE,
        NORMAL_MODE
    }

    private val healthCheckPhraseArray = healthCheckPhrase.toByteArray(Charsets.UTF_8)

    private var currentMode = TriState.UNDECIDED

    private var alreadyEchoedPos = 0

    override fun decode(ctx: ChannelHandlerContext, inByteBuf: ByteBuf, out: MutableList<Any>?) {

        fun ChannelHandlerContext.echoBack(inByteBuf: ByteBuf) {

            // WriteAndFlush() will decrement count and will blow unless we retain first
            // And we have to ensure we are not sending the same information multiple times
            val toBeWritten = inByteBuf.retainedSlice(alreadyEchoedPos, inByteBuf.readableBytes() - alreadyEchoedPos)

            writeAndFlush(toBeWritten)

            alreadyEchoedPos = inByteBuf.readableBytes()
        }

        if(currentMode == TriState.ECHO_MODE) {
            ctx.echoBack(inByteBuf)
            return
        }

        // Wait until the length prefix is available.
        if (inByteBuf.readableBytes() < healthCheckPhraseArray.size) {
            return
        }

        // Direct buffers do not allow calling `.array()` on them, see `io.netty.buffer.UnpooledDirectByteBuf.array`
        val incomingArray = Unpooled.copiedBuffer(inByteBuf).array()
        val zipped = healthCheckPhraseArray.zip(incomingArray)
        if (zipped.all { it.first == it.second }) {
            // Matched the healthCheckPhrase
            currentMode = TriState.ECHO_MODE
            log.info("Echo mode activated for connection ${ctx.channel().id()}")
            // Cancel scheduled action to avoid SSL handshake timeout, which starts "ticking" upon connection is established,
            // namely upon call to `io.netty.handler.ssl.SslHandler#handlerAdded` is made
            ctx.pipeline().get(SslHandler::class.java)?.handshakeFuture()?.cancel(false)
            ctx.echoBack(inByteBuf)
        } else {
            currentMode = TriState.NORMAL_MODE
            // Remove self from pipeline and replay all the messages received down the pipeline
            // It is important to bump-up reference count as pipeline removal decrements it by one.
            inByteBuf.retain()
            ctx.pipeline().remove(this)
            ctx.fireChannelRead(inByteBuf)
        }
    }
}