package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.metadataAs
import net.corda.gradle.jarfilter.asm.toClass
import org.gradle.api.logging.Logger
import org.junit.BeforeClass
import org.junit.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertFailsWith

/**
 * These tests cannot actually "test" anything until Kotlin reflection
 * supports package metadata. Until then, we can only execute the code
 * paths to ensure they don't throw any exceptions.
 */
class MetaFixPackageDefaultParameterTest {
    companion object {
        private const val TEMPLATE_CLASS = "net.corda.gradle.jarfilter.template.PackageWithDefaultParameters"
        private const val DEFAULT_PARAMETERS_CLASS = "net.corda.gradle.jarfilter.PackageWithDefaultParameters"
        private val logger: Logger = StdOutLogging(MetaFixPackageDefaultParameterTest::class)

        lateinit var sourceClass: Class<out Any>
        lateinit var fixedClass: Class<out Any>

        @BeforeClass
        @JvmStatic
        fun setup() {
            val defaultParametersClass = Class.forName(DEFAULT_PARAMETERS_CLASS)
            val bytecode = defaultParametersClass.metadataAs(Class.forName(TEMPLATE_CLASS))
            sourceClass = bytecode.toClass(defaultParametersClass, Any::class.java)
            fixedClass = bytecode.fixMetadata(logger, setOf(DEFAULT_PARAMETERS_CLASS)).toClass(sourceClass, Any::class.java)
        }
    }

    @Test
    fun `test package functions`() {
        assertFailsWith<UnsupportedOperationException> { fixedClass.kotlin.declaredFunctions }
    }
}
