package net.corda.verifier

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.NamedCacheFactory

@Suppress("MagicNumber")
class ExternalVerifierNamedCacheFactory : NamedCacheFactory {
    companion object {
        private const val DEFAULT_CACHE_SIZE = 1024L
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        checkCacheName(name)
        return configure(caffeine, name).build()
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        checkCacheName(name)
        return configure(caffeine, name).build(loader)
    }

    private fun<K, V> configure(caffeine: Caffeine<in K, in V>, name: String): Caffeine<in K, in V> {
        return when (name) {
            "AttachmentsClassLoader_cache" -> caffeine.maximumSize(DEFAULT_CACHE_SIZE / 4)
            "ExternalVerifier_parties" -> caffeine.maximumSize(DEFAULT_CACHE_SIZE)
            "ExternalVerifier_attachments" -> caffeine.maximumSize(DEFAULT_CACHE_SIZE)
            "ExternalVerifier_networkParameters" -> caffeine.maximumSize(DEFAULT_CACHE_SIZE)
            "ExternalVerifier_trustedClassAttachments" -> caffeine.maximumSize(DEFAULT_CACHE_SIZE)
            else -> throw IllegalArgumentException("Unexpected cache name $name. Did you add a new cache?")
        }
    }
}
