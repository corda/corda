package net.corda.core.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.MapReferenceResolver
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import de.javakaffee.kryoserializers.BitSetSerializer
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer
import de.javakaffee.kryoserializers.guava.*
import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.MetaData
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.NonEmptySetSerializer
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
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
import sun.security.provider.certpath.X509CertPath
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Modifier.isPublic
import java.security.cert.CertPath
import java.util.*

object DefaultKryoCustomizer {
    private val pluginRegistries: List<CordaPluginRegistry> by lazy {
        // No ClassResolver only constructor.  MapReferenceResolver is the default as used by Kryo in other constructors.
        val unusedKryo = Kryo(makeStandardClassResolver(), MapReferenceResolver())
        val customization = KryoSerializationCustomization(unusedKryo)
        ServiceLoader.load(CordaPluginRegistry::class.java).toList().filter { it.customizeSerialization(customization) }
    }

    fun customize(kryo: Kryo): Kryo {
        return kryo.apply {
            // Store a little schema of field names in the stream the first time a class is used which increases tolerance
            // for change to a class.
            setDefaultSerializer(CompatibleFieldSerializer::class.java)
            // Take the safest route here and allow subclasses to have fields named the same as super classes.
            fieldSerializerConfig.cachedFieldNameStrategy = FieldSerializer.CachedFieldNameStrategy.EXTENDED

            instantiatorStrategy = CustomInstantiatorStrategy()

            register(Arrays.asList("").javaClass, ArraysAsListSerializer())
            register(SignedTransaction::class.java, ImmutableClassSerializer(SignedTransaction::class))
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

            register(sun.security.ec.ECPublicKeyImpl::class.java, ECPublicKeyImplSerializer)
            register(EdDSAPublicKey::class.java, Ed25519PublicKeySerializer)
            register(EdDSAPrivateKey::class.java, Ed25519PrivateKeySerializer)

            // Using a custom serializer for compactness
            register(CompositeKey::class.java, CompositeKeySerializer)

            // Exceptions. We don't bother sending the stack traces as the client will fill in its own anyway.
            register(Array<StackTraceElement>::class, read = { _, _ -> emptyArray() }, write = { _, _, _ -> })

            // This ensures a NonEmptySetSerializer is constructed with an initial value.
            register(NonEmptySet::class.java, NonEmptySetSerializer)

            addDefaultSerializer(SerializeAsToken::class.java, SerializeAsTokenSerializer<SerializeAsToken>())

            register(MetaData::class.java, MetaDataSerializer)
            register(BitSet::class.java, BitSetSerializer())
            register(Class::class.java, ClassSerializer)

            addDefaultSerializer(Logger::class.java, LoggerSerializer)

            register(FileInputStream::class.java, InputStreamSerializer)
            // Required for HashCheckingStream (de)serialization.
            // Note that return type should be specifically set to InputStream, otherwise it may not work, i.e. val aStream : InputStream = HashCheckingStream(...).
            addDefaultSerializer(InputStream::class.java, InputStreamSerializer)

            register(CertPath::class.java, CertPathSerializer)
            register(X509CertPath::class.java, CertPathSerializer)
            register(X500Name::class.java, X500NameSerializer)
            register(X509CertificateHolder::class.java, X509CertificateSerializer)

            register(BCECPrivateKey::class.java, PrivateKeySerializer)
            register(BCECPublicKey::class.java, PublicKeySerializer)
            register(BCRSAPrivateCrtKey::class.java, PrivateKeySerializer)
            register(BCRSAPublicKey::class.java, PublicKeySerializer)
            register(BCSphincs256PrivateKey::class.java, PrivateKeySerializer)
            register(BCSphincs256PublicKey::class.java, PublicKeySerializer)
            register(sun.security.ec.ECPublicKeyImpl::class.java, PublicKeySerializer)

            val customization = KryoSerializationCustomization(this)
            pluginRegistries.forEach { it.customizeSerialization(customization) }
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
}
