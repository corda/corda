package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.core.node.AttachmentClassLoaderTests
import net.corda.core.node.AttachmentsClassLoader
import net.corda.core.node.services.AttachmentStorage
import net.corda.testing.node.MockAttachmentStorage
import org.junit.Test

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
        CordaClassResolver(EmptyClassList).getRegistration(Foo.Bar::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(EmptyClassList).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(EmptyClassList).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(EmptyClassList).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method on modified Kryo does not consult the whitelist`() {
        val kryo = CordaKryo(CordaClassResolver(EmptyClassList))
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Calling register method on unmodified Kryo does consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(EmptyClassList), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(EmptyClassList).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(GlobalTransientClassList(EmptyClassList))
        (resolver.whitelist as MutableClassList).add(NotSerializable::class.java)
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(EmptyClassList).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(EmptyClassList).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(EmptyClassList).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(EmptyClassList).getRegistration(DefaultSerializable::class.java)
    }

    private fun importJar(storage: AttachmentStorage) = AttachmentClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage)
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(EmptyClassList).getRegistration(attachedClass)
    }

    @Test
    fun `Annotation is inherited from interfaces`() {
        CordaClassResolver(EmptyClassList).getRegistration(SerializableViaInterface::class.java)
        CordaClassResolver(EmptyClassList).getRegistration(SerializableViaSubInterface::class.java)
    }

    @Test
    fun `Annotation is inherited from superclass`() {
        CordaClassResolver(EmptyClassList).getRegistration(SubElement::class.java)
        CordaClassResolver(EmptyClassList).getRegistration(SubSubElement::class.java)
        CordaClassResolver(EmptyClassList).getRegistration(SerializableViaSuperSubInterface::class.java)
    }
}