Default Class Evolution
=======================

.. contents::

Whilst more complex evolutionary modifications to classes require annotating, Corda's serialization
framework supports several minor modifications to classes without any external modification save
the actual code changes. These are:

    #.  Adding nullable properties
    #.  Adding non nullable properties if, and only if, an annotated constructor is provided
    #.  Removing properties
    #.  Reordering constructor parameters

Adding Nullable Properties
--------------------------

The serialization framework allows nullable properties to be freely added. For example:

.. container:: codeset

   .. sourcecode:: kotlin

        // Initial instance of the class
        data class Example1 (val a: Int, b: String) // (Version A)

        // Class post addition of property c
        data class Example1 (val a: Int, b: String, c: Int?) // (Version B)

A node with version A of class ``Example1``  will be able to deserialize a blob serialized by a node with it
at version B as the framework would treat it as a removed property.

A node with the class at version B will be able to deserialize a serialized version A of ``Example1`` without
any modification as the property is nullable and will thus provide null to the constructor.

Adding Non Nullable Properties
------------------------------

If a non null property is added, unlike nullable properties, some additional code is required for
this to work. Consider a similar example to our nullable example above

.. container:: codeset

   .. sourcecode:: kotlin

        // Initial instance of the class
        data class Example2 (val a: Int, b: String) // (Version A)

        // Class post addition of property c
        data class Example1 (val a: Int, b: String, c: Int) { // (Version B)
             @DeprecatedConstructorForDeserialization(1)
             constructor (a: Int, b: String) : this(a, b, 0) // 0 has been determined as a sensible default
        }

For this to work we have had to add a new constructor that allows nodes with the class at version B to create an
instance from serialised form of that class from an older version, in this case version A as per our example
above. A sensible default for the missing value is provided for instantiation of the non null property.

.. note:: The ``@DeprecatedConstructorForDeserialization`` annotation is important, this signifies to the
    serialization framework that this constructor should be considered for building instances of the
    object when evolution is required.

    Furthermore, the integer parameter passed to the constructor if the annotation indicates a precedence
    order, see the discussion below.

As before, instances of the class at version A will be able to deserialize serialized forms of example B as it
will simply treat them as if the property has been removed (as from its perspective, they will have been).


Constructor Versioning
~~~~~~~~~~~~~~~~~~~~~~

If, over time, multiple non nullable properties are added, then a class will potentially have to be able
to deserialize a number of different forms of the class. Being able to select the correct constructor is
important to ensure the maximum information is extracted.

Consider this example:


.. container:: codeset

   .. sourcecode:: kotlin

        // The original version of the class
        data class Example3 (val a: Int, val b: Int)

.. container:: codeset

   .. sourcecode:: kotlin

        // The first alteration, property c added
        data class Example3 (val a: Int, val b: Int, val c: Int)

.. container:: codeset

   .. sourcecode:: kotlin

        // The second alteration, property d added
        data class Example3 (val a: Int, val b: Int, val c: Int, val d: Int)

.. container:: codeset

   .. sourcecode:: kotlin

        // The third alteration, and how it currently exists, property e added
        data class Example3 (val a: Int, val b: Int, val c: Int, val d: Int, val: Int e) {
            // NOTE: version number purposefully omitted from annotation for demonstration purposes
            @DeprecatedConstructorForDeserialization
            constructor (a: Int, b: Int) : this(a, b, -1, -1, -1)          // alt constructor 1
            @DeprecatedConstructorForDeserialization
            constructor (a: Int, b: Int, c: Int) : this(a, b, c, -1, -1)   // alt constructor 2
            @DeprecatedConstructorForDeserialization
            constructor (a: Int, b: Int, c: Int, d) : this(a, b, c, d, -1) // alt constructor 3
        }

In this case, the deserializer has to be able to deserialize instances of class ``Example3`` that were serialized as, for example:

.. container:: codeset

   .. sourcecode:: kotlin

        Example3 (1, 2)             // example I
        Example3 (1, 2, 3)          // example II
        Example3 (1, 2, 3, 4)       // example III
        Example3 (1, 2, 3, 4, 5)    // example IV

