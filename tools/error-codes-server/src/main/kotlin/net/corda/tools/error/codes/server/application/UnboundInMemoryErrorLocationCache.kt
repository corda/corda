package net.corda.tools.error.codes.server.application

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import net.corda.tools.error.codes.server.application.annotations.Application
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import reactor.core.publisher.Mono.just
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@Application
@Named
internal class UnboundInMemoryErrorLocationCache @Inject constructor(configuration: UnboundInMemoryErrorLocationCache.Configuration) {

    private val cache: Cache<ErrorCode, ErrorDescriptionLocation> = Caffeine.newBuilder().maximumSize(configuration.cacheMaximumSize.value).build<ErrorCode, ErrorDescriptionLocation>()

    operator fun get(errorCode: ErrorCode): Mono<Optional<out ErrorDescriptionLocation>> {

        return just(cache[errorCode])
    }

    fun put(errorCode: ErrorCode, location: ErrorDescriptionLocation): Mono<Unit> {

        cache[errorCode] = location
        return empty()
    }

    private operator fun <KEY : Any, VALUE> Cache<KEY, VALUE>.get(key: KEY): Optional<VALUE> {

        return Optional.ofNullable(getIfPresent(key))
    }

    private operator fun <KEY : Any, VALUE> Cache<KEY, VALUE>.set(key: KEY, value: VALUE) {

        return put(key, value)
    }

    interface Configuration {

        val cacheMaximumSize: CacheSize
    }
}

// This allows injecting functions instead of types.
@Application
@Named
internal class GetCachedErrorCode @Inject constructor(private val cache: UnboundInMemoryErrorLocationCache) : (ErrorCode) -> Mono<Optional<out ErrorDescriptionLocation>> {

    override fun invoke(errorCode: ErrorCode) = cache[errorCode]
}

// This allows injecting functions instead of types.
@Application
@Named
internal class CacheErrorCode @Inject constructor(private val cache: UnboundInMemoryErrorLocationCache) : (ErrorCode, ErrorDescriptionLocation) -> Mono<Unit> {

    override fun invoke(errorCode: ErrorCode, location: ErrorDescriptionLocation) = cache.put(errorCode, location)
}

@Application
@Named
internal class UnboundInMemoryErrorLocationCacheConfiguration @Inject constructor(applyConfigStandards: (Config) -> Config) : UnboundInMemoryErrorLocationCache.Configuration {

    private companion object {

        private const val CONFIGURATION_SECTION_PATH = "configuration.application.service.cache"

        private object Spec : ConfigSpec(CONFIGURATION_SECTION_PATH) {

            val max_size by required<Long>()
        }
    }

    private val config = applyConfigStandards.invoke(Config { addSpec(Spec) })

    override val cacheMaximumSize: CacheSize by lazy { CacheSize(config[Spec.max_size]) }
}