.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Object serialization
====================

.. contents::

Introduction
------------

Object serialization is the process of converting objects into a stream of bytes and, deserialization, the reverse
process of creating objects from a stream of bytes.  It takes place every time nodes pass objects to each other as
messages, when objects are sent to or from RPC clients from the node, and when we store transactions in the database.

Corda pervasively uses a custom form of type safe binary serialisation. This stands in contrast to some other systems that use
weakly or untyped string-based serialisation schemes like JSON or XML. The primary drivers for this were:

*  A desire to have a schema describing what has been serialized alongside the actual data:

    #.  To assist with versioning, both in terms of being able to interpret data archived long ago (e.g. trades from
        a decade ago, long after the code has changed) and between differing code versions.
    #.  To make it easier to write generic code e.g. user interfaces that can navigate the serialized form of data.
    #.  To support cross platform (non-JVM) interaction, where the format of a class file is not so easily interpreted.

*  A desire to use a documented and static wire format that is platform independent, and is not subject to change with
   3rd party library upgrades, etc.
*  A desire to support open-ended polymorphism, where the number of subclasses of a superclass can expand over time
   and the subclasses do not need to be defined in the schema *upfront*. This is key to many Corda concepts, such as states.
*  Increased security by constructing deserialized objects through supported constructors, rather than having
   data inserted directly into their fields without an opportunity to validate consistency or intercept attempts to manipulate
   supposed invariants.
*  Binary formats work better with digital signatures than text based formats, as there's much less scope for
   changes that modify syntax but not semantics.

Whitelisting
------------

In classic Java serialization, any class on the JVM classpath can be deserialized.  This has shown to be a source of exploits
and vulnerabilities by exploiting the large set of 3rd party libraries on the classpath as part of the dependencies of
a JVM application and a carefully crafted stream of bytes to be deserialized. In Corda, we prevent just any class from
being deserialized (and pro-actively during serialization) by insisting that each object's class belongs on a whitelist
of allowed classes.

Classes get onto the whitelist via one of three mechanisms:

#. Via the ``@CordaSerializable`` annotation.  In order to whitelist a class, this annotation can be present on the
   class itself, on any of the super classes or on any interface implemented by the class or super classes or any
   interface extended by an interface implemented by the class or superclasses.
#. By implementing the ``SerializationWhitelist`` interface and specifying a list of `whitelist` classes.
#. Via the built in Corda whitelist (see the class ``DefaultWhitelist``).  Whilst this is not user editable, it does list
   common JDK classes that have been whitelisted for your convenience.

The annotation is the preferred method for whitelisting.  An example is shown in :doc:`tutorial-clientrpc-api`.
It's reproduced here as an example of both ways you can do this for a couple of example classes.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 7
    :end-before: END 7

.. note:: Several of the core interfaces at the heart of Corda are already annotated and so any classes that implement
   them will automatically be whitelisted.  This includes ``Contract``, ``ContractState`` and ``CommandData``.

.. warning:: Java 8 Lambda expressions are not serializable except in flow checkpoints, and then not by default. The syntax to declare a serializable Lambda
   expression that will work with Corda is ``Runnable r = (Runnable & Serializable) () -> System.out.println("Hello World");``, or
   ``Callable<String> c = (Callable<String> & Serializable) () -> "Hello World";``.

AMQP
----

Corda uses an extended form of AMQP 1.0 as its binary wire protocol.

Corda serialisation is currently used for:

    #.  Peer-to-peer networking.
    #.  Persisted messages, like signed transactions and states.

.. note:: At present, the Kryo-based format is still used by the RPC framework on both the client and server side. However, it is planned that the RPC framework will move to the AMQP framework soon.

For the checkpointing of flows Corda uses a private scheme that is subject to change. It is currently based on the Kryo
framework, but this may not be true in future.

This separation of serialization schemes into different contexts allows us to use the most suitable framework for that context rather than
attempting to force a one-size-fits-all approach. Kryo is more suited to the serialization of a program's stack frames, as it is more flexible
than our AMQP framework in what it can construct and serialize. However, that flexibility makes it exceptionally difficult to make secure. Conversely,
our AMQP framework allows us to concentrate on a secure framework that can be reasoned about and thus made safer, with far fewer
security holes.

Selection of serialization context should, for the most part, be opaque to CorDapp developers, the Corda framework selecting
the correct context as configured.

This document describes what is currently and what will be supported in the Corda AMQP format from the perspective
of CorDapp developers, to allow CorDapps to take into consideration the future state.  The AMQP serialization format will
continue to apply the whitelisting functionality that is already in place and described in :doc:`serialization`.

Core Types
----------

This section describes the classes and interfaces that the AMQP serialization format supports.

Collection Types
````````````````

