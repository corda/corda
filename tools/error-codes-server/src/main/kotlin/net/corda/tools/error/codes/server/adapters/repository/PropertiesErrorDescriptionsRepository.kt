package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.commons.di.FunctionQualifier
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.ErrorDescriptionsRepository
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Mono
import java.net.URI
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@Named
internal class PropertiesErrorDescriptionsRepository : ErrorDescriptionsRepository {

    override operator fun get(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        // TODO sollecitom change to read from a properties file
        if (errorCode.value.length % 2 == 0) {
            return Mono.empty()
        }
        val location = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
        return Mono.just(Optional.of(location))
    }
}

// This allows injecting functions instead of types.
@Named
@FunctionQualifier("Repository")
internal class RetrieveErrorDescription @Inject constructor(private val service: ErrorDescriptionsRepository) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service[errorCode, invocationContext]
}