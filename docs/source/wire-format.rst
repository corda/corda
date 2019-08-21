Wire format
===========

This document describes the Corda wire format. With the following information and an implementation of the AMQP/1.0
specification, you can read Corda serialised binary messages. An example implementation of AMQP/1.0 would be Apache
Qpid Proton, or Microsoft AMQP.NET Lite.

Header
------

All messages start with the 5 byte sequence ``corda`` followed by three versioning bytes: major, minor and encoding.
That means you can't directly feed a Corda message into an AMQP library. You must check the header string and
then skip it. This is deliberate, to enable other message formats in future.

The first version byte is set to 1 and indicates the major version of the format. It should always be set to 1,
if it isn't that means a backwards incompatible serialisation format has been developed and you should therefore abort.
The second byte is a minor version, you should be able to tolerate this incrementing as long as your code is robust
to unknown data (e.g. new schema elements). The third byte is an encoding byte. This is used to indicate new features
like compression are active. You should abort if this isn't zero.

AMQP intro
----------

AMQP/1.0 (which is quite different to AMQP/0.9) is protocol that contains a standardised binary encoding scheme, comparable to but
more advanced than Google protocol buffers. `The AMQP specification <https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-types-v1.0-os.html>`_
is quite concise and easy to read: this document will reference it in many places. It also provides a variety of encoded examples
that can be used to understand each byte of a message.

The format specifies encodings for several 'primitive' types: numbers, strings, UUIDs, timestamps
and symbols (these can be thought of as enum entries). It also defines how to encode maps, lists and arrays. The difference
between the latter two is that arrays always contain a single type, whereas lists can contain elements of different types.
An AMQP byte stream is simply a repeated series of elements.

So far, so standard. However AMQP goes further than most such tagged binary encodings by including the concept of
*described types*. This is a way to impose an application-level type system on top of the basic "bags of elements"
that low-level AMQP gives you. Any element in the stream can be prefixed with a *descriptor*, which is either a string
or a 64 bit value. Both types of label have a defined namespacing mechanism. This labelling scheme allows sophisticated
layerings to be added on top of the simple, interoperable core.

AMQP therefore also defines a type system and schema representation, that allows you to create the app-level type layer.
Standard AMQP defines an XML based schema language as part of the specification, but doesn't define any way to represent
schemas using AMQP itself. Fields can be grouped together using *composite types*. A composite type is simply a
described list, in which each list entry is one field of the composite. Composites are used to encode language-level
classes, records, structs etc.

You can also define in a *restricted type*, which can be used to define a new type that is a specialisation or subset of
an existing one. For enumerations the choices can be listed in the schema.

Due to this design you can think of a serialised message as being interpretable at several levels of detail.
You can parse it just using the basic AMQP type system, which will give you nested lists and maps containing a few basic
types. This is similar to what JSON would give you. Or you can utilise the descriptors and map those containers to higher
level, more strongly typed structures.

Extended AMQP
-------------

So far we've got collections that contain primitives or more collections, and any element can be labelled with a
string or numeric code. This is good, but compared to a format like JSON or XML it's not really self describing.
A class will be mapped to a list of field contents. Even if we know the name of that class, we still won't really know
what the fields mean without having access to the original code of the class that the message was generated from.

AMQP's type system can solve this, however, out of the box there are two problems:

1. Messages don't include their own schemas.
2. AMQP only defines an XML based representation for schemas.

We'd rather not embed XML inside a binary format designed to be digitally signed, so we have defined a straightforward
mapping from this schema notation to AMQP encoding itself. This makes our AMQP messages self describing, by embedding a
schema for each application or platform level type that is serialised. The schema provides information like field names,
annotations and type variables for generic types. The schema can of course be ignored in many interop cases: it's there
to enable version evolution of persisted data structures over time.

.. note:: It is a deliberate choice to sacrifice encoding efficiency for self-description: we prefer to pay more now than risk
   having data on the ledger later on that's hard to read due to loss of (old versions of) applications. The intention is
   that a mix of compression and separating the schema parts out when both sides already agree on what they are will return
   most of the lost efficiency.

Descriptors
-----------

Serialised messages use described types extensively. There are two types of descriptor:

1. 64 bit code. In Corda, the top 32 bits are always equal to 0x0000c562 which is R3's IANA assigned enterprise number. The
   low bits define various elements in our meta-schema (i.e. the way we describe the schemas of other messages).
