package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.net.URI
import java.util.*

internal class CachingErrorDescriptionServiceTest {

    @Nested
    internal inner class DescriptionLocationFor {

        // TODO sollecitom add tests for events

        @Test
        fun cache_hit_does_not_cause_a_lookup() {

            var lookupCalled = false
            val code = ErrorCode("1jwqa1d")
            val retrieveCached = { errorCode: ErrorCode -> locationForCode(errorCode).also { assertThat(errorCode).isEqualTo(code) } }
            val lookup = { errorCode: ErrorCode, _: InvocationContext -> locationForCode(errorCode).also { lookupCalled = true } }
            val addToCache = { _: ErrorCode, _: ErrorDescriptionLocation -> empty<Unit>() }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.descriptionLocationFor(code, InvocationContext.newInstance()).block()

            assertThat(lookupCalled).isFalse()
        }

        @Test
        fun cache_miss_causes_a_lookup() {

            var lookupCalled = false
            val code = ErrorCode("2kawqa1d")
            val retrieveCached = { _: ErrorCode -> empty<Optional<out ErrorDescriptionLocation>>() }
            val lookup = { errorCode: ErrorCode, _: InvocationContext -> locationForCode(errorCode).also { lookupCalled = true }.also { assertThat(errorCode).isEqualTo(code) } }
            val addToCache = { _: ErrorCode, _: ErrorDescriptionLocation -> empty<Unit>() }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.descriptionLocationFor(code, InvocationContext.newInstance()).block()

            assertThat(lookupCalled).isTrue()
        }

        @Test
        fun looked_up_value_is_cached() {

            var addToCacheCalled = false
            val errorCode = ErrorCode("1jwqa")
            var lookedUpLocation: ErrorDescriptionLocation? = null

            val retrieveCached = { _: ErrorCode -> just<Optional<out ErrorDescriptionLocation>>(Optional.empty()) }
            val lookup = { code: ErrorCode, _: InvocationContext -> locationForCode(code).doOnNext { location -> location.ifPresent { lookedUpLocation = it } } }
            val addToCache = { code: ErrorCode, location: ErrorDescriptionLocation -> empty<Unit>().also { addToCacheCalled = true }.also { assertThat(code).isEqualTo(errorCode) }.also { assertThat(location).isEqualTo(lookedUpLocation) } }

            val service = CachingErrorDescriptionService(lookup, retrieveCached, addToCache)

            service.descriptionLocationFor(errorCode, InvocationContext.newInstance()).block()

            assertThat(addToCacheCalled).isTrue()
        }
    }

    private fun locationForCode(errorCode: ErrorCode, url: String = "https://stackoverflow.com/questions/3591291/spring-jackson-and-customization-e-g-customdeserializer", location: ErrorDescriptionLocation? = ErrorDescriptionLocation.External(URI.create(url), errorCode)): Mono<Optional<out ErrorDescriptionLocation>> {

        return if (location != null) {
            just(Optional.of(location))
        } else {
            empty()
        }
    }
}