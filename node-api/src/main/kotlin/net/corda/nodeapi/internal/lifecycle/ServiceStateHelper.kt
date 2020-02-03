package net.corda.nodeapi.internal.lifecycle

import org.slf4j.Logger
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple implementation of [ServiceStateSupport] service domino logic using RxObservables.
 */
class ServiceStateHelper(private val log: Logger, private val serviceName: String = log.name.split(".").last()) : ServiceStateSupport {
    private val lock = ReentrantLock()

    // Volatile to prevent deadlocks when locking on read.
    @Volatile
    private var _active: Boolean = false
    override var active: Boolean
        get() = _active
        set(value) {
            lock.withLock {
                if (value != _active) {
                    _active = value
                    log.info("Status change to $value")
                    LifecycleStatusHelper.setServiceStatus(serviceName, value)
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