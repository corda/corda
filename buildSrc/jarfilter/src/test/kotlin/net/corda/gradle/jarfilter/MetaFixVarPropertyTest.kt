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

class MetaFixVarPropertyTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixVarPropertyTest::class)
        private val unwantedVar = isProperty("unwantedVar", String::class)
        private val intVar = isProperty("intVar", Int::class)
    }

    @Test
    fun testPropertyRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithVarProperty, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithVarProperty, HasIntVar>()

        // Check that the unwanted property has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.newInstance()
        assertEquals(NUMBER, sourceObj.intVar)
        assertThat("unwantedVar not found", sourceClass.kotlin.declaredMemberProperties, hasItem(unwantedVar))
        assertThat("intVar not found", sourceClass.kotlin.declaredMemberProperties, hasItem(intVar))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithVarProperty::class)).toClass<WithVarProperty, HasIntVar>()
        val fixedObj = fixedClass.newInstance()
        assertEquals(NUMBER, fixedObj.intVar)
        assertThat("unwantedVar still exists", fixedClass.kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
        assertThat("intVar not found", fixedClass.kotlin.declaredMemberProperties, hasItem(intVar))
    }

    class MetadataTemplate : HasIntVar {
        override var intVar: Int = 0
        @Suppress("UNUSED") var unwantedVar: String = "UNWANTED"
    }
}

class WithVarProperty : HasIntVar {
    override var intVar: Int = NUMBER
}
