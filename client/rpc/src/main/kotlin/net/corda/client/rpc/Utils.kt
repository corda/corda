package net.corda.client.rpc

import rx.Observable

/**
 * This function should be invoked on any unwanted Observables returned from RPC to release the server resources.
 *
 * subscribe({}, {}) was used instead of simply calling subscribe()
 * because if an {@code onError} emission arrives (eg. due to an non-correct transaction, such as 'Not sufficient funds')
 * then {@link OnErrorNotImplementedException} is thrown. As we won't handle exceptions from unused Observables,
 * empty inputs are used to subscribe({}, {}).
 */
fun <T> Observable<T>.notUsed() {
    try {
        this.subscribe({}, {}).unsubscribe()
    } catch (e: Exception) {
        // Swallow any other exceptions as well.
    }
}
