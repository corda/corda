package net.corda.gradle.jarfilter

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UnwantedCacheTest {
    private companion object {
        private const val CLASS_NAME = "org.testing.MyClass"
        private const val LONG_ARG = "(J)V"
        private const val NO_ARG = "()V"
    }

    private lateinit var cache: UnwantedCache

    @Before
    fun setup() {
        cache = UnwantedCache()
    }

    @Test
    fun testEmptyCache() {
        assertFalse(cache.containsClass(CLASS_NAME))
        assertFalse(cache.containsMethod(CLASS_NAME, null, null))
        assertFalse(cache.containsMethod(CLASS_NAME, "<init>", NO_ARG))
    }

    @Test
    fun testAddingClass() {
        cache.addClass(CLASS_NAME)
        assertTrue(cache.containsClass(CLASS_NAME))
        assertTrue(cache.containsMethod(CLASS_NAME, null, null))
        assertTrue(cache.containsMethod(CLASS_NAME, "<init>", NO_ARG))
    }

    @Test
    fun testAddingMethod() {
        cache.addMethod(CLASS_NAME, MethodElement("<init>", LONG_ARG))
        assertTrue(cache.containsMethod(CLASS_NAME, "<init>", LONG_ARG))
        assertFalse(cache.containsMethod(CLASS_NAME, "<init>", NO_ARG))
        assertFalse(cache.containsMethod(CLASS_NAME, "destroy", LONG_ARG))
        assertFalse(cache.containsMethod(CLASS_NAME, null, null))
        assertFalse(cache.containsMethod(CLASS_NAME, "nonsense", null))
        assertFalse(cache.containsClass(CLASS_NAME))
    }

    @Test
    fun testAddingMethodFollowedByClass() {
        cache.addMethod(CLASS_NAME, MethodElement("<init>", LONG_ARG))
        cache.addClass(CLASS_NAME)
        assertTrue(cache.containsMethod(CLASS_NAME, "<init>", LONG_ARG))
        assertEquals(0, cache.classMethods.size)
    }
}