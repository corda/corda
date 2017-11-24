Pluggable Serializers for CorDapps
==================================
To be serializable by Corda Java classes must be compiled with the -parameter switch to enable matching of it's properties
to constructor parameters. This is important because Corda's internal AMQP serialization scheme will only constuct
objects using their constructors. However, when recompilation isn't possible, or classes are built in such a way that
they cannot be easily modified for simple serailization, CorDapps can provide custom proxy serializers that Corda
can use to move from types it cannot serializer to an interim representation that it can with the transformation to and
from this proxy object being handled by the supplied serializer.

Serializer Location
-------------------
Custom serializers should be placed in the plugins directory of a CorDapp or a sub directory thereof. These
classes will be scanned and loaded by the CorDapp loading process.

Writing a Custom Serializer
---------------------------
Serializers must
 * Inherit from net.corda.core.serialization.SerializationCustomSerializer
 * Be annotated with the @CordaCustomSerializer annotation
 * Provide a proxy class to transform the object to and from
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

Without a custom serializer we cannot serialise this class as there is no public constructor that facilitates the
initialisation of al of its's properties.

To be serializable by Corda this would require a custom serializer as follows

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

Whitelisting
------------
By writing a custom serializer for a class it has the effect of adding that class to the whitelist, meaning such
classes don't need explicitly adding to the CorDapp's whitelist


