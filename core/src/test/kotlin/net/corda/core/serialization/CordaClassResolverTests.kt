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

@CordaNotSerializable
open class NotSerialisedElement

open class SubNotSerialisedElement : NotSerialisedElement()

class SubSubNotSerialisedElement : SubNotSerialisedElement()

@CordaNotSerializable
interface NotSerializableInterface

interface SubNotSerializableInterface : NotSerializableInterface

interface SubSubNotSerializableInterface : SubNotSerializableInterface

@CordaNotSerializable
@CordaSerializable
class DualElement

@CordaSerializable
class SerialisableElementWithNonSerialisableInteface : NotSerializableInterface

class NotSerializableViaInterface : NotSerializableInterface

open class NotSerializableViaSubInterface : SubNotSerializableInterface

class NotSerializableViaSuperSubInterface : NotSerializableViaSubInterface()

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
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(Foo.Bar::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method on modified Kryo does not consult the whitelist`() {
        val kryo = CordaKryo(CordaClassResolver(EmptyClassList, EmptyClassList))
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Calling register method on unmodified Kryo does consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(EmptyClassList, EmptyClassList), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(GlobalTransientClassList(EmptyClassList), EmptyClassList)
        (resolver.whitelist as MutableClassList).add(NotSerializable::class.java)
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(DefaultSerializable::class.java)
    }

    private fun importJar(storage: AttachmentStorage) = AttachmentClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it) }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage)
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(attachedClass)
    }

    @Test
    fun `Annotation is inherited from interfaces`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SerializableViaInterface::class.java)
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SerializableViaSubInterface::class.java)
    }

    @Test
    fun `Annotation is inherited from superclass`() {
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SubElement::class.java)
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SubSubElement::class.java)
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SerializableViaSuperSubInterface::class.java)
    }

    // Blacklisting checks
    @get:Rule
    val expectedEx = ExpectedException.none()

    @Test
    fun `check  corda not serialisable inherited from interfaces`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerializableViaInterface cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(NotSerializableViaInterface::class.java)
    }

    @Test
    fun `check CordaNotSerialisable inherited from subinterface`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerializableViaSubInterface cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(NotSerializableViaSubInterface::class.java)
    }

    @Test
    fun `check CordaNotSerialisable supersubinterface`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerializableViaSuperSubInterface cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(NotSerializableViaSuperSubInterface::class.java)
    }

    @Test
    fun `check CordaNotSerialisable class`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerialisedElement cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(NotSerialisedElement::class.java)
    }

    @Test
    fun `check CordaNotSerialisable subclass`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SubNotSerialisedElement cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SubNotSerialisedElement::class.java)
    }

    @Test
    fun `check CordaNotSerialisable subsubclass`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SubSubNotSerialisedElement cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SubSubNotSerialisedElement::class.java)
    }

    @Test
    fun `check blacklisted class`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerializable is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(EmptyClassList, GlobalTransientClassList(EmptyClassList))
        (resolver.blacklist as MutableClassList).add(NotSerializable::class.java)
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `blacklisting is always checked before whitelisting`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.NotSerializable is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(GlobalTransientClassList(EmptyClassList), GlobalTransientClassList(EmptyClassList))
        // add to whitelist.
        (resolver.whitelist as MutableClassList).add(NotSerializable::class.java)
        // add to blacklist.
        (resolver.blacklist as MutableClassList).add(NotSerializable::class.java)

        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `blacklisting is always checked beforeCordaSerializable annotated class`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.Element is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(EmptyClassList, GlobalTransientClassList(EmptyClassList))
        (resolver.blacklist as MutableClassList).add(Element::class.java)
        resolver.getRegistration(Element::class.java)
    }

    @Test
    fun `blacklisting is always checked before CordaSerializable annotated subclass`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SubElement is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(EmptyClassList, GlobalTransientClassList(EmptyClassList))
        (resolver.blacklist as MutableClassList).add(SubElement::class.java)
        resolver.getRegistration(SubElement::class.java)
    }

    @Test
    fun `blacklisting is always checked beforeCordaSerializable annotated subsubclass`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SubSubElement is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(EmptyClassList, GlobalTransientClassList(EmptyClassList))
        (resolver.blacklist as MutableClassList).add(SubSubElement::class.java)
        resolver.getRegistration(SubSubElement::class.java)
    }

    @Test
    fun `blacklisting is always checked before CordaSerializable class inherited from interfaces`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SerializableViaInterface is blacklisted and cannot be used in serialization")
        val resolver = CordaClassResolver(EmptyClassList, GlobalTransientClassList(EmptyClassList))
        (resolver.blacklist as MutableClassList).add(SerializableViaInterface::class.java)
        resolver.getRegistration(SerializableViaInterface::class.java)
    }

    @Test
    fun `Elements with both CordaSeriasable and CordaNotSerialisable are not allowed`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.DualElement cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(DualElement::class.java)
    }

    @Test
    fun `CordaSerialisable elements that implement a non serialisable interface are not allowed`() {
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("Class net.corda.core.serialization.SerialisableElementWithNonSerialisableInteface cannot be used in serialization. This class or at least one of its superclasses or implemented interfaces is annotated as CordaNotSerializable and thus, serialization is not permitted")
        CordaClassResolver(EmptyClassList, EmptyClassList).getRegistration(SerialisableElementWithNonSerialisableInteface::class.java)
    }

    @Test
    fun `Annotation is needed for blacklisting`() {

    }
}