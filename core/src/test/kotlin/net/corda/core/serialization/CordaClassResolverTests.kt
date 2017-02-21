package net.corda.core.serialization

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.core.node.AttachmentClassLoaderTests
import org.junit.Test
import java.net.URLClassLoader

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
class Element

abstract class AbstractClass

interface Interface

class NotSerializable

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
        CordaClassResolver(EmptyWhitelist()).getRegistration(Foo.Bar::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(EmptyWhitelist()).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method does not consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(EmptyWhitelist()), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(GlobalTransientClassWhiteList(EmptyWhitelist()))
        (resolver.whitelist as MutableClassWhitelist).add(NotSerializable::class.java)
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(EmptyWhitelist()).getRegistration(DefaultSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        class ClassLoaderForTests : URLClassLoader(arrayOf(AttachmentClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH), AttachmentClassLoaderTests.FilteringClassLoader)

        val attachedClass = Class.forName("net.corda.contracts.isolated.AnotherDummyContract", true, ClassLoaderForTests())
        CordaClassResolver(EmptyWhitelist()).getRegistration(attachedClass)
    }
}