package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.AttachmentClassLoaderTests
import net.corda.testing.node.MockAttachmentStorage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.lang.IllegalStateException
import java.sql.Connection
import java.util.*

@CordaSerializable
enum class Foo {
    Bar {
        override val value = 0
    },
    Stick {
        override val value = 1
    };

    abstract val value: Int
}

@CordaSerializable
open class Element

open class SubElement : Element()

class SubSubElement : SubElement()

abstract class AbstractClass

interface Interface

@CordaSerializable
interface SerializableInterface

interface SerializableSubInterface : SerializableInterface

class NotSerializable

class SerializableViaInterface : SerializableInterface

open class SerializableViaSubInterface : SerializableSubInterface

class SerializableViaSuperSubInterface : SerializableViaSubInterface()


@CordaSerializable
class CustomSerializable : KryoSerializable {
    override fun read(kryo: Kryo?, input: Input?) {
    }

    override fun write(kryo: Kryo?, output: Output?) {
    }
}

@CordaSerializable
@DefaultSerializer(DefaultSerializableSerializer::class)
class DefaultSerializable

class DefaultSerializableSerializer : Serializer<DefaultSerializable>() {
    override fun write(kryo: Kryo, output: Output, obj: DefaultSerializable) {
    }

    override fun read(kryo: Kryo, input: Input, type: Class<DefaultSerializable>): DefaultSerializable {
        return DefaultSerializable()
    }
}

class CordaClassResolverTests {
    val factory: SerializationFactory = object : SerializationFactory {
        override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }

    val emptyWhitelistContext: SerializationContext = SerializationContextImpl(KryoHeaderV0_1, this.javaClass.classLoader, EmptyWhitelist, emptyMap(), true, SerializationContext.UseCase.P2P)
    val allButBlacklistedContext: SerializationContext = SerializationContextImpl(KryoHeaderV0_1, this.javaClass.classLoader, AllButBlacklisted, emptyMap(), true, SerializationContext.UseCase.P2P)

    @Test
    fun `Annotation on enum works for specialised entries`() {
        // TODO: Remove this suppress when we upgrade to kotlin 1.1 or when JetBrain fixes the bug.
        @Suppress("UNSUPPORTED_FEATURE")
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(Foo.Bar::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method on modified Kryo does not consult the whitelist`() {
        val kryo = CordaKryo(CordaClassResolver(factory, emptyWhitelistContext))
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Calling register method on unmodified Kryo does consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(factory, emptyWhitelistContext), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(factory, emptyWhitelistContext.withWhitelisted(NotSerializable::class.java))
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(DefaultSerializable::class.java)
    }

    private fun importJar(storage: AttachmentStorage) = AttachmentClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage)
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(attachedClass)
    }

    @Test
    fun `Annotation is inherited from interfaces`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(SerializableViaInterface::class.java)
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(SerializableViaSubInterface::class.java)
    }

    @Test
    fun `Annotation is inherited from superclass`() {
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(SubElement::class.java)
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(SubSubElement::class.java)
        CordaClassResolver(factory, emptyWhitelistContext).getRegistration(SerializableViaSuperSubInterface::class.java)
    }

    // Blacklist tests.
    @get:Rule
    val expectedEx = ExpectedException.none()!!

    @Test
    fun `Check blacklisted class`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("Class java.util.HashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // HashSet is blacklisted.
        resolver.getRegistration(HashSet::class.java)
    }

    open class SubHashSet<E> : HashSet<E>()
    @Test
    fun `Check blacklisted subclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.nodeapi.internal.serialization.CordaClassResolverTests\$SubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // SubHashSet extends the blacklisted HashSet.
        resolver.getRegistration(SubHashSet::class.java)
    }

    class SubSubHashSet<E> : SubHashSet<E>()
    @Test
    fun `Check blacklisted subsubclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.nodeapi.internal.serialization.CordaClassResolverTests\$SubSubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // SubSubHashSet extends SubHashSet, which extends the blacklisted HashSet.
        resolver.getRegistration(SubSubHashSet::class.java)
    }

    class ConnectionImpl(val connection: Connection) : Connection by connection
    @Test
    fun `Check blacklisted interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.nodeapi.internal.serialization.CordaClassResolverTests\$ConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // ConnectionImpl implements blacklisted Connection.
        resolver.getRegistration(ConnectionImpl::class.java)
    }

    interface SubConnection : Connection
    class SubConnectionImpl(val subConnection: SubConnection) : SubConnection by subConnection
    @Test
    fun `Check blacklisted super-interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.nodeapi.internal.serialization.CordaClassResolverTests\$SubConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // SubConnectionImpl implements SubConnection, which extends the blacklisted Connection.
        resolver.getRegistration(SubConnectionImpl::class.java)
    }

    @Test
    fun `Check forcibly allowed`() {
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // LinkedHashSet is allowed for serialization.
        resolver.getRegistration(LinkedHashSet::class.java)
    }

    @CordaSerializable
    class CordaSerializableHashSet<E> : HashSet<E>()
    @Test
    fun `Check blacklist precedes CordaSerializable`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.nodeapi.internal.serialization.CordaClassResolverTests\$CordaSerializableHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(factory, allButBlacklistedContext)
        // CordaSerializableHashSet is @CordaSerializable, but extends the blacklisted HashSet.
        resolver.getRegistration(CordaSerializableHashSet::class.java)
    }
}