package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.annotations.Deletable
import net.corda.gradle.jarfilter.asm.bytecode
import net.corda.gradle.jarfilter.asm.toClass
import net.corda.gradle.jarfilter.matcher.isProperty
import org.gradle.api.logging.Logger
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNot.not
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThat
import org.junit.Test
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Demonstrate that we can still instantiate objects, even after we've deleted
 * one of their properties. (Check we haven't blown the constructor away!)
 */
class FieldRemovalTest {
    companion object {
        private val logger: Logger = StdOutLogging(FieldRemovalTest::class)
        private const val SHORT_NUMBER = 999.toShort()
        private const val BYTE_NUMBER = 99.toByte()
        private const val BIG_FLOATING_POINT = 9999999.9999
        private const val FLOATING_POINT = 9999.99f

        private val objectField = isProperty(equalTo("objectField"), equalTo("T"))
        private val longField = isProperty("longField", Long::class)
        private val intField = isProperty("intField", Int::class)
        private val shortField = isProperty("shortField", Short::class)
        private val byteField = isProperty("byteField", Byte::class)
        private val charField = isProperty("charField", Char::class)
        private val booleanField = isProperty("booleanField", Boolean::class)
        private val doubleField = isProperty("doubleField", Double::class)
        private val floatField = isProperty("floatField", Float::class)
        private val arrayField = isProperty("arrayField", ByteArray::class)
    }

    private inline fun <reified T: R, reified R: Any> transform(): Class<out R> = transform(T::class.java, R::class.java)

    private fun <T: R, R: Any> transform(type: Class<in T>, asType: Class<out R>): Class<out R> {
        val bytecode = type.bytecode.execute({ writer ->
            FilterTransformer(
                visitor = writer,
                logger = logger,
                removeAnnotations = emptySet(),
                deleteAnnotations = setOf(Deletable::class.jvmName.descriptor),
                stubAnnotations = emptySet(),
                unwantedElements = UnwantedCache()
            )
        }, COMPUTE_MAXS)
        return bytecode.toClass(type, asType)
    }

    @Test
    fun removeObject() {
        val sourceField = SampleGenericField(MESSAGE)
        assertEquals(MESSAGE, sourceField.objectField)
        assertThat("objectField not found", sourceField::class.declaredMemberProperties, hasItem(objectField))

        val targetField = transform<SampleGenericField<String>, HasGenericField<String>>()
            .getDeclaredConstructor(Any::class.java).newInstance(MESSAGE)
        assertFailsWith<AbstractMethodError> { targetField.objectField }
        assertFailsWith<AbstractMethodError> { targetField.objectField = "New Value" }
        assertThat("objectField still exists", targetField::class.declaredMemberProperties, not(hasItem(objectField)))
    }

    @Test
    fun removeLong() {
        val sourceField = SampleLongField(BIG_NUMBER)
        assertEquals(BIG_NUMBER, sourceField.longField)
        assertThat("longField not found", sourceField::class.declaredMemberProperties, hasItem(longField))

        val targetField = transform<SampleLongField, HasLongField>()
            .getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER)
        assertFailsWith<AbstractMethodError> { targetField.longField }
        assertFailsWith<AbstractMethodError> { targetField.longField = 10L }
        assertThat("longField still exists", targetField::class.declaredMemberProperties, not(hasItem(longField)))
    }

    @Test
    fun removeInt() {
        val sourceField = SampleIntField(NUMBER)
        assertEquals(NUMBER, sourceField.intField)
        assertThat("intField not found", sourceField::class.declaredMemberProperties, hasItem(intField))

        val targetField = transform<SampleIntField, HasIntField>()
            .getDeclaredConstructor(Int::class.java).newInstance(NUMBER)
        assertFailsWith<AbstractMethodError> { targetField.intField }
        assertFailsWith<AbstractMethodError> { targetField.intField = 100 }
        assertThat("intField still exists", targetField::class.declaredMemberProperties, not(hasItem(intField)))
    }

    @Test
    fun removeShort() {
        val sourceField = SampleShortField(SHORT_NUMBER)
        assertEquals(SHORT_NUMBER, sourceField.shortField)
        assertThat("shortField not found", sourceField::class.declaredMemberProperties, hasItem(shortField))

        val targetField = transform<SampleShortField, HasShortField>()
            .getDeclaredConstructor(Short::class.java).newInstance(SHORT_NUMBER)
        assertFailsWith<AbstractMethodError> { targetField.shortField }
        assertFailsWith<AbstractMethodError> { targetField.shortField = 15 }
        assertThat("shortField still exists", targetField::class.declaredMemberProperties, not(hasItem(shortField)))
    }

    @Test
    fun removeByte() {
        val sourceField = SampleByteField(BYTE_NUMBER)
        assertEquals(BYTE_NUMBER, sourceField.byteField)
        assertThat("byteField not found", sourceField::class.declaredMemberProperties, hasItem(byteField))

        val targetField = transform<SampleByteField, HasByteField>()
            .getDeclaredConstructor(Byte::class.java).newInstance(BYTE_NUMBER)
        assertFailsWith<AbstractMethodError> { targetField.byteField }
        assertFailsWith<AbstractMethodError> { targetField.byteField = 16 }
        assertThat("byteField still exists", targetField::class.declaredMemberProperties, not(hasItem(byteField)))
    }

    @Test
    fun removeBoolean() {
        val sourceField = SampleBooleanField(true)
        assertTrue(sourceField.booleanField)
        assertThat("booleanField not found", sourceField::class.declaredMemberProperties, hasItem(booleanField))

        val targetField = transform<SampleBooleanField, HasBooleanField>()
            .getDeclaredConstructor(Boolean::class.java).newInstance(true)
        assertFailsWith<AbstractMethodError> { targetField.booleanField }
        assertFailsWith<AbstractMethodError> { targetField.booleanField = false }
        assertThat("booleanField still exists", targetField::class.declaredMemberProperties, not(hasItem(booleanField)))
    }

    @Test
    fun removeChar() {
        val sourceField = SampleCharField('?')
        assertEquals('?', sourceField.charField)
        assertThat("charField not found", sourceField::class.declaredMemberProperties, hasItem(charField))

        val targetField = transform<SampleCharField, HasCharField>()
            .getDeclaredConstructor(Char::class.java).newInstance('?')
        assertFailsWith<AbstractMethodError> { targetField.charField }
        assertFailsWith<AbstractMethodError> { targetField.charField = 'A' }
        assertThat("charField still exists", targetField::class.declaredMemberProperties, not(hasItem(charField)))
    }

    @Test
    fun removeDouble() {
        val sourceField = SampleDoubleField(BIG_FLOATING_POINT)
        assertEquals(BIG_FLOATING_POINT, sourceField.doubleField)
        assertThat("doubleField not found", sourceField::class.declaredMemberProperties, hasItem(doubleField))

        val targetField = transform<SampleDoubleField, HasDoubleField>()
            .getDeclaredConstructor(Double::class.java).newInstance(BIG_FLOATING_POINT)
        assertFailsWith<AbstractMethodError> { targetField.doubleField }
        assertFailsWith<AbstractMethodError> { targetField.doubleField = 12345.678 }
        assertThat("doubleField still exists", targetField::class.declaredMemberProperties, not(hasItem(doubleField)))
    }

    @Test
    fun removeFloat() {
        val sourceField = SampleFloatField(FLOATING_POINT)
        assertEquals(FLOATING_POINT, sourceField.floatField)
        assertThat("floatField not found", sourceField::class.declaredMemberProperties, hasItem(floatField))

        val targetField = transform<SampleFloatField, HasFloatField>()
            .getDeclaredConstructor(Float::class.java).newInstance(FLOATING_POINT)
        assertFailsWith<AbstractMethodError> { targetField.floatField }
        assertFailsWith<AbstractMethodError> { targetField.floatField = 123.45f }
        assertThat("floatField still exists", targetField::class.declaredMemberProperties, not(hasItem(floatField)))
    }

    @Test
    fun removeArray() {
        val sourceField = SampleArrayField(byteArrayOf())
        assertArrayEquals(byteArrayOf(), sourceField.arrayField)
        assertThat("arrayField not found", sourceField::class.declaredMemberProperties, hasItem(arrayField))

        val targetField = transform<SampleArrayField, HasArrayField>()
            .getDeclaredConstructor(ByteArray::class.java).newInstance(byteArrayOf())
        assertFailsWith<AbstractMethodError> { targetField.arrayField }
        assertFailsWith<AbstractMethodError> { targetField.arrayField = byteArrayOf(0x35, 0x73) }
        assertThat("arrayField still exists", targetField::class.declaredMemberProperties, not(hasItem(arrayField)))
    }
}

