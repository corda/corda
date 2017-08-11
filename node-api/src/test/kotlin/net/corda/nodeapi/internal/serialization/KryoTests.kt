package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.primitives.Ints
import net.corda.core.crypto.*
import net.corda.core.serialization.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.sequence
import net.corda.node.serialization.KryoServerSerializationScheme
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.testing.ALICE_PUBKEY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KryoTests {
    private lateinit var factory: SerializationFactory
    private lateinit var context: SerializationContext

    @Before
    fun setup() {
        factory = SerializationFactoryImpl().apply { registerScheme(KryoServerSerializationScheme()) }
        context = SerializationContextImpl(KryoHeaderV0_1,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P)
    }

    @Test
    fun ok() {
        val birthday = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", birthday)
        val bits = mike.serialize(factory, context)
        assertThat(bits.deserialize(factory, context)).isEqualTo(Person("mike", birthday))
    }

    @Test
    fun nullables() {
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
    fun `InputStream serialisation`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = rubbish.inputStream().serialize(factory, context).deserialize(factory, context)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
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
        val context = SerializationContextImpl(KryoHeaderV0_1,
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                SerializationContext.UseCase.P2P)
        pt.serialize(factory, context)
    }
}
