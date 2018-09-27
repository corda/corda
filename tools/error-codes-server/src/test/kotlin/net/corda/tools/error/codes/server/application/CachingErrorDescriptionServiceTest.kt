package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.ofType
import java.net.URI

internal class CachingErrorDescriptionServiceTest {

    // TODO sollecitom add tests to cover for "closest" result.

    @Nested
    internal inner class DescriptionLocationFor {

        @Test
        fun cache_hit_does_not_cause_a_lookup() {

            var lookupCalled = false
            val errorCoordinates = ErrorCoordinates(ErrorCode("1jwqa1d"), ReleaseVersion(4, 3, 1), PlatformEdition.OpenSource)
            val invocationContext = InvocationContext.newInstance()
            val descriptions: Flux<out ErrorDescription> = Flux.just(description())

            val retrieveCached = { coordinates: ErrorCoordinates -> locationStub().also { assertThat(coordinates).isEqualTo(errorCoordinates) } }
            val lookup = { _: ErrorCode, _: InvocationContext -> descriptions.also { lookupCalled = true } }
            val addToCache = { _: ErrorCoordinates, _: ErrorDescriptionLocation -> empty<Unit>() }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.use { it.descriptionLocationFor(errorCoordinates, invocationContext).block() }

            assertThat(lookupCalled).isFalse()
        }

        @Test
        fun cache_miss_causes_a_lookup() {

            var lookupCalled = false
            val errorCoordinates = ErrorCoordinates(ErrorCode("2kawqa1d"), ReleaseVersion(4, 3, 1), PlatformEdition.Enterprise)
            val invocationContext = InvocationContext.newInstance()
            val descriptions: Flux<out ErrorDescription> = Flux.just(description())

            val retrieveCached = { _: ErrorCoordinates -> empty<ErrorDescriptionLocation>() }
            val lookup = { code: ErrorCode, _: InvocationContext -> descriptions.also { lookupCalled = true }.also { assertThat(code).isEqualTo(errorCoordinates.code) } }
            val addToCache = { _: ErrorCoordinates, _: ErrorDescriptionLocation -> empty<Unit>() }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.use { it.descriptionLocationFor(errorCoordinates, invocationContext).block() }

            assertThat(lookupCalled).isTrue()
        }

        @Test
        fun looked_up_value_is_cached() {

            var addToCacheCalled = false
            val errorCoordinates = ErrorCoordinates(ErrorCode("1jwqa"), ReleaseVersion(4, 3, 1), PlatformEdition.Enterprise)
            val invocationContext = InvocationContext.newInstance()
            val descriptions: Flux<out ErrorDescription> = Flux.just(description())

            var lookedUpDescription: ErrorDescription? = null
            descriptions.last().doOnNext { lookedUpDescription = it }.subscribe()

            val retrieveCached = { _: ErrorCoordinates -> Mono.empty<ErrorDescriptionLocation>() }

            val lookup = { _: ErrorCode, _: InvocationContext -> descriptions }
            val addToCache = { coordinates: ErrorCoordinates, location: ErrorDescriptionLocation -> empty<Unit>().also { addToCacheCalled = true }.also { assertThat(coordinates).isEqualTo(errorCoordinates) }.also { assertThat(location).isEqualTo(lookedUpDescription?.location) } }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.use { it.descriptionLocationFor(errorCoordinates, invocationContext).block() }

            assertThat(addToCacheCalled).isTrue()
        }

        @Test
        fun unmapped_description_produces_event() {

            val errorCoordinates = ErrorCoordinates(ErrorCode("1jwqa"), ReleaseVersion(4, 3, 1), PlatformEdition.OpenSource)
            val invocationContext = InvocationContext.newInstance()
            val descriptions: Flux<out ErrorDescription> = Flux.empty()

            val retrieveCached = { _: ErrorCoordinates -> empty<ErrorDescriptionLocation>() }
            val lookup = { _: ErrorCode, _: InvocationContext -> descriptions }
            val addToCache = { _: ErrorCoordinates, _: ErrorDescriptionLocation -> empty<Unit>() }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            // Here `single()` transforms a Flux in a Mono, failing if there are 0 or more than 1 events.
            service.events.ofType<ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor.WithoutDescriptionLocation>().single().doOnNext { event ->

                assertThat(event.errorCode).isEqualTo(errorCoordinates.code)
                assertThat(event.invocationContext).isEqualTo(invocationContext)
                assertThat(event.location).isNull()
            }.subscribe()

            service.use { it.descriptionLocationFor(errorCoordinates, invocationContext).block() }
        }
    }

    private fun ErrorDescriptionService.descriptionLocationFor(coordinates: ErrorCoordinates, invocationContext: InvocationContext) = descriptionLocationFor(coordinates.code, coordinates.releaseVersion, coordinates.platformEdition, invocationContext)

    // TODO sollecitom try and get rid of this.
    private fun locationStub(url: String = "https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer", location: ErrorDescriptionLocation? = ErrorDescriptionLocation.External(URI.create(url))): Mono<ErrorDescriptionLocation> {

        return Mono.justOrEmpty(location)
    }

    private fun description(url: String = "https://stackoverflow.com/questions/35", errorCode: ErrorCode = ErrorCode("12hdlsa"), releaseVersion: ReleaseVersion = ReleaseVersion(3, 2, 1), platformEdition: PlatformEdition = PlatformEdition.Enterprise, description: ErrorDescription = ErrorDescription(ErrorDescriptionLocation.External(URI.create(url)), ErrorCoordinates(errorCode, releaseVersion, platformEdition))) = description
}