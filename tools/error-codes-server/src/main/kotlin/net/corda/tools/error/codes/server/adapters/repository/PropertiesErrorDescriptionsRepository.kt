package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.commons.lifecycle.Startable
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.domain.repository.descriptions.ErrorDescriptionsRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.toFlux
import java.net.URI
import java.util.*
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Named

@Adapter
@Named
internal class PropertiesErrorDescriptionsRepository @Inject constructor(@Adapter private val loadProperties: () -> Properties) : ErrorDescriptionsRepository, Startable {

    private companion object {

        private const val DESCRIPTIONS_SEPARATOR = "|"
        private const val DESCRIPTION_PARTS_SEPARATOR = ","
    }

    private var properties = Properties()

    @PostConstruct
    override fun start() {

        properties = loadProperties.invoke()
    }

    override operator fun get(errorCode: ErrorCode, invocationContext: InvocationContext): Flux<out ErrorDescription> {

        return properties.getProperty(errorCode.value)?.split(DESCRIPTIONS_SEPARATOR)?.toFlux()?.filter(String::isNotBlank)?.map { parseDescription(it, errorCode) } ?: empty()
    }

    private fun parseDescription(rawValue: String, errorCode: ErrorCode): ErrorDescription {

        // Here we can trust the file format to be correct.
        val parts = rawValue.split(DESCRIPTION_PARTS_SEPARATOR)
        val location = ErrorDescriptionLocation.External(URI.create(parts[0]))
        val platformEdition = parts[1].let { if (it == "OS") PlatformEdition.OpenSource else PlatformEdition.Enterprise }
        val releaseVersion = parts[2].split(ReleaseVersion.SEPARATOR).map(String::toInt).let { releaseNumbers -> ReleaseVersion(releaseNumbers[0], releaseNumbers[1], releaseNumbers[2]) }
        val coordinates = ErrorCoordinates(errorCode, releaseVersion, platformEdition)
        return ErrorDescription(location, coordinates)
    }
}

// This allows injecting functions instead of types.
@Adapter
@Named
internal class RetrieveErrorDescription @Inject constructor(@Adapter private val service: ErrorDescriptionsRepository) : (ErrorCode, InvocationContext) -> Flux<out ErrorDescription> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service[errorCode, invocationContext]
}
