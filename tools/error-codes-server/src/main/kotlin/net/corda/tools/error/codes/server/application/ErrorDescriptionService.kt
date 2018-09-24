package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.commons.domain.identity.set
import net.corda.tools.error.codes.server.commons.events.AbstractEvent
import net.corda.tools.error.codes.server.commons.events.EventId
import net.corda.tools.error.codes.server.commons.events.EventPublisher
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import org.apache.commons.lang3.builder.ToStringBuilder
import reactor.core.publisher.Mono
import java.util.*

interface ErrorDescriptionService : EventPublisher<ErrorDescriptionService.Event>, AutoCloseable {

    fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>>

    sealed class Event(id: EventId = EventId.newInstance()) : AbstractEvent(id) {

        sealed class Invocation(val invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event(id) {

            override fun appendToStringElements(toString: ToStringBuilder) {

                super.appendToStringElements(toString)
                toString["invocationContext"] = invocationContext.description
            }

            sealed class Completed(invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation(invocationContext, id) {

                sealed class DescriptionLocationFor(val errorCode: ErrorCode, val location: ErrorDescriptionLocation?, invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation.Completed(invocationContext, id) {

                    class WithoutDescriptionLocation(errorCode: ErrorCode, invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor(errorCode, null, invocationContext, id)

                    override fun appendToStringElements(toString: ToStringBuilder) {

                        super.appendToStringElements(toString)
                        toString["errorCode"] = errorCode.value
                        toString["location"] = when (location) {
                            is ErrorDescriptionLocation.External -> location.uri
                            null -> "<null>"
                        }
                    }
                }
            }
        }
    }
}