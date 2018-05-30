package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.*
import net.corda.gradle.jarfilter.matcher.isClass
import org.gradle.api.logging.Logger
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.reflect.jvm.jvmName

/**
 * Kotlin reflection determines the nested classes directly from the java byte-code,
 * and not from the [kotlin.Metadata] annotation. The annotation still contains the
 * list of nested classes, and this task does fix it correctly. However, I can't use
 * Kotlin reflection to test it.
 */
class MetaFixNestedClassTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixNestedClassTest::class)
        private val wantedClass = isClass(WithNestedClass.Wanted::class.jvmName)
        private val unwantedClass = isClass("${WithNestedClass::class.jvmName}\$Unwanted")
    }

    @Test
    fun testNestedClassRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithNestedClass, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithNestedClass, Any>()
        assertThat("Wanted class not found", sourceClass.kotlin.nestedClasses, hasItem(wantedClass))
        //assertThat("Unwanted class not found", sourceClass.kotlin.nestedClasses, hasItem(unwantedClass))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger).toClass<WithNestedClass, Any>()
        assertThat("Wanted class not found", fixedClass.kotlin.nestedClasses, hasItem(wantedClass))
        assertThat("Unwanted class still exists", fixedClass.kotlin.nestedClasses, not(hasItem(unwantedClass)))
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
