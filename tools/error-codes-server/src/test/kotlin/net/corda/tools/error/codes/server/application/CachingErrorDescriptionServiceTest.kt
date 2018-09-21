package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.just
import java.net.URI
import java.util.*

internal class CachingErrorDescriptionServiceTest {

    // TODO sollecitom add assertion to check params are passed correctly
    @Test
    fun cache_hit_does_not_cause_a_lookup() {

        var lookupCalled = false
        val locationForCode: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode: ErrorCode ->
            val location = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
            just(Optional.of(location))
        }
        val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode -> locationForCode(errorCode) }
        val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode, _ -> locationForCode(errorCode).also { lookupCalled = true } }
        val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> = { _, _ -> Mono.empty() }

        val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

        service.descriptionLocationFor(ErrorCode("1aoj"), InvocationContext.newInstance()).block()

        assertThat(lookupCalled).isFalse()
    }

    @Test
    fun looked_up_value_is_cached() {

        var addToCacheCalled = false
        val locationForCode: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> = { errorCode: ErrorCode ->
            val location = ErrorDescriptionLocation.External(URI.create("https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer"), errorCode)
            just(Optional.of(location))
        }
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
}