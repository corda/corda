.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Enum Evolution
==============

.. contents::

In the continued development of a CorDapp an enumerated type that was fit for purpose at one time may
require changing. Normally, this would be problematic as anything serialised (and kept in a vault) would
run the risk of being unable to be deserialized in the future or older versions of the app still alive
within a compatibility zone may fail to deserialize a message.

To facilitate backward and forward support for alterations to enumerated types Corda's serialization
framework supports the evolution of such types through a well defined framework that allows different
versions to interoperate with serialised versions of an enumeration of differing versions.

This is achieved through the use of certain annotations. Whenever a change is made, an annotation
capturing the change must be added (whilst it can be omitted any interoperability will be lost). Corda
supports two modifications to enumerated types, adding new constants, and renaming existing constants

.. warning:: Once added evolution annotations MUST NEVER be removed from a class, doing so will break
    both forward and backward compatibility for this version of the class and any version moving
    forward

The Purpose of Annotating Changes
---------------------------------

The biggest hurdle to allowing enum constants to be changed is that there will exist instances of those
classes, either serialized in a vault or on nodes with the old, unmodified, version of the class that we
must be able to interoperate with. Thus if a received data structure references an enum assigned a constant
value that doesn't exist on the running JVM, a solution is needed.

For this, we use the annotations to allow developers to express their backward compatible intentions.

In the case of renaming constants this is somewhat obvious, the deserializing node will simply treat any
constants it doesn't understand as their "old" values, i.e. those values that it currently knows about.

In the case of adding new constants the developer must chose which constant (that existed *before* adding
the new one) a deserializing system should treat any instances of the new one as.

.. note:: Ultimately, this may mean some design compromises are required. If an enumeration is
    planned as being often extended and no sensible defaults will exist then including a constant
    in the original version of the class that all new additions can default to may make sense

Evolution Transmission
----------------------

An object serializer, on creation, will inspect the class it represents for any evolution annotations.
If a class is thus decorated those rules will be encoded as part of any serialized representation of a
data structure containing that class. This ensures that on deserialization the deserializing object will
have access to any transformative rules it needs to build a local instance of the serialized object.

Evolution Precedence
--------------------

On deserialization (technically on construction of a serialization object that facilitates serialization
and deserialization) a class's fingerprint is compared to the fingerprint received as part of the AMQP
header of the corresponding class. If they match then we are sure that the two class versions are functionally
the same and no further steps are required save the deserialization of the serialized information into an instance
of the class.

If, however, the fingerprints differ then we know that the class we are attempting to deserialize is different
than the version we will be deserializing it into. What we cannot know is which version is newer, at least
not by examining the fingerprint

.. note:: Corda's AMQP fingerprinting for enumerated types include the type name and the enum constants

Newer vs older is important as the deserializer needs to use the more recent set of transforms to ensure it
can transform the serialised object into the form as it exists in the deserializer. Newness is determined simply
by length of the list of all transforms. This is sufficient as transform annotations should only ever be added

.. warning:: technically there is nothing to prevent annotations being removed in newer versions. However,
    this will break backward compatibility and should thus be avoided unless a rigorous upgrade procedure
    is in place to cope with all deployed instances of the class and all serialised versions existing
    within vaults.

Thus, on deserialization, there will be two options to chose from in terms of transformation rules

    #.  Determined from the local class and the annotations applied to it (the local copy)
    #.  Parsed from the AMQP header (the remote copy)

Which set is used will simply be the largest.

Renaming Constants
------------------

Renamed constants are marked as such with the ``@CordaSerializationTransformRenames`` meta annotation that
wraps a list of ``@CordaSerializationTransformRename`` annotations. Each rename requiring an instance in the
list.

Each instance must provide the new name of the constant as well as the old. For example, consider the following enumeration:

.. container:: codeset

   .. sourcecode:: kotlin

        enum class Example {
            A, B, C
        }

If we were to rename constant C to D this would be done as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        @CordaSerializationTransformRenames (
            CordaSerializationTransformRename("D", "C")
        )
        enum class Example {
            A, B, D
        }

.. note:: The parameters to the ``CordaSerializationTransformRename`` annotation are defined as 'to' and 'from,
    so in the above example it can be read as constant D (given that is how the class now exists) was renamed
    from C

In the case where a single rename has been applied the meta annotation may be omitted. Thus, the following is
functionally identical to the above:

.. container:: codeset

   .. sourcecode:: kotlin

        @CordaSerializationTransformRename("D", "C")
        enum class Example {
            A, B, D
        }

However, as soon as a second rename is made the meta annotation must be used. For example, if at some time later
B is renamed to E:

.. container:: codeset

   .. sourcecode:: kotlin

        @CordaSerializationTransformRenames (
            CordaSerializationTransformRename(from = "B", to = "E"),
            CordaSerializationTransformRename(from = "C", to = "D")
        )
        enum class Example {
            A, E, D
        }

