Pluggable Serializers for CorDapps
==================================

To be serializable by Corda Java classes must be compiled with the -parameter switch to enable mathcing of ass property
to constructor parameter. However, when this isn't possible CorDapps can provide custom proxy serialiszers that Corda
can use to move from types it cannot serialiser to an interim represtnation that it can with the transformation to and
from this proxy object being handled by the supplied serialiser.

Serializer Location
-------------------
Custom serializers should be placed in the plugins directory fo a CorDapp or a sub directory (placing it in a sub
directory however does require that directory be added to the list of locations scanned within the jar)

Writing a Custom Serializer
--------------------------

Serializers must
 * Inherit from net.corda.core.serialization.SerializationCustomSerializer
 * Be annotated with the @CordaCustomSerializer annotation
 * Provide a proxy class to transform the objectto and from
 * Have that proxy class annotated with the @CordaCustomSerializerProxy annotation

Serializers inheriting from SerializationCustomSerializer have to implement two methods and two types

Example
-------

Consider this example class

.. sourcecode:: java
    public final class Example {
        private final Int a
        private final Int b

        private Example(Int a, Int b) {
            this.a = a;
            this.b = b;
        }

        public static Example of (int[] a) { return Example(a[0], a[1]); }

        public int getA() { return a; }
        public int getB() { return b; }
    }

This would require a serialiser as follows

.. sourcecode:: kotlin
    @CordaCustomSerializer
    class ExampleSerializer : SerializationCustomSerializer {
        @CordaCustomSerializerProxy
        data class Proxy(val a: Int, val b: Int)

        override fun toProxy(obj: Any): Any = Proxy((obj as Example).a, obj.b)

        override fun fromProxy(proxy: Any): Any {
            val constructorArg = IntArray(2);
            constructorArg[0] = (proxy as Proxy).a
            constructorArg[1] = proxy.b
            return Example.create(constructorArg)
        }

        override val type: Type get() = Example::class.java
        override val ptype: Type get() = Proxy::class.java
    }




