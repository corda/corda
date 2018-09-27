package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.Flux.just
import reactor.core.publisher.Mono
import java.net.URI

internal interface ErrorDescriptionsRepositoryTestSpecification {

    val repository: PropertiesErrorDescriptionsRepository

    fun prepareDownstream(vararg values: Pair<ErrorCode, Flux<out ErrorDescription>>)

    @Test
    @DirtiesContext
    fun empty_file_results_in_no_descriptions() {

        val errorCode = ErrorCode("1uoda")
        val invocationContext = InvocationContext.newInstance()
        val descriptions: Flux<out ErrorDescription> = empty()

        prepareDownstream(errorCode to descriptions)
        repository.start()

        val returned = repository[errorCode, invocationContext]

        assertThat(returned.collectList().switchIfEmpty(Mono.empty()).block()).isEqualTo(descriptions.collectList().block())
    }

    @Test
    @DirtiesContext
    fun file_with_one_line_results_in_one_description() {

        val errorCode = ErrorCode("1uoda")
        val invocationContext = InvocationContext.newInstance()

        val description = ErrorDescription(ErrorDescriptionLocation.External(URI.create("https//a.com")), ErrorCoordinates(errorCode, ReleaseVersion(3, 2, 1), PlatformEdition.Enterprise))
        val descriptions: Flux<out ErrorDescription> = just(description)

        prepareDownstream(errorCode to descriptions)
        repository.start()

        val returned = repository[errorCode, invocationContext]

        assertThat(returned.blockFirst()).isEqualTo(description)
    }
}