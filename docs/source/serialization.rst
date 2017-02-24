Object Serialization
====================

What is serialization (and deserialization)?
--------------------------------------------

Object serialization is the process of converting objects into a stream of bytes and, deserialization, the reverse
process of creating objects from a stream of bytes.  It takes place very time nodes pass objects to each other as
messages, when objects are sent to or from RPC clients from the node, and when we store transactions in the database.

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
   interface extended by an interface implemented by the class or superclasses.  And so on and so on.
#. By returning the class as part of a plugin via the method ``customizeSerialization``.  It's important to return
   true from this method if you override it, otherwise the plugin will be excluded. See :doc:`corda-plugins`.
#. Via the ``DefaultWhitelist``, which is implemented via the plugin framework mentioned above.

The annotation is the preferred method for whitelisting.  An example is shown in :doc:`tutorial-clientrpc-api`.

.. note:: Several of the core interfaces at the heart of Corda are already annotated and so any classes that implement
them will automatically be whitelisted.  This includes `Contract`, `ContractState` and `CommandData`.
