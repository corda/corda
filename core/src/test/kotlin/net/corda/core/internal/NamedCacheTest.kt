package net.corda.core.internal

import org.junit.Test
import kotlin.test.assertEquals

class NamedCacheTest {

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