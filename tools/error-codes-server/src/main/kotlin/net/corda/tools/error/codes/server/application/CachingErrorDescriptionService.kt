package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Mono
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

@Named
internal class CachingErrorDescriptionService : ErrorDescriptionService {

    override fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<ErrorDescriptionLocation> {

        // TODO sollecitom change
        if (errorCode.value.length % 2 == 0) {
            return Mono.empty()
        }
        return Mono.just(ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode))
    }
}

// This avoids having to inject the entire ErrorDescriptionService inside the endpoint. Kotlin does not support implementing the same interface with different arguments, so this allows a functional style.
@Named
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, InvocationContext) -> Mono<ErrorDescriptionLocation> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, invocationContext)
}