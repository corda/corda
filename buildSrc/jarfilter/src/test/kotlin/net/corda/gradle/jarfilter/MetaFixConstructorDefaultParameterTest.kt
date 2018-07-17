package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.asm.recodeMetadataFor
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isConstructor
import net.corda.gradle.unwanted.HasAll
import org.assertj.core.api.Assertions.*
import org.gradle.api.logging.Logger
import org.hamcrest.core.IsCollectionContaining.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import kotlin.reflect.full.primaryConstructor

class MetaFixConstructorDefaultParameterTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixConstructorDefaultParameterTest::class)
        private val primaryCon
                = isConstructor(WithConstructorParameters::class, Long::class, Int::class, String::class)
        private val secondaryCon
                = isConstructor(WithConstructorParameters::class, Char::class, String::class)

        lateinit var sourceClass: Class<out HasAll>
        lateinit var fixedClass: Class<out HasAll>

        @BeforeClass
        @JvmStatic
        fun setup() {
            val bytecode = recodeMetadataFor<WithConstructorParameters, MetadataTemplate>()
            sourceClass = bytecode.toClass<WithConstructorParameters, HasAll>()
            fixedClass = bytecode.fixMetadata(logger, pathsOf(WithConstructorParameters::class))
                    .toClass<WithConstructorParameters, HasAll>()
        }
    }

    @Test
    fun `test source constructor has optional parameters`() {
        with(sourceClass.kotlin.constructors) {
            assertThat(size).isEqualTo(2)
            assertThat("source primary constructor missing", this, hasItem(primaryCon))
            assertThat("source secondary constructor missing", this, hasItem(secondaryCon))
        }

        val sourcePrimary = sourceClass.kotlin.primaryConstructor
                ?: throw AssertionError("source primary constructor missing")
        sourcePrimary.call(BIG_NUMBER, NUMBER, MESSAGE).apply {
            assertThat(longData()).isEqualTo(BIG_NUMBER)
            assertThat(intData()).isEqualTo(NUMBER)
            assertThat(stringData()).isEqualTo(MESSAGE)
        }

        val sourceSecondary = sourceClass.kotlin.constructors.firstOrNull { it != sourcePrimary }
                ?: throw AssertionError("source secondary constructor missing")
        sourceSecondary.call('X', MESSAGE).apply {
            assertThat(stringData()).isEqualTo("X$MESSAGE")
        }

        assertTrue("All source parameters should have defaults", sourcePrimary.hasAllOptionalParameters)
    }

    @Test
    fun `test fixed constructors exist`() {
        with(fixedClass.kotlin.constructors) {
            assertThat(size).isEqualTo(2)
            assertThat("fixed primary constructor missing", this, hasItem(primaryCon))
            assertThat("fixed secondary constructor missing", this, hasItem(secondaryCon))
        }
    }

    @Test
    fun `test fixed primary constructor has mandatory parameters`() {
        val fixedPrimary = fixedClass.kotlin.primaryConstructor
                ?: throw AssertionError("fixed primary constructor missing")
        assertTrue("All fixed parameters should be mandatory", fixedPrimary.hasAllMandatoryParameters)
    }

    @Test
    fun `test fixed secondary constructor still has optional parameters`() {
        val fixedSecondary = (fixedClass.kotlin.constructors - fixedClass.kotlin.primaryConstructor).firstOrNull()
                ?: throw AssertionError("fixed secondary constructor missing")
        assertTrue("Some fixed parameters should be optional", fixedSecondary.hasAnyOptionalParameters)
    }

    class MetadataTemplate(
        private val longData: Long = 0,
        private val intData: Int = 0,
        private val message: String = DEFAULT_MESSAGE
    ) : HasAll {
        @Suppress("UNUSED")
        constructor(prefix: Char, message: String = DEFAULT_MESSAGE) : this(message = prefix + message)

        override fun longData(): Long = longData
        override fun intData(): Int = intData
        override fun stringData(): String = message
    }
}

class WithConstructorParameters(
    private val longData: Long,
    private val intData: Int,
    private val message: String
) : HasAll {
    @Suppress("UNUSED")
    constructor(prefix: Char, message: String = DEFAULT_MESSAGE) : this(0, 0, prefix + message)

    override fun longData(): Long = longData
    override fun intData(): Int = intData
    override fun stringData(): String = message
}

