package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.commons.lifecycle.Startable
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.domain.repository.descriptions.ErrorDescriptionsRepository
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.net.URI
import java.util.*
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named

// TODO sollecitom create an integration test for this class, fetching the bean from Spring context, and testing it through its interface.
@Adapter
@Named
internal class PropertiesErrorDescriptionsRepository @Inject constructor(@Adapter private val loadProperties: () -> Properties) : ErrorDescriptionsRepository, Startable {

    private var properties = Properties()

    @PostConstruct
    override fun start() {

        properties = loadProperties.invoke()
    }

    override operator fun get(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescription>> {

        // TODO sollecitom remove these.
        val releaseVersion = ReleaseVersion(3, 2, 1)
        val platformEdition = PlatformEdition.OpenSource
        return properties.getProperty(errorCode.value)?.let { url -> just<Optional<out ErrorDescription>>(Optional.of(ErrorDescription(ErrorDescriptionLocation.External(URI.create(url)), ErrorCoordinates(errorCode, releaseVersion, platformEdition)))) } ?: empty()
    }
}

// This allows injecting functions instead of types.
@Adapter
@Named
internal class RetrieveErrorDescription @Inject constructor(@Adapter private val service: ErrorDescriptionsRepository) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescription>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service[errorCode, invocationContext]
}
