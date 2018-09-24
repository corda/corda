package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.commons.di.FunctionQualifier
import net.corda.tools.error.codes.server.commons.events.PublishingEventSource
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.loggerFor
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.defer
import java.util.*
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Named

@Named
internal class CachingErrorDescriptionService @Inject constructor(@FunctionQualifier("Repository") private val lookup: (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>>, private val retrieveCached: (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>>, private val addToCache: (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit>, @Named(CachingErrorDescriptionService.eventSourceQualifier) override val source: PublishingEventSource<ErrorDescriptionService.Event> = CachingErrorDescriptionService.EventSourceBean()) : ErrorDescriptionService {

    private companion object {

        private const val eventSourceQualifier = "CachingErrorDescriptionService_PublishingEventSource"
        private val logger = loggerFor<CachingErrorDescriptionService>()
    }

    override fun descriptionLocationFor(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        return retrieveCached(errorCode).orIfAbsent { lookup(errorCode, invocationContext).andIfPresent { addToCache(errorCode, it) } }.thenPublish(errorCode, invocationContext)
    }

    @PreDestroy
    override fun close() {

        source.close()
        logger.info("Closed")
    }

    private fun <ELEMENT : Any> Mono<Optional<out ELEMENT>>.andIfPresent(action: (ELEMENT) -> Mono<Unit>): Mono<Optional<out ELEMENT>> {

        return doOnNext { result -> result.ifPresent { action(it) } }
    }

    private fun <ELEMENT : Any> Mono<Optional<out ELEMENT>>.orIfAbsent(action: () -> Mono<Optional<out ELEMENT>>): Mono<Optional<out ELEMENT>> {

        return filter(Optional<*>::isPresent).switchIfEmpty(defer(action))
    }

    private fun Mono<Optional<out ErrorDescriptionLocation>>.thenPublish(errorCode: ErrorCode, invocationContext: InvocationContext): Mono<Optional<out ErrorDescriptionLocation>> {

        return doOnSuccess { location -> completed(location?.orElse(null), errorCode, invocationContext)?.let(source::publish) }
    }

    private fun completed(location: ErrorDescriptionLocation?, errorCode: ErrorCode, invocationContext: InvocationContext): ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor? {

        return if (location == null) ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor.WithoutDescriptionLocation(errorCode, invocationContext) else null
    }

    @Named(eventSourceQualifier)
    private class EventSourceBean : PublishingEventSource<ErrorDescriptionService.Event>()
}

// This allows injecting functions instead of types.
@Named
@FunctionQualifier("Service")
internal class ErrorDescriptionLocator @Inject constructor(private val service: ErrorDescriptionService) : (ErrorCode, InvocationContext) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext) = service.descriptionLocationFor(errorCode, invocationContext)
}