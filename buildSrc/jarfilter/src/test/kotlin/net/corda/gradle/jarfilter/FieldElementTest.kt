package net.corda.gradle.jarfilter

import org.junit.Assert.*
import org.junit.Test

class FieldElementTest {
    private companion object {
        private const val DESCRIPTOR = "Ljava.lang.String;"
    }

    @Test
    fun testFieldsMatchByNameOnly() {
        val elt = FieldElement(name = "fieldName", descriptor = DESCRIPTOR)
        assertEquals(FieldElement(name = "fieldName"), elt)
    }

    @Test
    fun testFieldWithDescriptorDoesNotExpire() {
        val elt = FieldElement(name = "fieldName", descriptor = DESCRIPTOR)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
        assertFalse(elt.isExpired)
    }

    @Test
    fun testFieldWithoutDescriptorDoesExpire() {
        val elt = FieldElement(name = "fieldName")
        assertFalse(elt.isExpired)
        assertTrue(elt.isExpired)
        assertTrue(elt.isExpired)
    }
}