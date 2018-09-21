package net.corda.tools.error.codes.server.commons.reactive

import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

fun <ORIGINAL, NEW> Flux<ORIGINAL>.only(type: Class<NEW>): Flux<NEW> {

    return filter(type::isInstance).cast(type)
}

inline fun <reified NEW> Flux<*>.only(): Flux<NEW> = only(NEW::class.java)

fun <ELEMENT : Any> Mono<ELEMENT>.subscribeOptional(onError: (Throwable) -> Unit, action: (ELEMENT?) -> Unit): Disposable {

    var found = false
    val onResult = { result: ELEMENT ->
        found = true
        action.invoke(result)
    }
    val onComplete = {
        if (!found) {
            action.invoke(null)
        }
    }
    return subscribe(onResult, onError, onComplete)
}