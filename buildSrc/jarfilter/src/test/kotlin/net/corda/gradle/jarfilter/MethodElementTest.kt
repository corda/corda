package net.corda.gradle.jarfilter

import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.Opcodes.*

class MethodElementTest {
    private companion object {
        private const val DESCRIPTOR = "()Ljava.lang.String;"
    }

    @Test
    fun testMethodsMatchByNameAndDescriptor() {
        val elt = MethodElement(
            name = "getThing",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC or ACC_ABSTRACT or ACC_FINAL
        )
        assertEquals(MethodElement(name="getThing", descriptor=DESCRIPTOR), elt)
        assertNotEquals(MethodElement(name="getOther", descriptor=DESCRIPTOR), elt)
        assertNotEquals(MethodElement(name="getThing", descriptor="()J"), elt)
    }

    @Test
    fun testBasicMethodVisibleName() {
        val elt = MethodElement(
            name = "getThing",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertEquals("getThing", elt.visibleName)
    }

    @Test
    fun testMethodVisibleNameWithSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertEquals("getThing", elt.visibleName)
    }

    @Test
    fun testSyntheticMethodSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC or ACC_SYNTHETIC
        )
        assertTrue(elt.isKotlinSynthetic("extra"))
        assertFalse(elt.isKotlinSynthetic("something"))
        assertTrue(elt.isKotlinSynthetic("extra", "something"))
    }

    @Test
    fun testPublicMethodSuffix() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertFalse(elt.isKotlinSynthetic("extra"))
    }

    @Test
    fun testMethodDoesNotExpire() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR,
            access = ACC_PUBLIC
        )
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
    }

    @Test
    fun testArtificialMethodDoesExpire() {
        val elt = MethodElement(
            name = "getThing\$extra",
            descriptor = DESCRIPTOR
        )
        assertFalse(elt.isExpired)
        assertTrue(elt.isExpired)
        assertTrue(elt.isExpired)
    }
}