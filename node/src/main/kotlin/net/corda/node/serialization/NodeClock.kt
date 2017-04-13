package net.corda.node.serialization

import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SingletonSerializationToken
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.annotation.concurrent.ThreadSafe


/**
 * A [Clock] that tokenizes itself when serialized, and delegates to an underlying [Clock] implementation.
 */
@ThreadSafe
class NodeClock(private val delegateClock: Clock = Clock.systemUTC()) : Clock(), SerializeAsToken {

    private val token = singletonSerializationToken(javaClass)

    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)

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
