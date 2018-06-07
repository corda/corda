package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.*
import org.assertj.core.api.Assertions.*
import org.gradle.api.logging.Logger
import org.junit.Test
import kotlin.reflect.jvm.jvmName

/**
 * Kotlin reflection will attempt to validate the nested classes stored in the [kotlin.Metadata]
 * annotation rather than just reporting what is there, which means that it can tell us nothing
 * about what the MetaFixer task has done.
 */
class MetaFixNestedClassTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixNestedClassTest::class)
        private val WANTED_CLASS: String = WithNestedClass.Wanted::class.jvmName
        private val UNWANTED_CLASS: String = "${WithNestedClass::class.jvmName}\$Unwanted"
    }

    @Test
    fun testNestedClassRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithNestedClass, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithNestedClass, Any>()
        assertThat(sourceClass.classMetadata.nestedClasses).containsExactlyInAnyOrder(WANTED_CLASS, UNWANTED_CLASS)

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(WithNestedClass::class, WithNestedClass.Wanted::class))
                .toClass<WithNestedClass, Any>()
        assertThat(fixedClass.classMetadata.nestedClasses).containsExactly(WANTED_CLASS)
    }

    @Suppress("UNUSED")
    class MetadataTemplate {
        class Wanted
        class Unwanted
    }
}

class WithNestedClass {
    class Wanted
}
