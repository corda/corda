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

class MetaFixConstructorTest {
    companion object {
        private val logger: Logger = StdOutLogging(MetaFixConstructorTest::class)
        private val unwantedCon = isConstructor(
            returnType = WithConstructor::class,
            parameters = *arrayOf(Int::class, Long::class)
        )
        private val wantedCon = isConstructor(
            returnType = WithConstructor::class,
            parameters = *arrayOf(Long::class)
        )
    }

    @Test
    fun testConstructorRemovedFromMetadata() {
        val bytecode = recodeMetadataFor<WithConstructor, MetadataTemplate>()
        val sourceClass = bytecode.toClass<WithConstructor, HasLong>()

        // Check that the unwanted constructor has been successfully
        // added to the metadata, and that the class is valid.
        val sourceObj = sourceClass.getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
        assertEquals(BIG_NUMBER, sourceObj.longData())
        assertThat("<init>(Int,Long) not found", sourceClass.kotlin.constructors, hasItem(unwantedCon))
        assertThat("<init>(Long) not found", sourceClass.kotlin.constructors, hasItem(wantedCon))

        // Rewrite the metadata according to the contents of the bytecode.
        val fixedClass = bytecode.fixMetadata(logger).toClass<WithConstructor, HasLong>()
        val fixedObj = fixedClass.getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
        assertEquals(BIG_NUMBER, fixedObj.longData())
        assertThat("<init>(Int,Long) still exists", fixedClass.kotlin.constructors, not(hasItem(unwantedCon)))
        assertThat("<init>(Long) not found", fixedClass.kotlin.constructors, hasItem(wantedCon))
    }

    class MetadataTemplate(private val longData: Long) : HasLong {
        @Suppress("UNUSED_PARAMETER", "UNUSED")
        constructor(intData: Int, longData: Long) : this(longData)
        override fun longData(): Long = longData
    }
}

class WithConstructor(private val longData: Long) : HasLong {
    override fun longData(): Long = longData
}
