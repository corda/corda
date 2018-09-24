package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.net.URI
import java.util.*

internal class CachingErrorDescriptionServiceTest {

    @Test
    fun cache_hit_does_not_cause_a_lookup() {

        var lookupCalled = false
        val code = ErrorCode("1jwqa1d")
        val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode -> locationForCode(errorCode).also { assertThat(errorCode).isEqualTo(code) } }
        val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode, _ -> locationForCode(errorCode).also { lookupCalled = true } }
        val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> = { _, _ -> Mono.empty() }

        val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

        service.descriptionLocationFor(code, InvocationContext.newInstance()).block()

        assertThat(lookupCalled).isFalse()
    }

    @Test
    fun cache_miss_causes_a_lookup() {

        var lookupCalled = false
        val code = ErrorCode("2kawqa1d")
        val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { _ -> Mono.empty() }
        val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode, _ -> locationForCode(errorCode).also { lookupCalled = true }.also { assertThat(errorCode).isEqualTo(code) } }
        val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> = { _, _ -> Mono.empty() }

        val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

        service.descriptionLocationFor(code, InvocationContext.newInstance()).block()

        assertThat(lookupCalled).isTrue()
    }

    @Test
    fun looked_up_value_is_cached() {

        var addToCacheCalled = false
        val errorCode = ErrorCode("1jwqa")
        var lookedUpLocation: ErrorDescriptionLocation? = null

        val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { _ -> just(Optional.empty()) }
        val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> = { code, _ -> locationForCode(code).doOnNext { location -> location.ifPresent { lookedUpLocation = it } } }
        val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> = { code, location ->
            Mono.empty<Unit>().also { addToCacheCalled = true }.also {

                assertThat(code).isEqualTo(errorCode)
                assertThat(location).isEqualTo(lookedUpLocation)
            }
        }

        val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

        service.descriptionLocationFor(errorCode, InvocationContext.newInstance()).block()

        assertThat(addToCacheCalled).isTrue()
    }

    private fun locationForCode(errorCode: ErrorCode, url: String = "https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer", location: ErrorDescriptionLocation? = ErrorDescriptionLocation.External(URI.create(url), errorCode)): Mono<Optional<out ErrorDescriptionLocation>> {

        return if (location != null) {
            just(Optional.of(location))
        } else {
            empty()
        }
    }
}