Examples I, II, and III would require evolution and thus selection of constructor. Now, with no versioning applied there
is ambiguity as to which constructor should be used. For example, example II could use 'alt constructor 2' which matches
it's arguments most tightly or 'alt constructor 1' and not instantiate parameter c.

``constructor (a: Int, b: Int, c: Int) : this(a, b, c, -1, -1)``

or

``constructor (a: Int, b: Int) : this(a, b, -1, -1, -1)``

Whilst it may seem trivial which should be picked, it is still ambiguous, thus we use a versioning number in the constructor
annotation which gives a strict precedence order to constructor selection. Therefore, the proper form of the example would
be:

.. container:: codeset

   .. sourcecode:: kotlin

        // The third alteration, and how it currently exists, property e added
        data class Example3 (val a: Int, val b: Int, val c: Int, val d: Int, val: Int e) {
            @DeprecatedConstructorForDeserialization(1)
            constructor (a: Int, b: Int) : this(a, b, -1, -1, -1)          // alt constructor 1
            @DeprecatedConstructorForDeserialization(2)
            constructor (a: Int, b: Int, c: Int) : this(a, b, c, -1, -1)   // alt constructor 2
            @DeprecatedConstructorForDeserialization(3)
            constructor (a: Int, b: Int, c: Int, d) : this(a, b, c, d, -1) // alt constructor 3
        }

Constructors are selected in strict descending order taking the one that enables construction. So, deserializing examples I to IV would
give us:

.. container:: codeset

   .. sourcecode:: kotlin

        Example3 (1, 2, -1, -1, -1) // example I
        Example3 (1, 2, 3, -1, -1)  // example II
        Example3 (1, 2, 3, 4, -1)   // example III
        Example3 (1, 2, 3, 4, 5)    // example IV

Removing Properties
-------------------

Property removal is effectively a mirror of adding properties (both nullable and non nullable) given that this functionality
is required to facilitate the addition of properties. When this state is detected by the serialization framework, properties
that don't have matching parameters in the main constructor are simply omitted from object construction.

.. container:: codeset

   .. sourcecode:: kotlin

        // Initial instance of the class
        data class Example4 (val a: Int?, val b: String?, val c: Int?) // (Version A)


        // Class post removal of property 'a'
        data class Example4 (val b: String?, c: Int?) // (Version B)


In practice, what this means is removing nullable properties is possible. However, removing non nullable properties isn't because
a node receiving a message containing a serialized form of an object with fewer properties than it requires for construction has
no capacity to guess at what values should or could be used as sensible defaults. When those properties are nullable it simply sets
them to null.

Reordering Constructor Parameter Order
--------------------------------------

Properties (in Kotlin this corresponds to constructor parameters) may be reordered freely. The evolution serializer will create a
mapping between how a class was serialized and its current constructor parameter order. This is important to our AMQP framework as it
constructs objects using their primary (or annotated) constructor. The ordering of whose parameters will have determined the way
an object's properties were serialised into the byte stream.

For an illustrative example consider a simple class:

.. Container:: codeset

    .. sourcecode:: kotlin

        data class Example5 (val a: Int, val b: String)

        val e = Example5(999, "hello")

When we serialize ``e`` its properties will be encoded in order of its primary constructors parameters, so:

``999,hello``

Were those parameters to be reordered post serialisation then deserializing, without evolution, would fail with a basic
type error as we'd attempt to create the new value of ``Example5`` with the values provided in the wrong order:

.. Container:: codeset

    .. sourcecode:: kotlin

        // changed post serialisation
        data class Example5 (val b: String, val a: Int)

    .. sourcecode:: shell

        | 999 | hello |  <--- Extract properties to pass to constructor from byte stream
           |      |
           |      +--------------------------+  
           +--------------------------+      |
                                      |      |
        deserializedValue = Example5(999, "hello")  <--- Resulting attempt at construction
                                      |      |
                                      |      \
                                      |       \     <--- Will clearly fail as 999 is not a
                                      |        \         string and hello is not an integer
        data class Example5 (val b: String, val a: Int)