2. String. These always start with "net.corda:" and are then followed by either a 'well known' type name, or
   a base64 encoded *fingerprint* of the underlying schema that was generated from the original class. They are
   encoded using the AMQP symbol type.

The fingerprint can be used to determine if the serialised message maps precisely to a holder type (class) you already
have in your environment. If you don't recognise the fingerprint, you may need to examine the schema data to figure out
a reasonable approximate mapping to a type you do have ... or you can give up and throw a parse error.

The numeric codes are defined as follows (remember to mask out the top 16 bits first):

1. ENVELOPE
2. SCHEMA
3. OBJECT_DESCRIPTOR
4. FIELD
5. COMPOSITE_TYPE
6. RESTRICTED_TYPE
7. CHOICE
8. REFERENCED_OBJECT
9. TRANSFORM_SCHEMA
10. TRANSFORM_ELEMENT
11. TRANSFORM_ELEMENT_KEY

In this document, the term "record" is used to mean an AMQP list described with a numeric code as enumerated
above. A record may represent an actual logical list of variable length, or be a fixed length list of fields. Our
encoding should really have used AMQP arrays for the case where the contents are of variable length and lists only for
representing object/class like things, unfortunately it uses lists for both. The term "object" is used to mean a list
described with a string/symbolic descriptor that references a schema entry.

High level format
-----------------

Every Corda message is at the top level an *ENVELOPE* record containing three elements:

1. The top level message and is described using a string (symbolic) descriptor.
2. A *SCHEMA* record.
3. A *TRANSFORM_SCHEMA* record.

The transform schema will usually be empty - it's used to describe how a data structure has evolved over time, so
making it easier to map to old/new code.

The *SCHEMA* record always contains a single element, which is itself another list containing *COMPOSITE_TYPE* records.
Each *COMPOSITE_TYPE* record describes a single app-level type and has the following members:

1. Name: string
2. Label: nullable string
3. Provides: list of strings
4. Descriptor: An *OBJECT_DESCRIPTOR* record
5. Fields: A list of *FIELD* records

The label will typically be unused and left as null - it's here to match the AMQP specification and could in future contain
arbitrary unstructured text, e.g. a javadoc explaining more about the semantics of the field. The "provides list" is
a set of strings naming Java interfaces that the original type implements. It can be used to work with messages generically
in a strongly typed, safe manner. Rather than guessing whether a type is meant to be a Foo or Bar based on matching
with the field names, the schema itself declares what contracts it is intended to meet.

The descriptor record has two elements, the first is a string/symbol and the second is an unsigned long code. Typically
only one will be set. This record corresponds to the descriptor that will appear in the main message stream.

Finally, the fields are defined. Each *FIELD* record has the following members:

1. Name: string
2. Type: string
3. Requires: list of string
4. Default: nullable string
5. Label: nullable string
6. Mandatory: boolean
7. Multiple: boolean

The meaning of these are defined in the AMQP specification. The type string is a Java class name *with* generic parameters.

The other parts of the schema map to the AMQP XML schema specification in the same straightforward manner.

Mapping JVM classes to composite types
--------------------------------------

Corda does not need or use a separate schema definition language. Instead, source code is used as a way to define schemas
via regular class definitions in any statically typed JVM-bytecode targeting language. This specification will thus
frequently refer to types whose only definitions are found in the Corda source code: these definitions are canonical and not
derived from any other kind of schema. Any class annotated as ``@CordaSerializable`` could appear in an AMQP message.
Whilst you don't need access to the original class files to decode the typed structure of a Corda message due to the embedded AMQP
schema, it will often be much more convenient to work with the original structures using JVM reflection. This is typically
very useful for code generators.

If you want to you can nonetheless parse the Java .class file format using a variety of libraries. The format is a simple tagged
union style format and `can be parsed in about 300 lines of C <https://github.com/atcol/cfr/blob/master/src/class.c>`_. The only
part of the class file that actually matters for type information are the parameters to the constructor, as that defines which fields
are stored to the wire.

Source code does not have a deterministic field ordering. Developers may re-arrange fields in their classes as they refactor
their code, which in a conventional serialisation scheme would break the wire format. Thus when mapping classes to AMQP schemas,
we alphabetically sort the fields. If a new field is added, it may thus appear in the middle of the composite type list rather than
at the end.

.. warning:: The above implies that you cannot handle format evolution by simply skipping fields you don't understand. Instead you
   must notice when the descriptors have changed from what you expect, and consult the schema to determine how to map the new message
   to a schema that you can work with.

Containers
----------