The following collection types are supported.  Any implementation of the following will be mapped to *an* implementation
of the interface or class on the other end. For example, if you use a Guava implementation of a collection, it will
deserialize as the primitive collection type.

The declared types of properties should only use these types, and not any concrete implementation types (e.g.
Guava implementations). Collections must specify their generic type, the generic type parameters will be included in
the schema, and the element's type will be checked against the generic parameters when deserialized.

::

    java.util.Collection
    java.util.List
    java.util.Set
    java.util.SortedSet
    java.util.NavigableSet
    java.util.NonEmptySet
    java.util.Map
    java.util.SortedMap
    java.util.NavigableMap

However, as a convenience, we explicitly support the concrete implementation types below, and they can be used as the
declared types of properties.

::

    java.util.LinkedHashMap
    java.util.TreeMap
    java.util.EnumSet
    java.util.EnumMap (but only if there is at least one entry)


JVM primitives
``````````````

All the primitive types are supported.

::

    boolean
    byte
    char
    double
    float
    int
    long
    short

Arrays
``````

Arrays of any type are supported, primitive or otherwise.

JDK Types
`````````

The following JDK library types are supported:

::

    java.io.InputStream

    java.lang.Boolean
    java.lang.Byte
    java.lang.Character
    java.lang.Class
    java.lang.Double
    java.lang.Float
    java.lang.Integer
    java.lang.Long
    java.lang.Short
    java.lang.StackTraceElement
    java.lang.String
    java.lang.StringBuffer

    java.math.BigDecimal

    java.security.PublicKey

    java.time.DayOfWeek
    java.time.Duration
    java.time.Instant
    java.time.LocalDate
    java.time.LocalDateTime
    java.time.LocalTime
    java.time.Month
    java.time.MonthDay
    java.time.OffsetDateTime
    java.time.OffsetTime
    java.time.Period
    java.time.YearMonth
    java.time.Year
    java.time.ZonedDateTime
    java.time.ZonedId
    java.time.ZoneOffset

    java.util.BitSet
    java.util.Currency
    java.util.UUID

Third-Party Types
`````````````````

The following 3rd-party types are supported:

::

    kotlin.Unit
    kotlin.Pair

    org.apache.activemq.artemis.api.core.SimpleString

Corda Types
```````````

Any classes and interfaces in the Corda codebase annotated with ``@CordaSerializable`` are supported.

All Corda exceptions that are expected to be serialized inherit from ``CordaThrowable`` via either ``CordaException`` (for
checked exceptions) or ``CordaRuntimeException`` (for unchecked exceptions).  Any ``Throwable`` that is serialized but does
not conform to ``CordaThrowable`` will be converted to a ``CordaRuntimeException``, with the original exception type
and other properties retained within it.

Custom Types
------------

You own types must adhere to the following rules to be supported:

Classes
```````

General Rules
'''''''''''''

    #.  The class must be compiled with parameter names included in the ``.class`` file.  This is the default in Kotlin
        but must be turned on in Java using the ``-parameters`` command line option to ``javac``

        .. note:: In circumstances where classes cannot be recompiled, such as when using a third-party library, a
           proxy serializer can be used to avoid this problem. Details on creating such an object can be found on the
           :doc:`cordapp-custom-serializers` page.

    #.  The class must be annotated with ``@CordaSerializable``
    #.  The declared types of constructor arguments, getters, and setters must be supported, and where generics are used, the
        generic parameter must be a supported type, an open wildcard (``*``), or a bounded wildcard which is currently
        widened to an open wildcard
    #.  Any superclass must adhere to the same rules, but can be abstract
    #.  Object graph cycles are not supported, so an object cannot refer to itself, directly or indirectly

Constructor Instantiation
'''''''''''''''''''''''''

The primary way Corda's AMQP serialization framework instantiates objects is via a specified constructor. This is
used to first determine which properties of an object are to be serialised, then, on deserialization, it is used to
instantiate the object with the serialized values.

It is recommended that serializable objects in Corda adhere to the following rules, as they allow immutable state
objects to be deserialised:

    #.  A Java Bean getter for each of the properties in the constructor, with a name of the form ``getX``.  For example, for a constructor
        parameter ``foo``, there must be a getter called ``getFoo()``.  If ``foo`` is a boolean, the getter may
        optionally be called ``isFoo()`` (this is why the class must be compiled with parameter names turned on)
    #.  A constructor which takes all of the properties that you wish to record in the serialized form.  This is required in
        order for the serialization framework to reconstruct an instance of your class
    #.  If more than one constructor is provided, the serialization framework needs to know which one to use.  The ``@ConstructorForDeserialization``
        annotation can be used to indicate which one.  For a Kotlin class, without the ``@ConstructorForDeserialization`` annotation, the
        *primary constructor* will be selected

In Kotlin, this maps cleanly to a data class where there getters are synthesized automatically. For example, suppose we
have the following data class:

