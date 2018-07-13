@file:JvmName("StaticFields")
@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.annotations.Deletable
import net.corda.gradle.jarfilter.asm.bytecode
import net.corda.gradle.jarfilter.asm.toClass
import org.gradle.api.logging.Logger
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertFailsWith

/**
 * Static properties are all initialised in the same <clinit> block.
 * Show that deleting some field references doesn't break the other
 * properties' initialisation code.
 */
class StaticFieldRemovalTest {
    companion object {
        private val logger: Logger = StdOutLogging(StaticFieldRemovalTest::class)
        private const val FIELD_CLASS = "net.corda.gradle.jarfilter.StaticFields"

        private lateinit var sourceClass: Class<out Any>
        private lateinit var targetClass: Class<out Any>

        private fun <T : R, R : Any> transform(type: Class<in T>, asType: Class<out R>): Class<out R> {
            val bytecode = type.bytecode.execute({ writer ->
                FilterTransformer(
                    visitor = writer,
                    logger = logger,
                    removeAnnotations = emptySet(),
                    deleteAnnotations = setOf(Deletable::class.jvmName.descriptor),
                    stubAnnotations = emptySet(),
                    unwantedClasses = mutableSetOf()
                )
            }, COMPUTE_MAXS)
            return bytecode.toClass(type, asType)
        }

        @JvmStatic
        @BeforeClass
        fun setup() {
            sourceClass = Class.forName(FIELD_CLASS)
            targetClass = transform(sourceClass, Any::class.java)
        }
    }

    @Test
    fun deleteStaticString() {
        assertEquals("1", sourceClass.getDeclaredMethod("getStaticString").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticString") }
    }

    @Test
    fun deleteStaticLong() {
        assertEquals(2L, sourceClass.getDeclaredMethod("getStaticLong").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticLong") }
    }

    @Test
    fun deleteStaticInt() {
        assertEquals(3, sourceClass.getDeclaredMethod("getStaticInt").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticInt") }
    }

    @Test
    fun deleteStaticShort() {
        assertEquals(4.toShort(), sourceClass.getDeclaredMethod("getStaticShort").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticShort") }
    }

    @Test
    fun deleteStaticByte() {
        assertEquals(5.toByte(), sourceClass.getDeclaredMethod("getStaticByte").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticByte") }
    }

    @Test
    fun deleteStaticChar() {
        assertEquals(6.toChar(), sourceClass.getDeclaredMethod("getStaticChar").invoke(null))
        assertFailsWith<NoSuchMethodException> { targetClass.getDeclaredMethod("getStaticChar") }
    }

    @Test
    fun checkSeedHasBeenIncremented() {
        assertEquals(6, sourceClass.getDeclaredMethod("getStaticSeed").invoke(null))
        assertEquals(6, targetClass.getDeclaredMethod("getStaticSeed").invoke(null))
    }
}

private var seed: Int = 0
val staticSeed get() = seed

@Deletable val staticString: String = (++seed).toString()
@Deletable val staticLong: Long = (++seed).toLong()
@Deletable val staticInt: Int = ++seed
@Deletable val staticShort: Short = (++seed).toShort()
@Deletable val staticByte: Byte = (++seed).toByte()
@Deletable val staticChar: Char = (++seed).toChar()