AMQP defines encodings for maps and lists, which are mapped to/from ``java.util.Map`` and ``java.util.List`` in JVM code. You don't need
any special support to read these if you don't care about the higher level type system.

In the binary schemas containers are represented as follows. A field in a composite type that is a list will look like this:

1. Name: "livingIn"
2. Type: "*"
3. Requires: [ "java.util.List<net.corda.tools.serialization.City>" ]
4. Default: NULL
5. Label: NULL
6. Mandatory: true
7. Multiple: false

The *requires* field is a list of *archetypes*. These are simply uninterpreted strings that refer to other schema elements, which
list the same string in their *provides* field. In this way a form of intersection typing is implemented. We use Java type names
with generics to link the field to the definition of a restricted type.

The list type will be defined as a restricted type, like so:

0. Name: "java.util.List<net.corda.tools.serialization.City>"
1. Label: NULL
2. Provides: []
3. Source: "list"
4. Descriptor: [
     0. Symbol: net.corda:2A8U5kaXW/lD5ns+l0xPFg==
     1. Numeric: NULL
   ]
5. Choices: []

Signed data
-----------

A common pattern in Corda is that an outer wrapper serialised message contains signatures and certificates for an inner
serialised message. The inner message is represented as 'binary', thus it requires two passes to deserialise such a
message fully. This is intended as a form of security firebreak, because it means you can avoid processing any serialised
data until the signatures have been checked and provenance established. It also helps ensure everyone calculates a
signature over the same binary data without roundtripping issues appearing.

The following types are used for this in the current version of the protocol (correct as of Corda 4):

* ``net.corda.core.internal.SignedDataWithCert``, descriptor ``net.corda:VywzVs/TR8ztvQBpYFpnlQ==``. Fields:
    * raw: ``net.corda.core.serialization.SerializedBytes<?>``
    * sig: ``net.corda.core.internal.DigitalSignatureWithCert``
* ``net.corda.core.internal.DigitalSignatureWithCert``, descriptor ``net.corda:AJin3eE1QDfCwTiDWC5hJA==``. Fields:
    * by: ``java.security.cert.X509Certificate``
    * bytes: binary

The signature bytes are opaque and their format depends on the cryptographic scheme identified in the X.509 certificate,
for example, elliptic curve signatures use a standardised (non-AMQP) binary format that encodes the coordinates of the
point on the curve. The type ``java.security.cert.X509Certificate`` does not appear in the schema, it is parsed as a
special case and has the descriptor ``net.corda:java.security.cert.X509Certificate``. A field with this descriptor is
of type 'binary' and contains a certificate in the standard X.509 binary format (again, not AMQP).

Examples
--------

The following sample shows how a few lines of Kotlin code defining some sophisticated data structures maps to an AMQP message.

.. sourcecode:: kotlin

   @CordaSerializable
   data class Employee(val names: Pair<String, String>)

   @CordaSerializable
   data class Department(val name: String, val employees: List<Employee>)

   @CordaSerializable
   data class Company(
           val name: String,
           val createdInYear: Short,
           val logo: OpaqueBytes,
           val departments: List<Department>,
           val historicalEvents: Map<String, Instant>
   )

