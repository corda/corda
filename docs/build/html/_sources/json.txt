.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

JSON
====

Corda provides a module that extends the popular Jackson serialisation engine. Jackson is often used to serialise
to and from JSON, but also supports other formats such as YaML and XML. Jackson is itself very modular and has
a variety of plugins that extend its functionality. You can learn more at the `Jackson home page <https://github.com/FasterXML/jackson>`_.

To gain support for JSON serialisation of common Corda data types, include a dependency on ``net.corda:jackson:XXX``
in your Gradle or Maven build file, where XXX is of course the Corda version you are targeting (0.9 for M9, for instance).
Then you can obtain a Jackson ``ObjectMapper`` instance configured for use using the ``JacksonSupport.createNonRpcMapper()``
method. There are variants of this method for obtaining Jackson's configured in other ways: if you have an RPC
connection to the node (see ":doc:`clientrpc`") then your JSON mapper can resolve identities found in objects.

The API is described in detail here:

* `Kotlin API docs <api/kotlin/corda/net.corda.jackson/-jackson-support/index.html>`_
* `JavaDoc <api/javadoc/net/corda/jackson/package-summary.html>`_

.. container:: codeset

   .. sourcecode:: kotlin

      import net.corda.jackson.JacksonSupport

      val mapper = JacksonSupport.createNonRpcMapper()
      val json = mapper.writeValueAsString(myCordaState)  // myCordaState can be any object.

   .. sourcecode:: java

      import net.corda.jackson.JacksonSupport

      ObjectMapper mapper = JacksonSupport.createNonRpcMapper()
      String json = mapper.writeValueAsString(myCordaState)  // myCordaState can be any object.


.. note:: The way mappers interact with identity and RPC is likely to change in a future release.