.. container:: codeset

    .. sourcecode:: kotlin

        data class Example (val a: Int, val b: String)

Properties ``a`` and ``b`` will be included in the serialised form.

However, properties not mentioned in the constructor will not be serialised. For example, in the following code,
property ``c`` will not be considered part of the serialised form:

.. container:: codeset

    .. sourcecode:: kotlin

        data class Example (val a: Int, val b: String) {
            var c: Int = 20
        }

        var e = Example (10, "hello")
        e.c = 100;

        val e2 = e.serialize().deserialize() // e2.c will be 20, not 100!!!

Setter Instantiation
''''''''''''''''''''

As an alternative to constructor-based initialisation, Corda can also determine the important elements of an
object by inspecting the getter and setter methods present on the class. If a class has **only** a default
constructor **and** properties then the serializable properties will be determined by the presence of
both a getter and setter for that property that are both publicly visible (i.e. the class adheres to
the classic *idiom* of mutable JavaBeans).

On deserialization, a default instance will first be created, and then the setters will be invoked on that object to
populate it with the correct values.

For example:

.. container:: codeset

   .. sourcecode:: kotlin

      class Example(var a: Int, var b: Int, var c: Int)

   .. sourcecode:: java

      class Example {
          private int a;
          private int b;
          private int c;

          public int getA() { return a; }
          public int getB() { return b; }
          public int getC() { return c; }

          public void setA(int a) { this.a = a; }
          public void setB(int b) { this.b = b; }
          public void setC(int c) { this.c = c; }
      }

.. warning:: We do not recommend this pattern! Corda tries to use immutable data structures throughout, and if you
   rely heavily on mutable JavaBean style objects then you may sometimes find the API behaves in unintuitive ways.

Inaccessible Private Properties
```````````````````````````````

Whilst the Corda AMQP serialization framework supports private object properties without publicly
accessible getter methods, this development idiom is strongly discouraged.

For example.

.. container:: codeset

    .. sourcecode:: kotlin

       class C(val a: Int, private val b: Int)

    .. sourcecode:: java

       class C {
           public Integer a;
           private Integer b;

           public C(Integer a, Integer b) {
               this.a = a;
               this.b = b;
           }
       }

When designing Corda states, it should be remembered that they are not, despite appearances, traditional
OOP style objects. They are signed over, transformed, serialised, and relationally mapped. As such,
all elements should be publicly accessible by design.

.. warning:: IDEs will indicate erroneously that properties can be given something other than public visibility. Ignore
   this, as whilst it will work, as discussed above there are many reasons why this isn't a good idea.

Providing a public getter, as per the following example, is acceptable:

.. container:: codeset

   .. sourcecode:: kotlin

      class C(val a: Int, b: Int) {
          var b: Int = b
             private set
      }

   .. sourcecode:: java

      class C {
          public Integer a;
          private Integer b;

          C(Integer a, Integer b) {
              this.a = a;
              this.b = b;
          }

          public Integer getB() {
              return b;
          }
      }

Mismatched Class Properties / Constructor Parameters
````````````````````````````````````````````````````

Consider an example where you wish to ensure that a property of class whose type is some form of container is always sorted using some specific criteria yet you wish to maintain the immutability of the class.

This could be codified as follows:

.. container:: codeset

    .. sourcecode:: kotlin

        @CordaSerializable
        class ConfirmRequest(statesToConsume: List<StateRef>, val transactionId: SecureHash) {
            companion object {
                private val stateRefComparator = compareBy<StateRef>({ it.txhash }, { it.index })
            }

            private val states = statesToConsume.sortedWith(stateRefComparator)
        }

The intention in the example is to always ensure that the states are stored in a specific order regardless of the ordering
of the list used to initialise instances of the class. This is achieved by using the first constructor parameter as the
basis for a private member. However, because that member is not mentioned in the constructor (whose parameters determine
what is serializable as discussed above) it would not be serialized. In addition, as there is no provided mechanism to retrieve
a value for ``statesToConsume`` we would fail to build a serializer for this Class.

In this case a secondary constructor annotated with ``@ConstructorForDeserialization`` would not be a valid solution as the
two signatures would be the same. Best practice is thus to provide a getter for the constructor parameter which explicitly
associates it with the actual member variable.

.. container:: codeset

    .. sourcecode:: kotlin

        @CordaSerializable
        class ConfirmRequest(statesToConsume: List<StateRef>, val transactionId: SecureHash) {
            companion object {
                private val stateRefComparator = compareBy<StateRef>({ it.txhash }, { it.index })
            }

            private val states = statesToConsume.sortedWith(stateRefComparator)

            //Explicit "getter" for a property identified from the constructor parameters
            fun getStatesToConsume() = states
        }

Mutable Containers
``````````````````

Because Java fundamentally provides no mechanism by which the mutability of a class can be determined this presents a
problem for the serialization framework. When reconstituting objects with container properties (lists, maps, etc) we
must chose whether to create mutable or immutable objects. Given the restrictions, we have decided it is better to
preserve the immutability of immutable objects rather than force mutability on presumed immutable objects.

.. note:: Whilst we could potentially infer mutability empirically, doing so exhaustively is impossible as it's a design
  decision rather than something intrinsic to the JVM. At present, we defer to simply making things immutable on reconstruction
  with the following workarounds provided for those who use them. In future, this may change, but for now use the following
  examples as a guide.

For example, consider the following:

.. sourcecode:: kotlin

    data class C(val l : MutableList<String>)

    val bytes = C(mutableListOf ("a", "b", "c")).serialize()
    val newC = bytes.deserialize()

    newC.l.add("d")

The call to ``newC.l.add`` will throw an ``UnsupportedOperationException``.

There are several workarounds that can be used to preserve mutability on reconstituted objects. Firstly, if the class
isn't a Kotlin data class and thus isn't restricted by having to have a primary constructor.

.. sourcecode:: kotlin

    class C {
        val l : MutableList<String>

        @Suppress("Unused")
        constructor (l : MutableList<String>) {
            this.l = l.toMutableList()
        }
    }

    val bytes = C(mutableListOf ("a", "b", "c")).serialize()
    val newC = bytes.deserialize()

    // This time this call will succeed
    newC.l.add("d")

Secondly, if the class is a Kotlin data class, a secondary constructor can be used.

.. sourcecode:: kotlin

    data class C (val l : MutableList<String>){
        @ConstructorForDeserialization
        @Suppress("Unused")
        constructor (l : Collection<String>) : this (l.toMutableList())
    }

    val bytes = C(mutableListOf ("a", "b", "c")).serialize()
    val newC = bytes.deserialize()

    // This will also work
    newC.l.add("d")

Thirdly, to preserve immutability of objects (a recommend design principle - Copy on Write semantics) then mutating the
contents of the class can be done by creating a new copy of the data class with the altered list passed (in this example)
passed in as the Constructor parameter.

.. sourcecode:: kotlin

    data class C(val l : List<String>)

    val bytes = C(listOf ("a", "b", "c")).serialize()
    val newC = bytes.deserialize()

    val newC2 = newC.copy (l = (newC.l + "d"))

.. note:: If mutability isn't an issue at all then in the case of data classes a single constructor can
  be used by making the property var instead of val and in the ``init`` block reassigning the property
  to a mutable instance

Enums
`````

