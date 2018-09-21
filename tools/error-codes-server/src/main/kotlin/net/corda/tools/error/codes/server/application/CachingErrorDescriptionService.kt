package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.net.URI
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@Named
internal class CachingErrorDescriptionService @Inject constructor(private val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>>, private val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit>) : ErrorDescriptionService {

    override fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        return retrieveCached(errorCode).filter(Optional<*>::isPresent).switchIfEmpty(defer { lookup(errorCode, invocationContext).doOnNext { location -> location.ifPresent { addToCache(errorCode, it) } } })
    }

    // TODO sollecitom push to repository
    private fun lookup(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        // TODO sollecitom change to read from a properties file
        if (errorCode.value.length % 2 == 0) {
            return empty()
        }
        val location = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
        return just(Optional.of(location))
    }
}

// This allows injecting functions instead of types.
@Named
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, invocationContext)
}