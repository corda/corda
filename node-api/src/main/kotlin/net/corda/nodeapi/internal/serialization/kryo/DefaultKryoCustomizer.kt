/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.BitSetSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import de.javakaffee.kryoserializers.guava.*
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.readFully
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.*
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.toNonEmptySet
import net.corda.nodeapi.internal.serialization.CordaClassResolver
import net.corda.nodeapi.internal.serialization.DefaultWhitelist
import net.corda.nodeapi.internal.serialization.GeneratedAttachment
import net.corda.nodeapi.internal.serialization.MutableClassWhitelist
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.Logger
import sun.security.ec.ECPublicKeyImpl
import sun.security.provider.certpath.X509CertPath
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*
import kotlin.collections.ArrayList

object DefaultKryoCustomizer {
    private val serializationWhitelists: List<SerializationWhitelist> by lazy {
        ServiceLoader.load(SerializationWhitelist::class.java, this.javaClass.classLoader).toList() + DefaultWhitelist
    }

    fun customize(kryo: Kryo, publicKeySerializer: Serializer<PublicKey> = PublicKeySerializer): Kryo {
        return kryo.apply {
            // Store a little schema of field names in the stream the first time a class is used which increases tolerance
            // for change to a class.
            setDefaultSerializer(CompatibleFieldSerializer::class.java)
            // Take the safest route here and allow subclasses to have fields named the same as super classes.
            fieldSerializerConfig.cachedFieldNameStrategy = FieldSerializer.CachedFieldNameStrategy.EXTENDED

            instantiatorStrategy = CustomInstantiatorStrategy()

            // Required for HashCheckingStream (de)serialization.
            // Note that return type should be specifically set to InputStream, otherwise it may not work, i.e. val aStream : InputStream = HashCheckingStream(...).
            addDefaultSerializer(InputStream::class.java, InputStreamSerializer)
            addDefaultSerializer(SerializeAsToken::class.java, SerializeAsTokenSerializer<SerializeAsToken>())
            addDefaultSerializer(Logger::class.java, LoggerSerializer)
            addDefaultSerializer(X509Certificate::class.java, X509CertificateSerializer)

            // WARNING: reordering the registrations here will cause a change in the serialized form, since classes
            // with custom serializers get written as registration ids. This will break backwards-compatibility.
            // Please add any new registrations to the end.
            // TODO: re-organise registrations into logical groups before v1.0

            register(Arrays.asList("").javaClass, ArraysAsListSerializer())
            register(SignedTransaction::class.java, SignedTransactionSerializer)
            register(WireTransaction::class.java, WireTransactionSerializer)
            register(SerializedBytes::class.java, SerializedBytesSerializer)
            UnmodifiableCollectionsSerializer.registerSerializers(this)
            ImmutableListSerializer.registerSerializers(this)
            ImmutableSetSerializer.registerSerializers(this)
            ImmutableSortedSetSerializer.registerSerializers(this)
            ImmutableMapSerializer.registerSerializers(this)
            ImmutableMultimapSerializer.registerSerializers(this)
            // InputStream subclasses whitelisting, required for attachments.
            register(BufferedInputStream::class.java, InputStreamSerializer)
            register(Class.forName("sun.net.www.protocol.jar.JarURLConnection\$JarURLInputStream"), InputStreamSerializer)
            noReferencesWithin<WireTransaction>()
            register(ECPublicKeyImpl::class.java, publicKeySerializer)
            register(EdDSAPublicKey::class.java, publicKeySerializer)
            register(EdDSAPrivateKey::class.java, PrivateKeySerializer)
            register(CompositeKey::class.java, publicKeySerializer)  // Using a custom serializer for compactness
            // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
            register(Array<StackTraceElement>::class, read = { _, _ -> emptyArray() }, write = { _, _, _ -> })
            // This ensures a NonEmptySetSerializer is constructed with an initial value.
            register(NonEmptySet::class.java, NonEmptySetSerializer)
            register(BitSet::class.java, BitSetSerializer())
            register(Class::class.java, ClassSerializer)
            register(FileInputStream::class.java, InputStreamSerializer)
            register(CertPath::class.java, CertPathSerializer)
            register(X509CertPath::class.java, CertPathSerializer)
            register(BCECPrivateKey::class.java, PrivateKeySerializer)
            register(BCECPublicKey::class.java, publicKeySerializer)
            register(BCRSAPrivateCrtKey::class.java, PrivateKeySerializer)
            register(BCRSAPublicKey::class.java, publicKeySerializer)
            register(BCSphincs256PrivateKey::class.java, PrivateKeySerializer)
            register(BCSphincs256PublicKey::class.java, publicKeySerializer)
            register(NotaryChangeWireTransaction::class.java, NotaryChangeWireTransactionSerializer)
            register(PartyAndCertificate::class.java, PartyAndCertificateSerializer)

            // Don't deserialize PrivacySalt via its default constructor.
            register(PrivacySalt::class.java, PrivacySaltSerializer)

            // Used by the remote verifier, and will possibly be removed in future.
            register(ContractAttachment::class.java, ContractAttachmentSerializer)

            register(java.lang.invoke.SerializedLambda::class.java)
            register(ClosureSerializer.Closure::class.java, CordaClosureBlacklistSerializer)
            register(ContractUpgradeWireTransaction::class.java, ContractUpgradeWireTransactionSerializer)
            register(ContractUpgradeFilteredTransaction::class.java, ContractUpgradeFilteredTransactionSerializer)

            for (whitelistProvider in serializationWhitelists) {
                val types = whitelistProvider.whitelist
                require(types.toSet().size == types.size) {
                    val duplicates = types.toMutableList()
                    types.toSet().forEach { duplicates -= it }
                    "Cannot add duplicate classes to the whitelist ($duplicates)."
                }
                for (type in types) {
                    ((kryo.classResolver as? CordaClassResolver)?.whitelist as? MutableClassWhitelist)?.add(type)
                }
            }
        }
    }

