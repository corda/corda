/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.util

import net.corda.bridge.services.api.ServiceStateSupport
import org.slf4j.Logger
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple implementation of [ServiceStateSupport] service domino logic using RxObservables.
 */
class ServiceStateHelper(val log: Logger) : ServiceStateSupport {
    val lock = ReentrantLock()
    private var _active: Boolean = false
    override var active: Boolean
        get() = lock.withLock { _active }
        set(value) {
            lock.withLock {
                if (value != _active) {
                    _active = value
                    log.info("Status change to $value")
                    _activeChange.onNext(value)
                }
            }
        }

    private val _activeChange: BehaviorSubject<Boolean> = BehaviorSubject.create<Boolean>(false)
    private val _threadSafeObservable: Observable<Boolean> = _activeChange.serialize().distinctUntilChanged()
    override val activeChange: Observable<Boolean>
        get() = _threadSafeObservable
}

/**
 * Simple implementation of [ServiceStateSupport] where it only reports [active] true when a set of dependencies are all [active] true.
 */
class ServiceStateCombiner(val services: List<ServiceStateSupport>) : ServiceStateSupport {
    override val active: Boolean
        get() = services.all { it.active }

    private val _activeChange = Observable.combineLatest(services.map { it.activeChange }, { x -> x.all { y -> y as Boolean } }).serialize().distinctUntilChanged()
    override val activeChange: Observable<Boolean>
        get() = _activeChange
}