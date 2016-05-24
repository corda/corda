package com.r3corda.node.serialization

import com.r3corda.core.serialization.SerializeAsToken
import com.r3corda.core.serialization.SerializeAsTokenContext
import com.r3corda.core.serialization.SingletonSerializationToken
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.annotation.concurrent.ThreadSafe


/**
 * A [Clock] that tokenizes itself when serialized, and delegates to an underlying [Clock] implementation.
 */
@ThreadSafe
class NodeClock(private val delegateClock: Clock = Clock.systemUTC()) : Clock(), SerializeAsToken {

    private val token = SingletonSerializationToken(this)

    override fun toToken(context: SerializeAsTokenContext) = SingletonSerializationToken.registerWithContext(token, this, context)

    override fun instant(): Instant {
        return delegateClock.instant()
    }

    // Do not use this. Instead seek to use ZonedDateTime methods.
    override fun withZone(zone: ZoneId): Clock {
        throw UnsupportedOperationException("Tokenized clock does not support withZone()")
    }

    override fun getZone(): ZoneId {
        return delegateClock.zone
    }

}