package net.corda.core.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.junit.Test
import kotlin.test.assertEquals

class NamedCacheTest : NamedCacheFactory {
    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String): Cache<K, V> {
        throw IllegalStateException("Should not be called")
    }

    override fun <K, V> buildNamed(caffeine: Caffeine<in K, in V>, name: String, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        throw IllegalStateException("Should not be called")
    }

    fun checkNameHelper(name: String, throws: Boolean) {
        var exceptionThrown = false
        try {
            checkCacheName(name)
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertEquals(throws, exceptionThrown)
    }

    @Test
    fun TestCheckCacheName() {
        checkNameHelper("abc_123.234", false)
        checkNameHelper("", true)
        checkNameHelper("abc 123", true)
        checkNameHelper("abc/323", true)
    }
}