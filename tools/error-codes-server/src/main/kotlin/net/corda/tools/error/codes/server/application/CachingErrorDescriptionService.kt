package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Mono
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.inject.Inject
import javax.inject.Named

@Named
internal class CachingErrorDescriptionService : ErrorDescriptionService {

    // TODO sollecitom inject a cache instead - or better, 2 functions
    private val cache: ConcurrentMap<ErrorCode, ErrorDescriptionLocation> = ConcurrentHashMap()

    override fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        return cached(errorCode).filter(Optional<*>::isPresent).switchIfEmpty(Mono.defer { lookup(errorCode, invocationContext).doOnNext { location -> location.ifPresent { cache(errorCode, it) } } })
    }

    private fun cached(errorCode: ErrorCode): Mono<Optional<out ErrorDescriptionLocation>> {

        val cached = Optional.ofNullable(cache[errorCode])
        return Mono.just(cached)
    }

    private fun cache(errorCode: ErrorCode, location: ErrorDescriptionLocation): Mono<Unit> {

        cache[errorCode] = location
        return Mono.empty()
    }

    // TODO sollecitom push to repository
    private fun lookup(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        if (errorCode.value.length % 2 == 0) {
            return Mono.empty()
        }
        val location = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
        return Mono.just(Optional.of(location))
    }
}

// This avoids having to inject the entire ErrorDescriptionService inside the endpoint. Kotlin does not support implementing the same interface with different arguments, so this allows a functional style.
@Named
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, invocationContext)
}