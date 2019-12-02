package net.corda.core.internal.profiling

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.primitives.Longs
import net.corda.core.internal.profiling.CacheTracing.Companion.wrap
import org.junit.Test
import java.io.FileInputStream
import kotlin.test.assertEquals

class CacheTracingTest {
    @Test
    fun testEverythingGetsCaptured() {
        val tempDir = createTempDir()
        val cache = Caffeine.newBuilder().maximumSize(10).build<Long, Long>()

        val wrappedCache = wrap(cache, CacheTracing.CacheTracingConfig(true, tempDir.toPath(), { key: Any? -> key as Long }), "test")

        wrappedCache.put(1L, 1L)
        wrappedCache.putAll(mutableMapOf(2L to 2L, 3L to 3L))
        wrappedCache.get(4L) { it }
        wrappedCache.getIfPresent(5L)

        CacheTracing.shutdown()

        val fileName = tempDir.toPath().resolve("trace_test.bin")
        val inStream = FileInputStream(fileName.toFile())
        checkSequence(listOf(1L, 2L, 3L, 4L, 5L), inStream)
    }

    @Test
    fun testEverythingGetsCapturedInDirectoryToBeCreated() {
        val tempDir = createTempDir()
        val cache = Caffeine.newBuilder().maximumSize(10).build<Long, Long>()

        val wrappedCache = wrap(cache, CacheTracing.CacheTracingConfig(true, tempDir.toPath().resolve("foo/bar"), { key: Any? -> key as Long }), "test")

        wrappedCache.put(1L, 1L)
        wrappedCache.putAll(mutableMapOf(2L to 2L, 3L to 3L))
        wrappedCache.get(4L) { it }
        wrappedCache.getIfPresent(5L)

        CacheTracing.shutdown()

        val fileName = tempDir.toPath().resolve("foo/bar/trace_test.bin")
        val inStream = FileInputStream(fileName.toFile())
        checkSequence(listOf(1L, 2L, 3L, 4L, 5L), inStream)
    }


    @Test
    fun testStopsWorkingAfterShutdown() {
        val tempDir = createTempDir()
        val cache = Caffeine.newBuilder().maximumSize(10).build<Long, Long>()

        val wrappedCache = wrap(cache, CacheTracing.CacheTracingConfig(true, tempDir.toPath(), { key: Any? -> key as Long }), "test")

        wrappedCache.put(1L, 1L)
        CacheTracing.shutdown()

        wrappedCache.putAll(mutableMapOf(2L to 2L, 3L to 3L))
        CacheTracing.shutdown()

        val fileName = tempDir.toPath().resolve("trace_test.bin")
        val inStream = FileInputStream(fileName.toFile())
        checkSequence(listOf(1L), inStream)
    }


    @Test
    fun testEverythingGetsCapturedLoadingCache() {
        val tempDir = createTempDir()
        val cache = Caffeine.newBuilder().maximumSize(10).build<Long, Long> { it }

        val wrappedCache = wrap(cache, CacheTracing.CacheTracingConfig(true, tempDir.toPath(), { key: Any? -> key as Long }), "test")

        wrappedCache.put(1L, 1L)
        wrappedCache.putAll(mutableMapOf(2L to 2L, 3L to 3L))
        wrappedCache.get(4L)
        wrappedCache.getIfPresent(5L)
        wrappedCache.getAll(listOf(1L, 3L))
        wrappedCache.refresh(3L)

        CacheTracing.shutdown()

        val fileName = tempDir.toPath().resolve("trace_test.bin")
        val inStream = FileInputStream(fileName.toFile())
        checkSequence(listOf(1L, 2L, 3L, 4L, 5L, 1L, 3L, 3L), inStream)
    }

    private fun checkSequence(expected: Iterable<Long>, stream: FileInputStream) {
        val bytes = ByteArray(8)

        expected.forEachIndexed { ind: Int, exp: Long ->
            assertEquals(8, stream.read(bytes))
            val actual = Longs.fromByteArray(bytes)
            assertEquals(exp, actual, "Expected $exp, got $actual at positions $ind")
        }
        assertEquals(-1, stream.read(bytes))
    }
}