    private class CustomInstantiatorStrategy : InstantiatorStrategy {
        private val fallbackStrategy = StdInstantiatorStrategy()
        // Use this to allow construction of objects using a JVM backdoor that skips invoking the constructors, if there
        // is no no-arg constructor available.
        private val defaultStrategy = Kryo.DefaultInstantiatorStrategy(fallbackStrategy)

        override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
            // However this doesn't work for non-public classes in the java. namespace
            val strat = if (type.name.startsWith("java.") && !isPublic(type.modifiers)) fallbackStrategy else defaultStrategy
            return strat.newInstantiatorOf(type)
        }
    }

    private object PartyAndCertificateSerializer : Serializer<PartyAndCertificate>() {
        override fun write(kryo: Kryo, output: Output, obj: PartyAndCertificate) {
            kryo.writeClassAndObject(output, obj.certPath)
        }

        override fun read(kryo: Kryo, input: Input, type: Class<PartyAndCertificate>): PartyAndCertificate {
            return PartyAndCertificate(kryo.readClassAndObject(input) as CertPath)
        }
    }

    private object NonEmptySetSerializer : Serializer<NonEmptySet<Any>>() {
        override fun write(kryo: Kryo, output: Output, obj: NonEmptySet<Any>) {
            // Write out the contents as normal
            output.writeInt(obj.size, true)
            obj.forEach { kryo.writeClassAndObject(output, it) }
        }

        override fun read(kryo: Kryo, input: Input, type: Class<NonEmptySet<Any>>): NonEmptySet<Any> {
            val size = input.readInt(true)
            require(size >= 1) { "Invalid size read off the wire: $size" }
            val list = ArrayList<Any>(size)
            repeat(size) {
                list += kryo.readClassAndObject(input)
            }
            return list.toNonEmptySet()
        }
    }

    /*
     * Avoid deserialising PrivacySalt via its default constructor
     * because the random number generator may not be available.
     */
    private object PrivacySaltSerializer : Serializer<PrivacySalt>() {
        override fun write(kryo: Kryo, output: Output, obj: PrivacySalt) {
            output.writeBytesWithLength(obj.bytes)
        }

        override fun read(kryo: Kryo, input: Input, type: Class<PrivacySalt>): PrivacySalt {
            return PrivacySalt(input.readBytesWithLength())
        }
    }

    private object ContractAttachmentSerializer : Serializer<ContractAttachment>() {
        override fun write(kryo: Kryo, output: Output, obj: ContractAttachment) {
            if (kryo.serializationContext() != null) {
                obj.attachment.id.writeTo(output)
            } else {
                val buffer = ByteArrayOutputStream()
                obj.attachment.open().use { it.copyTo(buffer) }
                output.writeBytesWithLength(buffer.toByteArray())
            }
            output.writeString(obj.contract)
            kryo.writeClassAndObject(output, obj.additionalContracts)
            output.writeString(obj.uploader)
        }

        override fun read(kryo: Kryo, input: Input, type: Class<ContractAttachment>): ContractAttachment {
            if (kryo.serializationContext() != null) {
                val attachmentHash = SecureHash.SHA256(input.readBytes(32))
                val contract = input.readString()
                @Suppress("UNCHECKED_CAST")
                val additionalContracts = kryo.readClassAndObject(input) as Set<ContractClassName>
                val uploader = input.readString()
                val context = kryo.serializationContext()!!
                val attachmentStorage = context.serviceHub.attachments

                val lazyAttachment = object : AbstractAttachment({
                    val attachment = attachmentStorage.openAttachment(attachmentHash)
                            ?: throw MissingAttachmentsException(listOf(attachmentHash))
                    attachment.open().readFully()
                }) {
                    override val id = attachmentHash
                }

                return ContractAttachment(lazyAttachment, contract, additionalContracts, uploader)
            } else {
                val attachment = GeneratedAttachment(input.readBytesWithLength())
                val contract = input.readString()
                @Suppress("UNCHECKED_CAST")
                val additionalContracts = kryo.readClassAndObject(input) as Set<ContractClassName>
                val uploader = input.readString()
                return ContractAttachment(attachment, contract, additionalContracts, uploader)
            }
        }
    }
}