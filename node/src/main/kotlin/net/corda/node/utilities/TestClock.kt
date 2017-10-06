package net.corda.node.utilities

import net.corda.core.internal.until
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SingletonSerializationToken.Companion.singletonSerializationToken
import net.corda.node.internal.MutableClock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.annotation.concurrent.ThreadSafe

/**
 * A [Clock] that can have the date advanced for use in demos.
 */
@ThreadSafe
class TestClock(private var delegateClock: Clock = Clock.systemUTC()) : MutableClock(), SerializeAsToken {

    private val token = singletonSerializationToken(javaClass)

    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)

    @Synchronized
    fun updateDate(date: LocalDate): Boolean {
        val currentDate = LocalDate.now(this)
        if (currentDate.isBefore(date)) {
            // It's ok to increment
            delegateClock = Clock.offset(delegateClock, currentDate.atStartOfDay() until date.atStartOfDay())
            notifyMutationObservers()
            return true
        }
        return false
    }

    @Synchronized override fun instant(): Instant {
        return delegateClock.instant()
    }

    // Do not use this. Instead seek to use ZonedDateTime methods.
    override fun withZone(zone: ZoneId): Clock {
        throw UnsupportedOperationException("Tokenized clock does not support withZone()")
    }

    @Synchronized override fun getZone(): ZoneId {
        return delegateClock.zone
    }

}
