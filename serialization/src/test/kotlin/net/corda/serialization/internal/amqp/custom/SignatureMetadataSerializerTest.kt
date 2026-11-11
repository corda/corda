package net.corda.serialization.internal.amqp.custom

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.testutils.TestDescriptorBasedSerializerRegistry
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SignatureMetadataSerializerTest {
    private companion object {
        // The descriptors SignatureMetadataSerializer writes on the wire.
        const val SIGNATURE_METADATA_LEGACY_DESCRIPTOR = "net.corda:IzFt8cRKytsJq3vQ+yjsGg=="
        const val SIGNATURE_METADATA_EXTENDED_DESCRIPTOR = "net.corda:net.corda.core.crypto.SignatureMetadata:extended"
    }

    data class Wrapper(val metadata: SignatureMetadata)

    private fun factory(registry: TestDescriptorBasedSerializerRegistry = TestDescriptorBasedSerializerRegistry()): SerializerFactory =
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
    fun `type descriptor is the legacy descriptor written on the wire`() {
        // DefaultRemoteSerializerFactory compares the remote descriptor with this one, and logs a
        // "Mismatch between type descriptors" warning when they differ.
        assertEquals(SIGNATURE_METADATA_LEGACY_DESCRIPTOR, SignatureMetadataSerializer.typeDescriptor.toString())
    }

    @Test(timeout = 300_000)
    fun `descriptors written on the wire resolve to the custom serializer without type matching`() {
        val registry = TestDescriptorBasedSerializerRegistry()
        factory(registry)

        assertSame(SignatureMetadataSerializer, registry.contents[SIGNATURE_METADATA_LEGACY_DESCRIPTOR])
        assertSame(SignatureMetadataSerializer, registry.contents[SIGNATURE_METADATA_EXTENDED_DESCRIPTOR])
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