interface HasGenericField<T> { var objectField: T }
interface HasLongField { var longField: Long }
interface HasIntField { var intField: Int }
interface HasShortField { var shortField: Short }
interface HasByteField { var byteField: Byte }
interface HasBooleanField { var booleanField: Boolean }
interface HasCharField { var charField: Char }
interface HasFloatField { var floatField: Float }
interface HasDoubleField { var doubleField: Double }
interface HasArrayField { var arrayField: ByteArray }

internal class SampleGenericField<T>(@Deletable override var objectField: T) : HasGenericField<T>
internal class SampleLongField(@Deletable override var longField: Long) : HasLongField
internal class SampleIntField(@Deletable override var intField: Int) : HasIntField
internal class SampleShortField(@Deletable override var shortField: Short) : HasShortField
internal class SampleByteField(@Deletable override var byteField: Byte) : HasByteField
internal class SampleBooleanField(@Deletable override var booleanField: Boolean) : HasBooleanField
internal class SampleCharField(@Deletable override var charField: Char) : HasCharField
internal class SampleFloatField(@Deletable override var floatField: Float) : HasFloatField
internal class SampleDoubleField(@Deletable override var doubleField: Double) : HasDoubleField
internal class SampleArrayField(@Deletable override var arrayField: ByteArray) : HasArrayField