Rules
~~~~~

    #.  A constant cannot be renamed to match an existing constant, this is enforced through language constraints
    #.  A constant cannot be renamed to a value that matches any previous name of any other constant

If either of these covenants are inadvertently broken, a ``NotSerializableException`` will be thrown on detection
by the serialization engine as soon as they are detected. Normally this will be the first time an object doing
so is serialized. However, in some circumstances, it could be at the point of deserialization.

Adding Constants
----------------

Enumeration constants can be added with the ``@CordaSerializationTransformEnumDefaults`` meta annotation that
wraps a list of ``CordaSerializationTransformEnumDefault`` annotations. For each constant added an annotation
must be included that signifies, on deserialization, which constant value should be used in place of the
serialised property if that value doesn't exist on the version of the class as it exists on the deserializing
node.

.. container:: codeset

   .. sourcecode:: kotlin

        enum class Example {
            A, B, C
        }

If we were to add the constant D

.. container:: codeset

   .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("D", "C")
        )
        enum class Example {
            A, B, C, D
        }

.. note:: The parameters to the ``CordaSerializationTransformEnumDefault`` annotation are defined as 'new' and 'old',
    so in the above example it can be read as constant D should be treated as constant C if you, the deserializing
    node, don't know anything about constant D

.. note:: Just as with the ``CordaSerializationTransformRename`` transformation if a single transform is being applied
    then the meta transform may be omitted.

    .. container:: codeset

       .. sourcecode:: kotlin

            @CordaSerializationTransformEnumDefault("D", "C")
            enum class Example {
                A, B, C, D
            }

New constants may default to any other constant older than them, including constants that have also been added
since inception. In this example, having added D (above) we add the constant E and chose to default it to D

.. container:: codeset

   .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("E", "D"),
            CordaSerializationTransformEnumDefault("D", "C")
        )
        enum class Example {
            A, B, C, D, E
        }

.. note:: Alternatively, we could have decided both new constants should have been defaulted to the first
    element

    .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("E", "A"),
            CordaSerializationTransformEnumDefault("D", "A")
        )
        enum class Example {
            A, B, C, D, E
        }

When deserializing the most applicable transform will be applied. Continuing the above example, deserializing
nodes could have three distinct views on what the enum Example looks like (annotations omitted for brevity)

.. container:: codeset

   .. sourcecode:: kotlin

        // The original version of the class. Will deserialize: -
        //   A -> A  
        //   B -> B
        //   C -> C  
        //   D -> C  
        //   E -> C  
        enum class Example {
            A, B, C
        }

   .. sourcecode:: kotlin

        // The class as it existed after the first addition. Will deserialize:
        //   A -> A  
        //   B -> B
        //   C -> C  
        //   D -> D  
        //   E -> D  
        enum class Example {
            A, B, C, D
        }

   .. sourcecode:: kotlin

        // The current state of the class. All values will deserialize as themselves
        enum class Example {
            A, B, C, D, E
        }

Thus, when deserializing a value that has been encoded as E could be set to one of three constants (E, D, and C)
depending on how the deserializing node understands the class.

Rules
~~~~~

    #.  New constants must be added to the end of the existing list of constants
    #.  Defaults can only be set to "older" constants, i.e. those to the left of the new constant in the list
    #.  Constants must never be removed once added
    #.  New constants can be renamed at a later date using the appropriate annotation
    #.  When renamed, if a defaulting annotation refers to the old name, it should be left as is

Combining Evolutions
---------------------

Renaming constants and adding constants can be combined over time as a class changes freely. Added constants can
in turn be renamed and everything will continue to be deserializeable. For example, consider the following enum:

.. container:: codeset

    .. sourcecode:: kotlin

        enum class OngoingExample { A, B, C }

For the first evolution, two constants are added, D and E, both of which are set to default to C when not present

.. container:: codeset

    .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("E", "C"),
            CordaSerializationTransformEnumDefault("D", "C")
        )
        enum class OngoingExample { A, B, C, D, E }

Then lets assume constant C is renamed to CAT

.. container:: codeset

    .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("E", "C"),
            CordaSerializationTransformEnumDefault("D", "C")
        )
        @CordaSerializationTransformRename("C", "CAT")
        enum class OngoingExample { A, B, CAT, D, E }

Note how the first set of modifications still reference C, not CAT. This is as it should be and will
continue to work as expected.

Subsequently is is fine to add an additional new constant that references the renamed value.

.. container:: codeset

    .. sourcecode:: kotlin

        @CordaSerializationTransformEnumDefaults (
            CordaSerializationTransformEnumDefault("F", "CAT"),
            CordaSerializationTransformEnumDefault("E", "C"),
            CordaSerializationTransformEnumDefault("D", "C")
        )
        @CordaSerializationTransformRename("C", "CAT")
        enum class OngoingExample { A, B, CAT, D, E, F }

Unsupported Evolutions
----------------------

The following evolutions are not currently supports

    #.  Removing constants
    #.  Reordering constants
