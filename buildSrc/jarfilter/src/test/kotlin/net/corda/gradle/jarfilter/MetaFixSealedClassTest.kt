package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.*
import org.assertj.core.api.Assertions.*
import org.gradle.api.logging.Logger
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class MetaFixSealedClassTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixSealedClassTest::class)
        private val UNWANTED_CLASS: String = "${MetaSealedClass::class.jvmName}\$Unwanted"
        private val WANTED_CLASS: String = MetaSealedClass.Wanted::class.jvmName
    }

    @Test
    fun testSealedSubclassRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<MetaSealedClass, MetadataTemplate>()
        val sourceClass = bytecode.toClass<MetaSealedClass, Any>()
        assertThat(sourceClass.classMetadata.sealedSubclasses).containsExactlyInAnyOrder(UNWANTED_CLASS, WANTED_CLASS)

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger, pathsOf(MetaSealedClass::class, MetaSealedClass.Wanted::class))
                .toClass<MetaSealedClass, Any>()
        assertThat(fixedClass.classMetadata.sealedSubclasses).containsExactly(WANTED_CLASS)
    }

    @Suppress("UNUSED")
    sealed class MetadataTemplate {
        class Wanted : MetadataTemplate()
        class Unwanted : MetadataTemplate()
    }
}

sealed class MetaSealedClass {
    class Wanted : MetaSealedClass()
}
