package net.corda.tools.error.codes.server.domain.repository.descriptions

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.InvocationContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ErrorDescriptionsRepository {

    operator fun get(errorCode: ErrorCode, invocationContext: InvocationContext): Flux<out ErrorDescription>
}