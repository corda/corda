package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.testutils.TestDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import org.apache.qpid.proton.amqp.DescribedType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SignatureMetadataSerializerTest {
    private companion object {
        // The fingerprint SignatureMetadata had before it had a proof chain: metadata without one is still written under it.
        const val SIGNATURE_METADATA_LEGACY_DESCRIPTOR = "net.corda:IzFt8cRKytsJq3vQ+yjsGg=="
        // The descriptor of the serializer of the class, under which metadata with a proof chain is written.
        const val SIGNATURE_METADATA_DESCRIPTOR = "net.corda:net.corda.core.crypto.SignatureMetadata"
    }

    data class Wrapper(val metadata: SignatureMetadata)

    /**
     * A registry that records the descriptors it had no serializer for when asked. DefaultRemoteSerializerFactory builds a serializer
     * for those by matching the remote type against the local one, which is where it logs "Mismatch between type descriptors".
     */
    private class RecordingRegistry : DescriptorBasedSerializerRegistry {
        private val serializers = mutableMapOf<String, AMQPSerializer<Any>>()
        val builtFor = mutableListOf<String>()

        override fun get(descriptor: String): AMQPSerializer<Any>? = serializers[descriptor]

        override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
            serializers.putIfAbsent(descriptor, serializer)
        }

        override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>): AMQPSerializer<Any> {
            return serializers[descriptor] ?: builder().also {
                builtFor += descriptor
                serializers.putIfAbsent(descriptor, it)
            }
        }
    }

    private fun factory(registry: DescriptorBasedSerializerRegistry = TestDescriptorBasedSerializerRegistry()): SerializerFactory =
            testDefaultFactory(registry).apply {
                register(PublicKeySerializer)
                register(SignatureMetadataSerializer)
            }

    private inline fun <reified T : Any> roundTrip(obj: T): T {
        // Use separate factories so that deserialization has to resolve the descriptors from the received schema.
        val bytes = TestSerializationOutput(false, factory()).serialize(obj)
        return DeserializationInput(factory()).deserialize(bytes)
    }

    @Test(timeout = 300_000)
    fun `each descriptor written on the wire resolves to a serializer with that descriptor`() {
        // A descriptor that is missing from the registry sends DefaultRemoteSerializerFactory to compare it with the descriptor of
        // the serializer of the class, and to log a "Mismatch between type descriptors" warning when they differ. Each form
        // therefore has a serializer of its own, registered under the descriptor it writes.
        val registry = TestDescriptorBasedSerializerRegistry()
        val factory = factory(registry)

        listOf(SIGNATURE_METADATA_LEGACY_DESCRIPTOR, SIGNATURE_METADATA_DESCRIPTOR).forEach { descriptor ->
            val serializer = registry.contents.getValue(descriptor)
            assertEquals(descriptor, serializer.typeDescriptor.toString())
            assertEquals(SignatureMetadata::class.java, serializer.type)
        }
        // The serializer of the class is the ordinary one: its descriptor is hashed into the fingerprint of every type that has a
        // SignatureMetadata property.
        assertSame(SignatureMetadataSerializer, registry.contents[SIGNATURE_METADATA_DESCRIPTOR])
        assertSame(SignatureMetadataSerializer, factory.get(SignatureMetadata::class.java, SignatureMetadata::class.java))
    }

    @Test(timeout = 300_000)
    fun `deserialization finds the serializer by descriptor, without type matching`() {
        val registry = RecordingRegistry()
        val receiver = factory(registry)
        // Registration builds the entries of the custom serializers: only what deserialization builds matters.
        registry.builtFor.clear()

        val withoutProofChain = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID)
        val withProofChain = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID, proofChain())
        listOf(withoutProofChain, withProofChain).forEach { metadata ->
            // On its own: whatever descriptor it was written under must already have its serializer.
            val bytes = TestSerializationOutput(false, factory()).serialize(metadata)
            val writtenUnder = (DeserializationInput.getEnvelope(bytes).obj as DescribedType).descriptor.toString()
            assertEquals(metadata, DeserializationInput(receiver).deserialize(bytes))
            assertFalse(writtenUnder in registry.builtFor, "No serializer was registered for $writtenUnder")

            // As a property: the schema of the wrapper is interpreted, and with it the type notation of the metadata, if it has one.
            val wrapped = TestSerializationOutput(false, factory()).serialize(Wrapper(metadata))
            assertEquals(Wrapper(metadata), DeserializationInput(receiver).deserialize(wrapped))
        }

        assertTrue(registry.builtFor.none { it == SIGNATURE_METADATA_LEGACY_DESCRIPTOR || it == SIGNATURE_METADATA_DESCRIPTOR },
                "A serializer was built for a SignatureMetadata descriptor: ${registry.builtFor}")
    }

    @Test(timeout = 300_000)
    fun `metadata without a proof chain is written in its legacy form`() {
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID)
        val envelope = DeserializationInput.getEnvelope(TestSerializationOutput(false, factory()).serialize(metadata))

        // The descriptor, the type notation and the fields of the object serializer it had before the proof chain existed.
        assertEquals(SIGNATURE_METADATA_LEGACY_DESCRIPTOR, (envelope.obj as DescribedType).descriptor.toString())
        val typeNotation = envelope.schema.types.single() as CompositeType
        assertEquals(SignatureMetadata::class.java.name, typeNotation.name)
        assertEquals(SIGNATURE_METADATA_LEGACY_DESCRIPTOR, typeNotation.descriptor.name.toString())
        assertEquals(listOf("platformVersion", "schemeNumberID"), typeNotation.fields.map { it.name })
        assertEquals(2, ((envelope.obj as DescribedType).described as List<*>).size)
    }

    @Test(timeout = 300_000)
    fun `metadata with a proof chain is written under the descriptor of its serializer`() {
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID, proofChain())
        val envelope = DeserializationInput.getEnvelope(TestSerializationOutput(false, factory()).serialize(metadata))

        assertEquals(SIGNATURE_METADATA_DESCRIPTOR, (envelope.obj as DescribedType).descriptor.toString())
        assertEquals(3, ((envelope.obj as DescribedType).described as List<*>).size)
        // Like any custom serializer, it describes the fields itself: the schema has no notation for the type.
        assertTrue(envelope.schema.types.none { it.name == SignatureMetadata::class.java.name })
    }

    private fun proofChain(): KeyRotationProofChain {
        val oldKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val newKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        return KeyRotationProofChain(listOf(KeyRotationProof(oldKey.public, newKey.public, Crypto.doSign(oldKey.private, newKey.public.encoded))))
    }

    @Test(timeout = 300_000)
    fun `legacy signature metadata round trips`() {
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID)
        assertEquals(metadata, roundTrip(metadata))
        assertEquals(Wrapper(metadata), roundTrip(Wrapper(metadata)))
    }

    @Test(timeout = 300_000)
    fun `signature metadata with a proof chain round trips`() {
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID, proofChain())
        assertEquals(metadata, roundTrip(metadata))
        assertEquals(Wrapper(metadata), roundTrip(Wrapper(metadata)))
    }
}
