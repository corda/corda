package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.inject.Inject
import javax.inject.Named

@Named
internal class UnboundInMemoryErrorLocationCache {

    // TODO sollecitom use a real cache with max number of elements
    private val cache: ConcurrentMap<ErrorCode, ErrorDescriptionLocation> = ConcurrentHashMap()

    operator fun get(errorCode: ErrorCode): Mono<Optional<out ErrorDescriptionLocation>> {

        val cached = Optional.ofNullable(cache[errorCode])
        return just(cached)
    }

    fun put(errorCode: ErrorCode, location: ErrorDescriptionLocation): Mono<Unit> {

        cache[errorCode] = location
        return empty()
    }
}

// This allows injecting functions instead of types.
@Named
internal class GetCachedErrorCode @Inject constructor(private val cache: UnboundInMemoryErrorLocationCache) : (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode) = cache[errorCode]
}

// This allows injecting functions instead of types.
@Named
internal class CacheErrorCode @Inject constructor(private val cache: UnboundInMemoryErrorLocationCache) : (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> {

    override fun invoke(errorCode: ErrorCode, location: ErrorDescriptionLocation) = cache.put(errorCode, location)
}