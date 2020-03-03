package net.corda.coretesting.internal

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class NettyTestHandler(val onMessageFunc: (ctx: ChannelHandlerContext?, msg: Any?) -> Unit = { _, _ -> }) : ChannelDuplexHandler() {
    private var channel: Channel? = null
    private var failure: Throwable? = null

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    var readCalledCounter: Int = 0
        private set

    override fun channelRegistered(ctx: ChannelHandlerContext?) {
        channel = ctx?.channel()
        super.channelRegistered(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        try {
            lock.lock()
            readCalledCounter++
            onMessageFunc(ctx, msg)
        } catch( e: Throwable ){
            failure = e
        } finally {
            condition.signal()
            lock.unlock()
        }
    }

    fun writeString(msg: String) {
        val buffer = Unpooled.wrappedBuffer(msg.toByteArray())
        require(channel != null) { "Channel must be registered before sending messages" }
        channel!!.writeAndFlush(buffer)
    }

    fun rethrowIfFailed() {
        failure?.also { throw it }
    }

    fun waitForReadCalled(numberOfExpectedCalls: Int = 1): Boolean {
        try {
            lock.lock()
            if (readCalledCounter >= numberOfExpectedCalls) {
                return true
            }
            while (readCalledCounter < numberOfExpectedCalls) {
                if (!condition.await(5, TimeUnit.SECONDS)) {
                    return false
                }
            }
            return true
        } finally {
            lock.unlock()
        }
    }

    companion object {
        fun readString(buffer: Any?): String {
            checkNotNull(buffer)
            val ar = ByteArray((buffer as ByteBuf).readableBytes())
            buffer.readBytes(ar)
            return String(ar)
        }
    }
}