package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.DescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.testutils.TestDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignatureMetadataSerializerTest {
    private companion object {
        // The descriptors SignatureMetadataSerializer writes on the wire.
        const val SIGNATURE_METADATA_LEGACY_DESCRIPTOR = "net.corda:IzFt8cRKytsJq3vQ+yjsGg=="
        const val SIGNATURE_METADATA_EXTENDED_DESCRIPTOR = "net.corda:net.corda.core.crypto.SignatureMetadata:extended"
    }

    data class Wrapper(val metadata: SignatureMetadata)

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
    fun `descriptors written on the wire resolve to the custom serializer without type matching`() {
        // If these descriptors are missing from the registry, DefaultRemoteSerializerFactory falls back to comparing
        // them with the custom serializer's own descriptor, which never matches, and logs a
        // "Mismatch between type descriptors" warning on every deserialization.
        val registry = TestDescriptorBasedSerializerRegistry()
        factory(registry)

        listOf(SIGNATURE_METADATA_LEGACY_DESCRIPTOR, SIGNATURE_METADATA_EXTENDED_DESCRIPTOR).forEach { descriptor ->
            val serializer = registry.contents.getValue(descriptor)
            assertEquals(descriptor, serializer.typeDescriptor.toString())
            assertEquals(SignatureMetadata::class.java, serializer.type)
        }
    }

    @Test(timeout = 300_000)
    fun `deserialization finds the serializer by descriptor, without type matching`() {
        // Records the descriptors the registry had no serializer for. DefaultRemoteSerializerFactory builds a serializer for those by
        // matching the remote type against the local one, which is where it logs "Mismatch between type descriptors".
        val builtFor = mutableListOf<String>()
        val serializers = mutableMapOf<String, AMQPSerializer<Any>>()
        val receiver = factory(object : DescriptorBasedSerializerRegistry {
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
        })
        // Registration builds the entries of the custom serializers: only what deserialization builds matters.
        builtFor.clear()

        val oldKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val newKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val proofChain = KeyRotationProofChain(listOf(KeyRotationProof(oldKey.public, newKey.public, Crypto.doSign(oldKey.private, newKey.public.encoded))))
        listOf(
                SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID),
                SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID, proofChain)
        ).forEach { metadata ->
            // On its own, and as a property, where the type notation of the metadata is interpreted with the schema of the wrapper.
            assertEquals(metadata, DeserializationInput(receiver).deserialize(TestSerializationOutput(false, factory()).serialize(metadata)))
            assertEquals(Wrapper(metadata), DeserializationInput(receiver).deserialize(TestSerializationOutput(false, factory()).serialize(Wrapper(metadata))))
        }

        assertTrue(builtFor.none { it == SIGNATURE_METADATA_LEGACY_DESCRIPTOR || it == SIGNATURE_METADATA_EXTENDED_DESCRIPTOR },
                "A serializer was built for a SignatureMetadata descriptor: $builtFor")
    }

    @Test(timeout = 300_000)
    fun `legacy signature metadata round trips`() {
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID)
        assertEquals(metadata, roundTrip(metadata))
        assertEquals(Wrapper(metadata), roundTrip(Wrapper(metadata)))
    }

    @Test(timeout = 300_000)
    fun `signature metadata with a proof chain round trips`() {
        val oldKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val newKey = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val proof = KeyRotationProof(oldKey.public, newKey.public, Crypto.doSign(oldKey.private, newKey.public.encoded))
        val metadata = SignatureMetadata(170, Crypto.EDDSA_ED25519_SHA512.schemeNumberID, KeyRotationProofChain(listOf(proof)))
        assertEquals(metadata, roundTrip(metadata))
        assertEquals(Wrapper(metadata), roundTrip(Wrapper(metadata)))
    }
}
