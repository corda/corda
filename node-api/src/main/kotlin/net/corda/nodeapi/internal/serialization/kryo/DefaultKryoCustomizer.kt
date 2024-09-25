package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.BitSetSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import de.javakaffee.kryoserializers.guava.ImmutableListSerializer
import de.javakaffee.kryoserializers.guava.ImmutableMapSerializer
import de.javakaffee.kryoserializers.guava.ImmutableMultimapSerializer
import de.javakaffee.kryoserializers.guava.ImmutableSetSerializer
import de.javakaffee.kryoserializers.guava.ImmutableSortedSetSerializer
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.PrivacySalt
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.LazyMappedList
import net.corda.core.internal.readFully
import net.corda.core.serialization.MissingAttachmentsException
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.ContractUpgradeFilteredTransaction
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.toNonEmptySet
import net.corda.serialization.internal.DefaultWhitelist
import net.corda.serialization.internal.GeneratedAttachment
import net.corda.serialization.internal.MutableClassWhitelist
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import org.slf4j.Logger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*

object DefaultKryoCustomizer {
    private val serializationWhitelists: List<SerializationWhitelist> by lazy {
        ServiceLoader.load(SerializationWhitelist::class.java, this.javaClass.classLoader).toList() + DefaultWhitelist
    }

    fun customize(kryo: Kryo): Kryo {
        return kryo.apply {
            isRegistrationRequired = false
            references = true
            // Needed because of https://github.com/EsotericSoftware/kryo/issues/864
            setOptimizedGenerics(false)

            val defaultFactoryConfig = FieldSerializer.FieldSerializerConfig()
            // Take the safest route here and allow subclasses to have fields named the same as super classes.
            defaultFactoryConfig.extendedFieldNames = true
            defaultFactoryConfig.serializeTransient = false
            // For checkpoints we still want all the synthetic fields.  This allows inner classes to reference
            // their parents after deserialization.
            defaultFactoryConfig.ignoreSyntheticFields = false
            kryo.setDefaultSerializer(SerializerFactory.FieldSerializerFactory(defaultFactoryConfig))

            instantiatorStrategy = CustomInstantiatorStrategy()

            addDefaultSerializer(Iterator::class.java, object : SerializerFactory.BaseSerializerFactory<IteratorSerializer>() {
                override fun newSerializer(kryo: Kryo, type: Class<*>): IteratorSerializer {
                    val config = CompatibleFieldSerializer.CompatibleFieldSerializerConfig().apply {
                        ignoreSyntheticFields = false
                        extendedFieldNames = true
                    }
                    return IteratorSerializer(type, CompatibleFieldSerializer(kryo, type, config))
                }
            })

            // Required for HashCheckingStream (de)serialization.
            // Note that return type should be specifically set to InputStream, otherwise it may not work,
            // i.e. val aStream : InputStream = HashCheckingStream(...).
            addDefaultSerializer(InputStream::class.java, InputStreamSerializer)
            addDefaultSerializer(SerializeAsToken::class.java, SerializeAsTokenSerializer<SerializeAsToken>())
            addDefaultSerializer(Logger::class.java, LoggerSerializer)
            addDefaultSerializer(X509Certificate::class.java, X509CertificateSerializer)

            // WARNING: reordering the registrations here will cause a change in the serialized form, since classes
            // with custom serializers get written as registration ids. This will break backwards-compatibility.
            // Please add any new registrations to the end.

            addDefaultSerializer(LinkedHashMapIteratorSerializer.getIterator()::class.java.superclass, LinkedHashMapIteratorSerializer)
            addDefaultSerializer(PublicKey::class.java, PublicKeySerializer)
            addDefaultSerializer(PrivateKey::class.java, PrivateKeySerializer)
            register(LinkedHashMapEntrySerializer.getEntry()::class.java, LinkedHashMapEntrySerializer)
            register(LinkedListItrSerializer.getListItr()::class.java, LinkedListItrSerializer)
            register(Arrays.asList("").javaClass, ArraysAsListSerializer())
            register(LazyMappedList::class.java, LazyMappedListSerializer)
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
            // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
            register(Array<StackTraceElement>::class, read = { _, _ -> emptyArray() }, write = { _, _, _ -> })
            // This ensures a NonEmptySetSerializer is constructed with an initial value.
            register(NonEmptySet::class.java, NonEmptySetSerializer)
            register(BitSet::class.java, BitSetSerializer())
            register(Class::class.java, ClassSerializer)
            register(FileInputStream::class.java, InputStreamSerializer)
            register(CertPath::class.java, CertPathSerializer)
            register(NotaryChangeWireTransaction::class.java, NotaryChangeWireTransactionSerializer)
            register(PartyAndCertificate::class.java, PartyAndCertificateSerializer)

            // Don't deserialize PrivacySalt via its default constructor.
            register(PrivacySalt::class.java, PrivacySaltSerializer)

            // Used by the remote verifier, and will possibly be removed in future.
            register(ContractAttachment::class.java, ContractAttachmentSerializer)

            register(java.lang.invoke.SerializedLambda::class.java)
            register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
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
        private val defaultStrategy = DefaultInstantiatorStrategy(fallbackStrategy)

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

        override fun read(kryo: Kryo, input: Input, type: Class<out PartyAndCertificate>): PartyAndCertificate {
            return PartyAndCertificate(kryo.readClassAndObject(input) as CertPath)
        }
    }

    private object NonEmptySetSerializer : Serializer<NonEmptySet<Any>>() {
        override fun write(kryo: Kryo, output: Output, obj: NonEmptySet<Any>) {
            // Write out the contents as normal
            output.writeInt(obj.size, true)
            obj.forEach { kryo.writeClassAndObject(output, it) }
        }

        override fun read(kryo: Kryo, input: Input, type: Class<out NonEmptySet<Any>>): NonEmptySet<Any> {
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

        override fun read(kryo: Kryo, input: Input, type: Class<out PrivacySalt>): PrivacySalt {
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
            kryo.writeClassAndObject(output, obj.signerKeys)
            output.writeInt(obj.version)
        }

        @Suppress("UNCHECKED_CAST")
        override fun read(kryo: Kryo, input: Input, type: Class<out ContractAttachment>): ContractAttachment {
            if (kryo.serializationContext() != null) {
                val attachmentHash = SecureHash.createSHA256(input.readBytes(32))
                val contract = input.readString()
                val additionalContracts = kryo.readClassAndObject(input) as Set<ContractClassName>
                val uploader = input.readString()
                val signers = kryo.readClassAndObject(input) as List<PublicKey>
                val version = input.readInt()
                val context = kryo.serializationContext()!!
                val attachmentStorage = context.serviceHub.attachments

                val lazyAttachment = object : AbstractAttachment({
                    val attachment = attachmentStorage.openAttachment(attachmentHash)
                            ?: throw MissingAttachmentsException(listOf(attachmentHash))
                    attachment.open().readFully()
                }, uploader) {
                    override val id = attachmentHash
                }

                return ContractAttachment.create(lazyAttachment, contract, additionalContracts, uploader, signers, version)
            } else {
                val attachment = GeneratedAttachment(input.readBytesWithLength(), "generated")
                val contract = input.readString()
                val additionalContracts = kryo.readClassAndObject(input) as Set<ContractClassName>
                val uploader = input.readString()
                val signers = kryo.readClassAndObject(input) as List<PublicKey>
                val version = input.readInt()
                return ContractAttachment.create(attachment, contract, additionalContracts, uploader, signers, version)
            }
        }
    }
}
