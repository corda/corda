package net.corda.nodeapi.internal.rpc.client

import com.github.benmanes.caffeine.cache.Cache
import net.corda.core.context.Trace
import rx.Notification
import rx.Observable
import rx.subjects.UnicastSubject
import java.util.concurrent.ConcurrentHashMap

/** A throwable that doesn't represent a real error - it's just here to wrap a stack trace. */
class CallSite(val rpcName: String) : Throwable("<Call site of root RPC '$rpcName'>")

typealias RpcObservableMap = Cache<Trace.InvocationId, UnicastSubject<Notification<*>>>
typealias CallSiteMap = ConcurrentHashMap<Trace.InvocationId, CallSite?>

/**
 * Holds a context available during de-serialisation of messages that are expected to contain Observables.
 *
 * @property observableMap holds the Observables that are ultimately exposed to the user.
 * @property hardReferenceStore holds references to Observables we want to keep alive while they are subscribed to.
 * @property callSiteMap keeps stack traces captured when an RPC was invoked, useful for debugging when an observable leaks.
 */
data class ObservableContext(
        val callSiteMap: CallSiteMap?,
        val observableMap: RpcObservableMap,
        val hardReferenceStore: MutableSet<Observable<*>>
)
