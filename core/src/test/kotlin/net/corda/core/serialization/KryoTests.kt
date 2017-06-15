package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.google.common.primitives.Ints
import net.corda.core.crypto.*
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.node.services.messaging.Ack
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.testing.BOB_PUBKEY
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.cert.X509CertificateHolder
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.cert.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KryoTests {

    private lateinit var kryo: Kryo

    @Before
    fun setup() {
        // We deliberately do not return this, since we do some unorthodox registering below and do not want to pollute the pool.
        kryo = p2PKryo().borrow()
    }

    @Test
    fun ok() {
        val birthday = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", birthday)
        val bits = mike.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(Person("mike", birthday))
    }

    @Test
    fun nullables() {
        val bob = Person("bob", null)
        val bits = bob.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(Person("bob", null))
    }

    @Test
    fun `serialised form is stable when the same object instance is added to the deserialised object graph`() {
        kryo.noReferencesWithin<ArrayList<*>>()
        val obj = Ints.toByteArray(0x01234567).opaque()
        val originalList = arrayListOf(obj)
        val deserialisedList = originalList.serialize(kryo).deserialize(kryo)
        originalList += obj
        deserialisedList += obj
        assertThat(deserialisedList.serialize(kryo)).isEqualTo(originalList.serialize(kryo))
    }

    @Test
    fun `serialised form is stable when the same object instance occurs more than once, and using java serialisation`() {
        kryo.noReferencesWithin<ArrayList<*>>()
        val instant = Instant.ofEpochMilli(123)
        val instantCopy = Instant.ofEpochMilli(123)
        assertThat(instant).isNotSameAs(instantCopy)
        val listWithCopies = arrayListOf(instant, instantCopy)
        val listWithSameInstances = arrayListOf(instant, instant)
        assertThat(listWithSameInstances.serialize(kryo)).isEqualTo(listWithCopies.serialize(kryo))
    }

    @Test
    fun `cyclic object graph`() {
        val cyclic = Cyclic(3)
        val bits = cyclic.serialize(kryo)
        assertThat(bits.deserialize(kryo)).isEqualTo(cyclic)
    }

    @Test
    fun `deserialised key pair functions the same as serialised one`() {
        val keyPair = generateKeyPair()
        val bitsToSign: ByteArray = Ints.toByteArray(0x01234567)
        val wrongBits: ByteArray = Ints.toByteArray(0x76543210)
        val signature = keyPair.sign(bitsToSign)
        signature.verify(bitsToSign)
        assertThatThrownBy { signature.verify(wrongBits) }

        val deserialisedKeyPair = keyPair.serialize(kryo).deserialize(kryo)
        val deserialisedSignature = deserialisedKeyPair.sign(bitsToSign)
        deserialisedSignature.verify(bitsToSign)
        assertThatThrownBy { deserialisedSignature.verify(wrongBits) }
    }

    @Test
    fun `write and read Ack`() {
        val tokenizableBefore = Ack
        val serializedBytes = tokenizableBefore.serialize(kryo)
        val tokenizableAfter = serializedBytes.deserialize(kryo)
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    @Test
    fun `InputStream serialisation`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = rubbish.inputStream().serialize(kryo).deserialize(kryo)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @Test
    fun `serialize - deserialize MetaData`() {
        val testString = "Hello World"
        val testBytes = testString.toByteArray()
        val keyPair1 = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val bitSet = java.util.BitSet(10)
        bitSet.set(3)

        val meta = MetaData("ECDSA_SECP256K1_SHA256", "M9", SignatureType.FULL, Instant.now(), bitSet, bitSet, testBytes, keyPair1.public)
        val serializedMetaData = meta.bytes()
        val meta2 = serializedMetaData.deserialize<MetaData>()
        assertEquals(meta2, meta)
    }

    @Test
    fun `serialize - deserialize Logger`() {
        val logger = LoggerFactory.getLogger("aName")
        val logger2 = logger.serialize(storageKryo()).deserialize(storageKryo())
        assertEquals(logger.name, logger2.name)
        assertTrue(logger === logger2)
    }

    @Test
    fun `HashCheckingStream (de)serialize`() {
        val rubbish = ByteArray(12345, { (it * it * 0.12345).toByte() })
        val readRubbishStream: InputStream = NodeAttachmentService.HashCheckingStream(SecureHash.sha256(rubbish), rubbish.size, ByteArrayInputStream(rubbish)).serialize(kryo).deserialize(kryo)
        for (i in 0..12344) {
            assertEquals(rubbish[i], readRubbishStream.read().toByte())
        }
        assertEquals(-1, readRubbishStream.read())
    }

    @Test
    fun `serialize - deserialize X509CertififcateHolder`() {
        val expected: X509CertificateHolder = X509Utilities.createSelfSignedCACertificate(ALICE.name, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        val serialized = expected.serialize(kryo).bytes
        val actual: X509CertificateHolder = serialized.deserialize(kryo)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize - deserialize X509CertPath`() {
        val certFactory = CertificateFactory.getInstance("X509")
        val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(ALICE.name, rootCAKey)
        val certificate = X509Utilities.createCertificate(CertificateType.TLS, rootCACert, rootCAKey, BOB.name, BOB_PUBKEY)
        val expected = certFactory.generateCertPath(listOf(rootCACert.cert, certificate.cert))
        val serialized = expected.serialize(kryo).bytes
        val actual: CertPath = serialized.deserialize(kryo)
        assertEquals(expected, actual)
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

}