All enums are supported, provided they are annotated with ``@CordaSerializable``. Corda supports interoperability of
enumerated type versions. This allows such types to be changed over time without breaking backward (or forward)
compatibility. The rules and mechanisms for doing this are discussed in :doc:`serialization-enum-evolution`.

Exceptions
``````````

The following rules apply to supported ``Throwable`` implementations.

    #.  If you wish for your exception to be serializable and transported type safely it should inherit from either
        ``CordaException`` or ``CordaRuntimeException``
    #.  If not, the ``Throwable`` will deserialize to a ``CordaRuntimeException`` with the details of the original
        ``Throwable`` contained within it, including the class name of the original ``Throwable``

Kotlin Objects
``````````````

Kotlin's non-anonymous ``object`` s (i.e. constructs like ``object foo : Contract {...}``) are singletons and
treated differently.  They are recorded into the stream with no properties, and deserialize back to the
singleton instance. Currently, the same is not true of Java singletons, which will deserialize to new instances
of the class. This is hard to fix because there's no perfectly standard idiom for Java singletons.

Kotlin's anonymous ``object`` s (i.e. constructs like ``object : Contract {...}``) are not currently supported
and will not serialize correctly. They need to be re-written as an explicit class declaration.

Class synthesis
---------------

Corda serialization supports dynamically synthesising classes from the supplied schema when deserializing,
without the supporting classes being present on the classpath.  This can be useful where generic code might expect to
be able to use reflection over the deserialized data, for scripting languages that run on the JVM, and also for
ensuring classes not on the classpath can be deserialized without loading potentially malicious code.

Possible future enhancements include:

    #.  Java singleton support.  We will add support for identifying classes which are singletons and identifying the
        static method responsible for returning the singleton instance
    #.  Instance internalizing support.  We will add support for identifying classes that should be resolved against an instances map to avoid
        creating many duplicate instances that are equal (similar to ``String.intern()``)

.. Type Evolution:

Type Evolution
--------------

Type evolution is the mechanism by which classes can be altered over time yet still remain serializable and deserializable across
all versions of the class. This ensures an object serialized with an older idea of what the class "looked like" can be deserialized
and a version of the current state of the class instantiated.

More detail can be found in :doc:`serialization-default-evolution`.

