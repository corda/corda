package net.corda.tools.error.codes.server.domain

import reactor.core.publisher.Mono
import java.util.*

interface ErrorDescriptionsRepository {

    operator fun get(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>>
}