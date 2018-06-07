package net.corda.gradle.jarfilter

import net.corda.gradle.unwanted.*
import net.corda.gradle.jarfilter.asm.*
import net.corda.gradle.jarfilter.matcher.*
import org.gradle.api.logging.Logger
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsNot.not
import org.junit.Assert.*
import org.junit.Test
import kotlin.jvm.kotlin
import kotlin.reflect.full.declaredMemberProperties

class MetaFixValPropertyTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixValPropertyTest::class)
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val intVal = isProperty("intVal", Int::class)
    }

    @Test
    fun testPropertyRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithValProperty, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithValProperty, HasIntVal>()

        // Check that the unwanted property has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.newInstance()
        assertEquals(NUMBER, sourceObj.intVal)
        assertThat("unwantedVal not found", sourceClass.kotlin.declaredMemberProperties, hasItem(unwantedVal))
        assertThat("intVal not found", sourceClass.kotlin.declaredMemberProperties, hasItem(intVal))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithValProperty::class)).toClass<WithValProperty, HasIntVal>()
        val fixedObj = fixedClass.newInstance()
        assertEquals(NUMBER, fixedObj.intVal)
        assertThat("unwantedVal still exists", fixedClass.kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
        assertThat("intVal not found", fixedClass.kotlin.declaredMemberProperties, hasItem(intVal))
    }

    class MetadataTemplate : HasIntVal {
        override val intVal: Int = 0
        @Suppress("UNUSED") val unwantedVal: String = "UNWANTED"
    }
}

class WithValProperty : HasIntVal {
    override val intVal: Int = NUMBER
}
