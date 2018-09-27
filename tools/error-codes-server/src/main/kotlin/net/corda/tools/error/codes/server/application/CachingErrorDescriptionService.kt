package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.application.annotations.Application
import net.corda.tools.error.codes.server.commons.events.PublishingEventSource
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.domain.loggerFor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named

@Application
@Named
internal class CachingErrorDescriptionService @Inject constructor(@Adapter private val lookup: (ErrorCode, InvocationContext) -> Flux<out ErrorDescription>, private val retrieveCached: (ErrorCoordinates) -> Mono<ErrorDescriptionLocation>, private val addToCache: (ErrorCoordinates, ErrorDescriptionLocation) -> Mono<Unit>, @Named(CachingErrorDescriptionService.eventSourceQualifier) override val source: PublishingEventSource<ErrorDescriptionService.Event> = CachingErrorDescriptionService.EventSourceBean()) : ErrorDescriptionService {

    private companion object {

        private const val eventSourceQualifier = "CachingErrorDescriptionService_PublishingEventSource"
        private val logger = loggerFor<CachingErrorDescriptionService>()
    }

    // TODO sollecitom perhaps return a StackOverflow search query URL instead of not found on absent.
    override fun descriptionLocationFor(errorCode: ErrorCode, releaseVersion: ReleaseVersion, platformEdition: PlatformEdition, invocationContext: InvocationContext): Mono<ErrorDescriptionLocation> {

        val coordinates = ErrorCoordinates(errorCode, releaseVersion, platformEdition)
        return coordinates.let(retrieveCached).orIfAbsent { lookupClosestTo(coordinates, invocationContext).doOnNext { addToCache(coordinates, it) } }.thenPublish(coordinates, invocationContext)
    }

    @PreDestroy
    override fun close() {

        source.close()
        logger.info("Closed")
    }

    private fun lookupClosestTo(coordinates: ErrorCoordinates, invocationContext: InvocationContext): Mono<ErrorDescriptionLocation> {

        return lookup(coordinates.code, invocationContext).sort(closestTo(coordinates.releaseVersion).thenComparing(closestTo(coordinates.platformEdition))).take(1).singleOrEmpty().map(ErrorDescription::location)
    }

    private fun closestTo(releaseVersion: ReleaseVersion): Comparator<ErrorDescription> {

        return Comparator { first, second ->

            val firstDistance: ReleaseVersion = first?.coordinates?.releaseVersion?.distanceFrom(releaseVersion) ?: ReleaseVersion(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            val secondDistance: ReleaseVersion = second?.coordinates?.releaseVersion?.distanceFrom(releaseVersion) ?: ReleaseVersion(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            firstDistance.compareTo(secondDistance)
        }
    }

    private fun closestTo(platformEdition: PlatformEdition): Comparator<ErrorDescription> {

        return Comparator { first, second ->

            when {
                first?.coordinates?.platformEdition == second?.coordinates?.platformEdition -> 0
                first?.coordinates?.platformEdition == platformEdition -> -1
                second?.coordinates?.platformEdition == platformEdition -> 1
                else -> 0
            }
        }
    }

    private fun <ELEMENT : Any> Mono<ELEMENT>.orIfAbsent(action: () -> Mono<ELEMENT>): Mono<ELEMENT> {

        return switchIfEmpty(defer(action))
    }

    private fun Mono<ErrorDescriptionLocation>.thenPublish(coordinates: ErrorCoordinates, invocationContext: InvocationContext): Mono<ErrorDescriptionLocation> {

        return doOnSuccess { location: ErrorDescriptionLocation? -> completed(location, coordinates.code, coordinates.releaseVersion, coordinates.platformEdition, invocationContext)?.let(source::publish) }
    }

    private fun completed(location: ErrorDescriptionLocation?, errorCode: ErrorCode, releaseVersion: ReleaseVersion, platformEdition: PlatformEdition, invocationContext: InvocationContext): ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor? {

        return if (location == null) ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor.WithoutDescriptionLocation(errorCode, releaseVersion, platformEdition, invocationContext) else null
    }

    @Named(eventSourceQualifier)
    private class EventSourceBean : PublishingEventSource<ErrorDescriptionService.Event>()
}

// This allows injecting functions instead of types.
@Application
@Named
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, ReleaseVersion, PlatformEdition, InvocationContext) -> Mono<ErrorDescriptionLocation> {

    override fun invoke(errorCode: ErrorCode, releaseVersion: ReleaseVersion, platformEdition: PlatformEdition, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, releaseVersion, platformEdition, invocationContext)
}