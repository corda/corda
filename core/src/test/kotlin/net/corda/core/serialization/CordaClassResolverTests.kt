package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.core.node.AttachmentClassLoaderTests
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.node.services.AttachmentStorage
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
    @Test
    fun `Annotation on enum works for specialised entries`() {
        // TODO: Remove this suppress when we upgrade to kotlin 1.1 or when JetBrain fixes the bug.
        @Suppress("UNSUPPORTED_FEATURE")
        CordaClassResolver(EmptyWhitelist).getRegistration(Foo.Bar::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(EmptyWhitelist).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method on modified Kryo does not consult the whitelist`() {
        val kryo = CordaKryo(CordaClassResolver(EmptyWhitelist))
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Calling register method on unmodified Kryo does consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(EmptyWhitelist), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(GlobalTransientClassWhiteList(EmptyWhitelist))
        (resolver.whitelist as MutableClassWhitelist).add(NotSerializable::class.java)
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(DefaultSerializable::class.java)
    }

    private fun importJar(storage: AttachmentStorage) = AttachmentClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage)
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(EmptyWhitelist).getRegistration(attachedClass)
    }

    @Test
    fun `Annotation is inherited from interfaces`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(SerializableViaInterface::class.java)
        CordaClassResolver(EmptyWhitelist).getRegistration(SerializableViaSubInterface::class.java)
    }

    @Test
    fun `Annotation is inherited from superclass`() {
        CordaClassResolver(EmptyWhitelist).getRegistration(SubElement::class.java)
        CordaClassResolver(EmptyWhitelist).getRegistration(SubSubElement::class.java)
        CordaClassResolver(EmptyWhitelist).getRegistration(SerializableViaSuperSubInterface::class.java)
    }

    // Blacklist tests.
    @get:Rule
    val expectedEx = ExpectedException.none()!!

    @Test
    fun `Check blacklisted class`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("Class java.util.HashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // HashSet is blacklisted.
        resolver.getRegistration(HashSet::class.java)
    }

    open class SubHashSet<E> : HashSet<E>()
    @Test
    fun `Check blacklisted subclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.core.serialization.CordaClassResolverTests\$SubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // SubHashSet extends the blacklisted HashSet.
        resolver.getRegistration(SubHashSet::class.java)
    }

    class SubSubHashSet<E> : SubHashSet<E>()
    @Test
    fun `Check blacklisted subsubclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.core.serialization.CordaClassResolverTests\$SubSubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // SubSubHashSet extends SubHashSet, which extends the blacklisted HashSet.
        resolver.getRegistration(SubSubHashSet::class.java)
    }

    class ConnectionImpl(val connection: Connection) : Connection by connection
    @Test
    fun `Check blacklisted interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.core.serialization.CordaClassResolverTests\$ConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // ConnectionImpl implements blacklisted Connection.
        resolver.getRegistration(ConnectionImpl::class.java)
    }

    interface SubConnection : Connection
    class SubConnectionImpl(val subConnection: SubConnection) : SubConnection by subConnection
    @Test
    fun `Check blacklisted super-interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.core.serialization.CordaClassResolverTests\$SubConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // SubConnectionImpl implements SubConnection, which extends the blacklisted Connection.
        resolver.getRegistration(SubConnectionImpl::class.java)
    }

    @Test
    fun `Check forcibly allowed`() {
        val resolver = CordaClassResolver(AllButBlacklisted)
        // LinkedHashSet is allowed for serialization.
        resolver.getRegistration(LinkedHashSet::class.java)
    }

    @CordaSerializable
    class CordaSerializableHashSet<E> : HashSet<E>()
    @Test
    fun `Check blacklist precedes CordaSerializable`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.core.serialization.CordaClassResolverTests\$CordaSerializableHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(AllButBlacklisted)
        // CordaSerializableHashSet is @CordaSerializable, but extends the blacklisted HashSet.
        resolver.getRegistration(CordaSerializableHashSet::class.java)
    }
}