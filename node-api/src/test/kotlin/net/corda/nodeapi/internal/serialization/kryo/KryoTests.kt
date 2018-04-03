package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.primitives.Ints
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.*
import net.corda.core.internal.FetchDataFlow
import net.corda.core.serialization.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.sequence
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.serialization.*
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import org.assertj.core.api.Assertions.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class KryoTests(private val compression: CordaSerializationEncoding?) {
    companion object {
        private val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        @Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }

    private lateinit var factory: SerializationFactory
    private lateinit var context: SerializationContext

    @Before
    fun setup() {
        factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        context = SerializationContextImpl(kryoMagic,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.Storage,
                compression,
                rigorousMock<EncodingWhitelist>().also {
                    if (compression != null) doReturn(true).whenever(it).acceptEncoding(compression)
                })
    }

    @Test
    fun `simple data class`() {
        val birthday = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", birthday)
        val bits = mike.serialize(factory, context)
        assertThat(bits.deserialize(factory, context)).isEqualTo(Person("mike", birthday))
    }

    @Test
    fun `null values`() {
        val bob = Person("bob", null)
        val bits = bob.serialize(factory, context)
        assertThat(bits.deserialize(factory, context)).isEqualTo(Person("bob", null))
    }

    @Test
    fun `serialised form is stable when the same object instance is added to the deserialised object graph`() {
        val noReferencesContext = context.withoutReferences()
        val obj = Ints.toByteArray(0x01234567).sequence()
        val originalList = arrayListOf(obj)
        val deserialisedList = originalList.serialize(factory, noReferencesContext).deserialize(factory, noReferencesContext)
        originalList += obj
        deserialisedList += obj
        assertThat(deserialisedList.serialize(factory, noReferencesContext)).isEqualTo(originalList.serialize(factory, noReferencesContext))
    }

    @Test
    fun `serialised form is stable when the same object instance occurs more than once, and using java serialisation`() {
        val noReferencesContext = context.withoutReferences()
        val instant = Instant.ofEpochMilli(123)
        val instantCopy = Instant.ofEpochMilli(123)
        assertThat(instant).isNotSameAs(instantCopy)
        val listWithCopies = arrayListOf(instant, instantCopy)
        val listWithSameInstances = arrayListOf(instant, instant)
        assertThat(listWithSameInstances.serialize(factory, noReferencesContext)).isEqualTo(listWithCopies.serialize(factory, noReferencesContext))
    }

    @Test
    fun `cyclic object graph`() {
        val cyclic = Cyclic(3)
        val bits = cyclic.serialize(factory, context)
        assertThat(bits.deserialize(factory, context)).isEqualTo(cyclic)
    }

    @Test
    fun `deserialised key pair functions the same as serialised one`() {
        val keyPair = generateKeyPair()
        val bitsToSign: ByteArray = Ints.toByteArray(0x01234567)
        val wrongBits: ByteArray = Ints.toByteArray(0x76543210)
        val signature = keyPair.sign(bitsToSign)
        signature.verify(bitsToSign)
        assertThatThrownBy { signature.verify(wrongBits) }

        val deserialisedKeyPair = keyPair.serialize(factory, context).deserialize(factory, context)
        val deserialisedSignature = deserialisedKeyPair.sign(bitsToSign)
        deserialisedSignature.verify(bitsToSign)
        assertThatThrownBy { deserialisedSignature.verify(wrongBits) }
    }

    @Test
    fun `write and read Kotlin object singleton`() {
        val serialised = TestSingleton.serialize(factory, context)
        val deserialised = serialised.deserialize(factory, context)
        assertThat(deserialised).isSameAs(TestSingleton)
    }

    @Test
    fun `check Kotlin EmptyList can be serialised`() {
        val deserialisedList: List<Int> = emptyList<Int>().serialize(factory, context).deserialize(factory, context)
        assertEquals(0, deserialisedList.size)
        assertEquals<Any>(Collections.emptyList<Int>().javaClass, deserialisedList.javaClass)
    }

    @Test
    fun `check Kotlin EmptySet can be serialised`() {
        val deserialisedSet: Set<Int> = emptySet<Int>().serialize(factory, context).deserialize(factory, context)
        assertEquals(0, deserialisedSet.size)
        assertEquals<Any>(Collections.emptySet<Int>().javaClass, deserialisedSet.javaClass)
    }

    @Test
    fun `check Kotlin EmptyMap can be serialised`() {
        val deserialisedMap: Map<Int, Int> = emptyMap<Int, Int>().serialize(factory, context).deserialize(factory, context)
        assertEquals(0, deserialisedMap.size)
        assertEquals<Any>(Collections.emptyMap<Int, Int>().javaClass, deserialisedMap.javaClass)
    }

    @Test
    fun `InputStream serialisation`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = rubbish.inputStream().serialize(factory, context).deserialize(factory, context)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @Test
    fun `InputStream serialisation does not write trailing garbage`() {
        val byteArrays = listOf("123", "456").map { it.toByteArray() }
        val streams = byteArrays.map { it.inputStream() }.serialize(factory, context).deserialize(factory, context).iterator()
        byteArrays.forEach { assertArrayEquals(it, streams.next().readBytes()) }
        assertFalse(streams.hasNext())
    }

    @Test
    fun `serialize - deserialize SignableData`() {
        val testString = "Hello World"
        val testBytes = testString.toByteArray()

        val meta = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID))
        val serializedMetaData = meta.serialize(factory, context).bytes
        val meta2 = serializedMetaData.deserialize<SignableData>(factory, context)
        assertEquals(meta2, meta)
    }

    @Test
    fun `serialize - deserialize Logger`() {
        val storageContext: SerializationContext = context // TODO: make it storage context
        val logger = LoggerFactory.getLogger("aName")
        val logger2 = logger.serialize(factory, storageContext).deserialize(factory, storageContext)
        assertEquals(logger.name, logger2.name)
        assertTrue(logger === logger2)
    }

    @Test
    fun `HashCheckingStream (de)serialize`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = NodeAttachmentService.HashCheckingStream(SecureHash.sha256(rubbish), rubbish.size, ByteArrayInputStream(rubbish)).serialize(factory, context).deserialize(factory, context)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @net.corda.annotations.serialization.Serializable
    private data class Person(val name: String, val birthday: Instant?)

    @Suppress("unused")
    @net.corda.annotations.serialization.Serializable
    private class Cyclic(val value: Int) {
        val thisInstance = this
        override fun equals(other: Any?): Boolean = (this === other) || (other is Cyclic && this.value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Cyclic($value)"
    }

    @Test
    fun `serialize - deserialize PrivacySalt`() {
        val expected = PrivacySalt(byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32
        ))
        val serializedBytes = expected.serialize(factory, context)
        val actual = serializedBytes.deserialize(factory, context)
        assertEquals(expected, actual)
    }

    @CordaSerializable
    private object TestSingleton

    object SimpleSteps {
        object ONE : ProgressTracker.Step("one")
        object TWO : ProgressTracker.Step("two")
        object THREE : ProgressTracker.Step("three")
        object FOUR : ProgressTracker.Step("four")

        fun tracker() = ProgressTracker(ONE, TWO, THREE, FOUR)
    }

    object ChildSteps {
        object AYY : ProgressTracker.Step("ayy")
        object BEE : ProgressTracker.Step("bee")
        object SEA : ProgressTracker.Step("sea")

        fun tracker() = ProgressTracker(AYY, BEE, SEA)
    }

    @Test
    fun rxSubscriptionsAreNotSerialized() {
        val pt: ProgressTracker = SimpleSteps.tracker()
        val pt2: ProgressTracker = ChildSteps.tracker()

        class Unserializable : KryoSerializable {
            override fun write(kryo: Kryo?, output: Output?) = throw AssertionError("not called")
            override fun read(kryo: Kryo?, input: Input?) = throw AssertionError("not called")

            fun foo() {
                println("bar")
            }
        }

        pt.setChildProgressTracker(SimpleSteps.TWO, pt2)
        class Tmp {
            val unserializable = Unserializable()

            init {
                pt2.changes.subscribe { unserializable.foo() }
            }
        }
        Tmp()
        val factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        val context = SerializationContextImpl(kryoMagic,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P,
                null)
        pt.serialize(factory, context)
    }

    @Test
    fun `serialize - deserialize Exception with suppressed`() {
        val exception = IllegalArgumentException("fooBar")
        val toBeSuppressedOnSenderSide = IllegalStateException("bazz1")
        exception.addSuppressed(toBeSuppressedOnSenderSide)
        val exception2 = exception.serialize(factory, context).deserialize(factory, context)
        assertEquals(exception.message, exception2.message)

        assertEquals(1, exception2.suppressed.size)
        assertNotNull({ exception2.suppressed.find { it.message == toBeSuppressedOnSenderSide.message } })

        val toBeSuppressedOnReceiverSide = IllegalStateException("bazz2")
        exception2.addSuppressed(toBeSuppressedOnReceiverSide)
        assertTrue { exception2.suppressed.contains(toBeSuppressedOnReceiverSide) }
        assertEquals(2, exception2.suppressed.size)
    }

    @Test
    fun `serialize - deserialize Exception no suppressed`() {
        val exception = IllegalArgumentException("fooBar")
        val exception2 = exception.serialize(factory, context).deserialize(factory, context)
        assertEquals(exception.message, exception2.message)
        assertEquals(0, exception2.suppressed.size)

        val toBeSuppressedOnReceiverSide = IllegalStateException("bazz2")
        exception2.addSuppressed(toBeSuppressedOnReceiverSide)
        assertEquals(1, exception2.suppressed.size)
        assertTrue { exception2.suppressed.contains(toBeSuppressedOnReceiverSide) }
    }

    @Test
    fun `serialize - deserialize HashNotFound`() {
        val randomHash = SecureHash.randomSHA256()
        val exception = FetchDataFlow.HashNotFound(randomHash)
        val exception2 = exception.serialize(factory, context).deserialize(factory, context)
        assertEquals(randomHash, exception2.requested)
    }

    @Test
    fun `compression has the desired effect`() {
        compression ?: return
        val data = ByteArray(12345).also { Random(0).nextBytes(it) }.let { it + it }
        val compressed = data.serialize(factory, context)
        assertEquals(.5, compressed.size.toDouble() / data.size, .03)
        assertArrayEquals(data, compressed.deserialize(factory, context))
    }

    @Test
    fun `a particular encoding can be banned for deserialization`() {
        compression ?: return
        doReturn(false).whenever(context.encodingWhitelist).acceptEncoding(compression)
        val compressed = "whatever".serialize(factory, context)
        catchThrowable { compressed.deserialize(factory, context) }.run {
            assertSame(KryoException::class.java, javaClass)
            assertEquals(encodingNotPermittedFormat.format(compression), message)
        }
    }
}