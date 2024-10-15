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
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.sha256
import net.corda.core.crypto.sign
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.sequence
import net.corda.coretesting.internal.rigorousMock
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.serialization.internal.encodingNotPermittedFormat
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Instant
import java.util.Collections
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class KryoTests(private val compression: CordaSerializationEncoding?) {
    companion object {
        private val ALICE_PUBKEY = TestIdentity(ALICE_NAME, 70).publicKey
        @Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }

    @get:Rule
    val serializationRule = CheckpointSerializationEnvironmentRule()
    private lateinit var context: CheckpointSerializationContext

    @Before
    fun setup() {
        context = CheckpointSerializationContextImpl(
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                compression,
                rigorousMock<EncodingWhitelist>().also {
                    if (compression != null) doReturn(true).whenever(it).acceptEncoding(compression)
                })
    }

    @Test(timeout=300_000)
	fun `simple data class`() {
        val birthday = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", birthday)
        val bits = mike.checkpointSerialize(context)
        assertThat(bits.checkpointDeserialize(context)).isEqualTo(Person("mike", birthday))
    }

    @Test(timeout=300_000)
	fun `null values`() {
        val bob = Person("bob", null)
        val bits = bob.checkpointSerialize(context)
        assertThat(bits.checkpointDeserialize(context)).isEqualTo(Person("bob", null))
    }

    @Test(timeout=300_000)
	fun `serialised form is stable when the same object instance is added to the deserialised object graph`() {
        val noReferencesContext = context.withoutReferences()
        val obj : ByteSequence = Ints.toByteArray(0x01234567).sequence()
        val originalList : ArrayList<ByteSequence> = ArrayList<ByteSequence>().apply { this += obj }
        val deserialisedList = originalList.checkpointSerialize(noReferencesContext).checkpointDeserialize(noReferencesContext)
        originalList += obj
        deserialisedList += obj
        assertThat(deserialisedList.checkpointSerialize(noReferencesContext)).isEqualTo(originalList.checkpointSerialize(noReferencesContext))
    }

    @Test(timeout=300_000)
	fun `serialised form is stable when the same object instance occurs more than once, and using java serialisation`() {
        val noReferencesContext = context.withoutReferences()
        val instant = Instant.ofEpochMilli(123)
        val instantCopy = Instant.ofEpochMilli(123)
        assertThat(instant).isNotSameAs(instantCopy)
        val listWithCopies = ArrayList<Instant>().apply {
            this += instant
            this += instantCopy
        }
        val listWithSameInstances = ArrayList<Instant>().apply {
            this += instant
            this += instant
        }
        assertThat(listWithSameInstances.checkpointSerialize(noReferencesContext)).isEqualTo(listWithCopies.checkpointSerialize(noReferencesContext))
    }

    @Test(timeout=300_000)
	fun `cyclic object graph`() {
        val cyclic = Cyclic(3)
        val bits = cyclic.checkpointSerialize(context)
        assertThat(bits.checkpointDeserialize(context)).isEqualTo(cyclic)
    }

    @Test(timeout=300_000)
	fun `deserialised key pair functions the same as serialised one`() {
        val keyPair = generateKeyPair()
        val bitsToSign: ByteArray = Ints.toByteArray(0x01234567)
        val wrongBits: ByteArray = Ints.toByteArray(0x76543210)
        val signature = keyPair.sign(bitsToSign)
        signature.verify(bitsToSign)
        assertThatThrownBy { signature.verify(wrongBits) }

        val deserialisedKeyPair = keyPair.checkpointSerialize(context).checkpointDeserialize(context)
        val deserialisedSignature = deserialisedKeyPair.sign(bitsToSign)
        deserialisedSignature.verify(bitsToSign)
        assertThatThrownBy { deserialisedSignature.verify(wrongBits) }
        assertSame(keyPair.public, deserialisedKeyPair.public)
    }

    @Test(timeout=300_000)
	fun `write and read Kotlin object singleton`() {
        val serialised = TestSingleton.checkpointSerialize(context)
        val deserialised = serialised.checkpointDeserialize(context)
        assertThat(deserialised).isSameAs(TestSingleton)
    }

    @Test(timeout=300_000)
	fun `check Kotlin EmptyList can be serialised`() {
        val deserialisedList: List<Int> = emptyList<Int>().checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(0, deserialisedList.size)
        assertEquals<Any>(Collections.emptyList<Int>().javaClass, deserialisedList.javaClass)
    }

    @Test(timeout=300_000)
	fun `check Kotlin EmptySet can be serialised`() {
        val deserialisedSet: Set<Int> = emptySet<Int>().checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(0, deserialisedSet.size)
        assertEquals<Any>(Collections.emptySet<Int>().javaClass, deserialisedSet.javaClass)
    }

    @Test(timeout=300_000)
	fun `check Kotlin EmptyMap can be serialised`() {
        val deserialisedMap: Map<Int, Int> = emptyMap<Int, Int>().checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(0, deserialisedMap.size)
        assertEquals<Any>(Collections.emptyMap<Int, Int>().javaClass, deserialisedMap.javaClass)
    }

    @Test(timeout=300_000)
	fun `InputStream serialisation`() {
        val rubbish = ByteArray(12345) { (it * it * 0.12345).toByte() }
        val readRubbishStream: InputStream = rubbish.inputStream().checkpointSerialize(context).checkpointDeserialize(context)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @Test(timeout=300_000)
	fun `InputStream serialisation does not write trailing garbage`() {
        val byteArrays = listOf("123", "456").map { it.toByteArray() }
        val streams = byteArrays.map { it.inputStream() }.checkpointSerialize(context).checkpointDeserialize(context).iterator()
        byteArrays.forEach { assertArrayEquals(it, streams.next().readBytes()) }
        assertFalse(streams.hasNext())
    }

    @Test(timeout=300_000)
    fun `serialize - deserialize SignableData`() {
        val testString = "Hello World"
        val testBytes = testString.toByteArray()

        val meta = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(ALICE_PUBKEY).schemeNumberID))
        val serializedMetaData = meta.checkpointSerialize(context).bytes
        val meta2 = serializedMetaData.checkpointDeserialize<SignableData>(context)
        assertEquals(meta2, meta)
        assertSame(meta.txId, meta2.txId)
    }

    @Test(timeout = 300_000)
    fun `serialize - deserialize internables`() {
        val list: List<Any> = listOf(
                SecureHash.randomSHA256(),
                CordaX500Name.parse("O=bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name"),
                Party.create(CordaX500Name.parse("O=bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name"), Crypto.generateKeyPair().public),
                AnonymousParty.create(Crypto.generateKeyPair().public),
                SignatureAttachmentConstraint.create(Crypto.generateKeyPair().public)
        )

        val serializedList = list.checkpointSerialize(context).bytes
        val list2 = serializedList.checkpointDeserialize<List<Any>>(context)
        list.zip(list2).forEach { (original, deserialized) ->
            assertSame(original, deserialized, "${original.javaClass} not interned")
        }
    }

    @Test(timeout = 300_000)
    fun `serialize - deserialize Logger`() {
        val storageContext: CheckpointSerializationContext = context
        val logger = LoggerFactory.getLogger("aName")
        val logger2 = logger.checkpointSerialize(storageContext).checkpointDeserialize(storageContext)
        assertEquals(logger.name, logger2.name)
        assertSame(logger, logger2)
    }

    @CordaSerializable
    private data class Person(val name: String, val birthday: Instant?)

    @Suppress("unused")
    @CordaSerializable
    private class Cyclic(val value: Int) {
        val thisInstance = this
        override fun equals(other: Any?): Boolean = (this === other) || (other is Cyclic && this.value == other.value)
        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = "Cyclic($value)"
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize PrivacySalt`() {
        val expected = PrivacySalt(byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32
        ))
        val serializedBytes = expected.checkpointSerialize(context)
        val actual = serializedBytes.checkpointDeserialize(context)
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

    @Test(timeout=300_000)
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
        val context = CheckpointSerializationContextImpl(
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                null)
        pt.checkpointSerialize(context)
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize Exception with suppressed`() {
        val exception = IllegalArgumentException("fooBar")
        val toBeSuppressedOnSenderSide = IllegalStateException("bazz1")
        exception.addSuppressed(toBeSuppressedOnSenderSide)
        val exception2 = exception.checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(exception.message, exception2.message)

        assertEquals(1, exception2.suppressed.size)
        assertNotNull({ exception2.suppressed.find { it.message == toBeSuppressedOnSenderSide.message } })

        val toBeSuppressedOnReceiverSide = IllegalStateException("bazz2")
        exception2.addSuppressed(toBeSuppressedOnReceiverSide)
        assertTrue { exception2.suppressed.contains(toBeSuppressedOnReceiverSide) }
        assertEquals(2, exception2.suppressed.size)
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize Exception no suppressed`() {
        val exception = IllegalArgumentException("fooBar")
        val exception2 = exception.checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(exception.message, exception2.message)
        assertEquals(0, exception2.suppressed.size)

        val toBeSuppressedOnReceiverSide = IllegalStateException("bazz2")
        exception2.addSuppressed(toBeSuppressedOnReceiverSide)
        assertEquals(1, exception2.suppressed.size)
        assertTrue { exception2.suppressed.contains(toBeSuppressedOnReceiverSide) }
    }

    @Test(timeout=300_000)
	fun `serialize - deserialize HashNotFound`() {
        val randomHash = SecureHash.randomSHA256()
        val exception = FetchDataFlow.HashNotFound(randomHash)
        val exception2 = exception.checkpointSerialize(context).checkpointDeserialize(context)
        assertEquals(randomHash, exception2.requested)
    }

    @Test(timeout=300_000)
	fun `compression has the desired effect`() {
        compression ?: return
        val data = ByteArray(12345).also { Random(0).nextBytes(it) }.let { it + it }
        val compressed = data.checkpointSerialize(context)
        assertEquals(.5, compressed.size.toDouble() / data.size, .03)
        assertArrayEquals(data, compressed.checkpointDeserialize(context))
    }

    @Test(timeout=300_000)
	fun `a particular encoding can be banned for deserialization`() {
        compression ?: return
        doReturn(false).whenever(context.encodingWhitelist).acceptEncoding(compression)
        val compressed = "whatever".checkpointSerialize(context)
        catchThrowable { compressed.checkpointDeserialize(context) }.run {
            assertSame<Any>(KryoException::class.java, javaClass)
            assertEquals(encodingNotPermittedFormat.format(compression), message)
        }
    }

    @Test(timeout=300_000)
	fun `compression reduces number of bytes significantly`() {
        class Holder(val holder: ByteArray)

        val obj = Holder(ByteArray(20000))
        val uncompressedSize = obj.checkpointSerialize(context.withEncoding(null)).size
        val compressedSize = obj.checkpointSerialize(context.withEncoding(CordaSerializationEncoding.SNAPPY)).size
        // If these need fixing, sounds like Kryo wire format changed and checkpoints might not survive an upgrade.
        if (SystemUtils.IS_JAVA_11)
            assertEquals(20184, uncompressedSize)
        else
            assertEquals(20234, uncompressedSize)
        assertEquals(1123, compressedSize)
    }
}
