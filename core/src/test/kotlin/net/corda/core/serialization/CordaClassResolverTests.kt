package net.corda.core.serialization

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
class Element

abstract class AbstractClass

interface Interface

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
}