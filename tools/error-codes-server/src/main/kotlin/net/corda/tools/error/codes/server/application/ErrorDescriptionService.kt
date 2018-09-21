package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import reactor.core.publisher.Mono
import java.util.*

interface ErrorDescriptionService {

    fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>>
}