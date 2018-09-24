package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.commons.di.FunctionQualifier
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import java.util.*
import javax.inject.Inject
import javax.inject.Named

// TODO sollecitom make internal again and check wht Intellij complains in tests if not.
@Named
class CachingErrorDescriptionService @Inject constructor(@FunctionQualifier("Repository") private val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>>, private val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>>, private val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit>) : ErrorDescriptionService {

    override fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        return retrieveCached(errorCode).orIfAbsent { lookup(errorCode, invocationContext).andIfPresent { addToCache(errorCode, it) } }
    }

    private fun <ELEMENT : Any> Mono<Optional<out ELEMENT>>.andIfPresent(action: (ELEMENT) -> Mono<Unit>): Mono<Optional<out ELEMENT>> {

        return doOnNext { result -> result.ifPresent { action(it) } }
    }

    private fun <ELEMENT : Any> Mono<Optional<out ELEMENT>>.orIfAbsent(action: () -> Mono<Optional<out ELEMENT>>): Mono<Optional<out ELEMENT>> {

        return filter(Optional<*>::isPresent).switchIfEmpty(defer(action))
    }
}

// This allows injecting functions instead of types.
@Named
@FunctionQualifier("Service")
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, invocationContext)
}