and here is an ad-hoc textual representation of what it turns into on the wire (this format is not stable or meaningful)::

    envelope [
        0. net.corda:XIBlQ9Yl/RlKGLjCMY1/Kg== [
               0. 2014: short
                      0. net.corda:J6fOfvKOUIhpLqSmzN2ecw== [
               1. net.corda:mCdn5Q/6wPrRd120wfv5og== [
                             0. net.corda:KwaBqNRsTDOaXBrYdtDZpw== [
                                           0. net.corda:c0Lkwk4E63sshTPr2G60aQ== [
                                    0. net.corda:zjQ3JQXiArQUxXuCcaWANw== [
                                                  0. "Mike"
                                              ]
                                                  1. "Hearn"
                                       ]
                                           0. net.corda:c0Lkwk4E63sshTPr2G60aQ== [
                                    1. net.corda:zjQ3JQXiArQUxXuCcaWANw== [
                                                  0. "Richard"
                                              ]
                                                  1. "Brown"
                                       ]
                                           0. net.corda:c0Lkwk4E63sshTPr2G60aQ== [
                                    2. net.corda:zjQ3JQXiArQUxXuCcaWANw== [
                                                  0. "James"
                                              ]
                                                  1. "Carlyle"
                                       ]
                                ]
                             1. "Platform"
                         ]
                  ]
               2. net.corda:QXkG3ayKZNvF8dIEKbOTSw== {
                      "First lab project proposal email" -> net.corda:java.time.Instant [
                          0. 1411596660: long
                          1. 0: int
                      ]
                      "Hired Mike" -> net.corda:java.time.Instant [
                          0. 1446552000: long
                          1. 0: int
                      ]
                  }
               3. net.corda:pgT0Kc3t/bvnzmgu/nb4Cg== [
                      0. <binary of 1 bytes>
                  ]
               4. "R3"
           ]
        1. schema [
               0. [
                      0. composite type [
                             0. "net.corda.tools.serialization.Company"
                             1. NULL
                             2. []
                             3. object descriptor [
                                    0. net.corda:XIBlQ9Yl/RlKGLjCMY1/Kg==: symbol
                                    1. NULL
                                ]
                             4. [
                                    0. field [
                                           0. "createdInYear"
                                           1. "short"
                                           2. []
                                           3. "0"
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    1. field [
                                           0. "departments"
                                           1. "*"
                                           2. [
                                                  0. "java.util.List<net.corda.tools.serialization.Department>"
                                              ]
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    2. field [
                                           0. "historicalEvents"
                                           1. "*"
                                           2. [
                                                  0. "java.util.Map<string, java.time.Instant>"
                                              ]
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    3. field [
                                           0. "logo"
                                           1. "net.corda.core.utilities.OpaqueBytes"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    4. field [
                                           0. "name"
                                           1. "string"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                ]
                         ]
                      1. restricted type [
                             0. "java.util.List<net.corda.tools.serialization.Department>"
                             1. NULL
                             2. []
                             3. "list"
                             4. object descriptor [
                                    0. net.corda:mCdn5Q/6wPrRd120wfv5og==: symbol
                                    1. NULL
                                ]
                             5. []
                         ]
                      2. composite type [
                             0. "net.corda.tools.serialization.Department"
                             1. NULL
                             2. []
                             3. object descriptor [
                                    0. net.corda:J6fOfvKOUIhpLqSmzN2ecw==: symbol
                                    1. NULL
                                ]
                             4. [
                                    0. field [
                                           0. "employees"
                                           1. "*"
                                           2. [
                                                  0. "java.util.List<net.corda.tools.serialization.Employee>"
                                              ]
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    1. field [
                                           0. "name"
                                           1. "string"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                ]
                         ]
                      3. restricted type [
                             0. "java.util.List<net.corda.tools.serialization.Employee>"
                             1. NULL
                             2. []
                             3. "list"
                             4. object descriptor [
                                    0. net.corda:KwaBqNRsTDOaXBrYdtDZpw==: symbol
                                    1. NULL
                                ]
                             5. []
                         ]
                      4. composite type [
                             0. "net.corda.tools.serialization.Employee"
                             1. NULL
                             2. []
                             3. object descriptor [
                                    0. net.corda:zjQ3JQXiArQUxXuCcaWANw==: symbol
                                    1. NULL
                                ]
                             4. [
                                    0. field [
                                           0. "names"
                                           1. "kotlin.Pair<string, string>"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                ]
                         ]
                      5. composite type [
                             0. "kotlin.Pair<string, string>"
                             1. NULL
                             2. []
                             3. object descriptor [
                                    0. net.corda:c0Lkwk4E63sshTPr2G60aQ==: symbol
                                    1. NULL
                                ]
                             4. [
                                    0. field [
                                           0. "first"
                                           1. "string"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                    1. field [
                                           0. "second"
                                           1. "string"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                ]
                         ]
                      6. restricted type [
                             0. "java.util.Map<string, java.time.Instant>"
                             1. NULL
                             2. []
                             3. "map"
                             4. object descriptor [
                                    0. net.corda:QXkG3ayKZNvF8dIEKbOTSw==: symbol
                                    1. NULL
                                ]
                             5. []
                         ]
                      7. composite type [
                             0. "net.corda.core.utilities.OpaqueBytes"
                             1. NULL
                             2. []
                             3. object descriptor [
                                    0. net.corda:pgT0Kc3t/bvnzmgu/nb4Cg==: symbol
                                    1. NULL
                                ]
                             4. [
                                    0. field [
                                           0. "bytes"
                                           1. "binary"
                                           2. []
                                           3. NULL
                                           4. NULL
                                           5. true
                                           6. false
                                       ]
                                ]
                         ]
                  ]
           ]
        2. transform schema {
           }
    ]