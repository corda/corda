/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import net.corda.core.serialization.SingletonSerializationToken
import rx.Observable
import rx.Subscriber
import rx.subscriptions.Subscriptions
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.concurrent.ThreadSafe

/** A [Clock] that tokenizes itself when serialized, and delegates to an underlying [Clock] implementation. */
abstract class CordaClock : Clock(), SerializeAsToken {
    protected abstract val delegateClock: Clock
    private val token = SingletonSerializationToken.singletonSerializationToken(javaClass)
    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)
    override fun instant(): Instant = delegateClock.instant()
    override fun getZone(): ZoneId = delegateClock.zone
    @Deprecated("Do not use this. Instead seek to use ZonedDateTime methods.", level = DeprecationLevel.ERROR)
    override fun withZone(zone: ZoneId) = throw UnsupportedOperationException("Tokenized clock does not support withZone()")

    /** This is an observer on the mutation count of this [Clock], which reflects the occurrence of mutations. */
    abstract val mutations: Observable<Long>
}

@ThreadSafe
class SimpleClock(override val delegateClock: Clock) : CordaClock() {
    override val mutations: Observable<Long> = Observable.never()
}

/**
 * An abstract class with helper methods for a type of Clock that might have it's concept of "now" adjusted externally.
 * e.g. for testing (so unit tests do not have to wait for timeouts in realtime) or for demos and simulations.
 */
abstract class MutableClock(private var _delegateClock: Clock) : CordaClock() {
    override var delegateClock
        @Synchronized get() = _delegateClock
        @Synchronized set(clock) {
            _delegateClock = clock
        }
    private val _version = AtomicLong(0L)
    override val mutations: Observable<Long> by lazy {
        Observable.create { subscriber: Subscriber<in Long> ->
            if (!subscriber.isUnsubscribed) {
                mutationObservers.add(subscriber)
                // This is not very intuitive, but subscribing to a subscriber observes unsubscribes.
                subscriber.add(Subscriptions.create { mutationObservers.remove(subscriber) })
            }
        }
    }
    private val mutationObservers = CopyOnWriteArraySet<Subscriber<in Long>>()
    /** Must be called by subclasses when they mutate (but not just with the passage of time as per the "wall clock"). */
    protected fun notifyMutationObservers() {
        val version = _version.incrementAndGet()
        for (observer in mutationObservers) {
            if (!observer.isUnsubscribed) {
                observer.onNext(version)
            }
        }
    }
}
