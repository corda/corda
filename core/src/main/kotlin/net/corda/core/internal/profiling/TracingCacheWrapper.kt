package net.corda.core.internal.profiling

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.utilities.seconds
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function
import kotlin.concurrent.thread

class CacheTracing {
    private class TraceCollector<K>(private val fileName: String, private val converter: (key: K) -> Long) {
        @Volatile
        private var running = true
        private val queue = ConcurrentLinkedQueue<Long>()
        private val writeThread = thread(start = true, name = "TraceWriter-$fileName", isDaemon = true) { writeFunc() }

        fun collectKeys(keys: Iterable<K>) {
            if (!running) {
                return
            }
            keys.forEach { queue.add(converter(it)) }
        }

        fun shutdown() {
            running = false
            writeThread.join(10.seconds.toMillis())
        }

        private fun writeFunc() {
            val file = File(fileName)
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            FileOutputStream(fileName, true).use {
                val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
                var firstRun = true // make sure the loop runs at least once (in case of very short lived process where the thread might be started after shutdown is initiated.
                while (running || firstRun) {
                    Thread.sleep(100) // sleep first, then check for work (so that work arriving during sleep does not get lost in shutdown)
                    var item: Long? = null
                    while ({ item = queue.poll(); item }() != null) {
                        buffer.putLong(item!!)
                        it.write(buffer.array())
                        buffer.clear()
                    }
                }
            }
        }
    }

    private open class TracingCacheWrapper<K, V>(protected val cache: Cache<K, V>, protected val collector: TraceCollector<K>) : Cache<K, V> by cache {
        override fun put(key: K, value: V) {
            collector.collectKeys(listOf(key))
            cache.put(key, value)
        }

        override fun putAll(map: MutableMap<out K, out V>) {
            collector.collectKeys(map.keys)
            cache.putAll(map)
        }

        override fun get(key: K, mappingFunction: Function<in K, out V>): V? {
            collector.collectKeys(listOf(key))
            return cache.get(key, mappingFunction)
        }

        override fun getIfPresent(key: Any): V? {
            @Suppress("UNCHECKED_CAST") // need to suppress this warning - no way to check against an erased type
            collector.collectKeys(listOf(key as K))
            return cache.getIfPresent(key)
        }
    }

    private class TracingLoadingCacheWrapper<K, V>(val loadingCache: LoadingCache<K, V>, collector: TraceCollector<K>) : LoadingCache<K, V>, TracingCacheWrapper<K, V>(loadingCache, collector) {
        override fun getAll(keys: MutableIterable<K>): MutableMap<K, V> {
            collector.collectKeys(keys)
            return loadingCache.getAll(keys)
        }

        override fun refresh(key: K) {
            collector.collectKeys(listOf(key))
            loadingCache.refresh(key)
        }

        override fun get(key: K): V? {
            collector.collectKeys(listOf(key))
            return loadingCache.get(key)
        }
    }

    data class CacheTracingConfig(val enabled: Boolean, val targetDir: Path, val converter: (key: Any?) -> Long)

    companion object {
        var cacheTracingConfig: CacheTracingConfig? = null

        fun <K, V> wrap(cache: Cache<K, V>, name: String): Cache<K, V> {
            return wrap(cache, cacheTracingConfig, name)
        }

        fun <K, V> wrap(cache: LoadingCache<K, V>, name: String): LoadingCache<K, V> {
            return wrap(cache, cacheTracingConfig, name)
        }

        fun <K, V> wrap(cache: Cache<K, V>, config: CacheTracingConfig?, traceName: String): Cache<K, V> {
            return if (config != null && config.enabled) TracingCacheWrapper(cache, getCollector(config.targetDir, traceName, config.converter)) else cache
        }

        fun <K, V> wrap(cache: LoadingCache<K, V>, config: CacheTracingConfig?, traceName: String): LoadingCache<K, V> {
            return if (config != null && config.enabled) TracingLoadingCacheWrapper(cache, getCollector(config.targetDir, traceName, config.converter)) else cache
        }

        private val collectors = ConcurrentHashMap<String, TraceCollector<*>>()

        @Synchronized
        private fun <K> getCollector(targetDir: Path, traceName: String, converter: (key: Any?) -> Long): TraceCollector<K> {
            if (collectors.isEmpty()) {
                val hook = Thread { shutdown() }
                val runtime = Runtime.getRuntime()
                runtime.addShutdownHook(hook)
            }
            val fileName = targetDir.resolve("trace_$traceName.bin").toAbsolutePath().toString()
            @Suppress("UNCHECKED_CAST") // need to suppress this warning - no way to check against an erased type
            return collectors.computeIfAbsent(fileName) { TraceCollector(it, converter) } as TraceCollector<K>
        }

        fun shutdown() {
            collectors.values.forEach { it.shutdown() }
            collectors.clear()
        }
    }
}