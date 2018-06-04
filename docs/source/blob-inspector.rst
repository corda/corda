Blob Inspector
==============

There are many benefits to having a custom binary serialisation format (see :doc:`serialization` for details) but one
disadvantage is the inability to view the contents in a human-friendly manner. The blob inspector tool alleviates this issue
by allowing the contents of a binary blob file (or URL end-point) to be output in either YAML or JSON. It uses
``JacksonSupport`` to do this (see :doc:`json`).

The latest version of the tool can be downloaded from `here <https://www.corda.net/downloads/>`_.

To run simply pass in the file or URL as the first parameter:

``java -jar blob-inspector.jar <file or URL>``

Use the ``--help`` flag for a full list of command line options.

When inspecting your custom data structures, there's no need to include the jars containing the class definitions for them
in the classpath. The blob inspector (or rather the serialization framework) is able to synthesis any classes found in the
blob that aren't on the classpath.

SerializedBytes
~~~~~~~~~~~~~~~

One thing to note is that the binary blob may contain embedded ``SerializedBytes`` objects. Rather than printing these
out as a Base64 string, the blob inspector will first materialise them into Java objects and then output those. You will
see this when dealing with classes such as ``SignedData`` or other structures that attach a signature, such as the
``nodeInfo-*`` files or the ``network-parameters`` file in the node's directory. For example, the output of a node-info
file may look like:


**-\\-format=YAML**
::

    net.corda.nodeapi.internal.SignedNodeInfo
    ---
    raw:
      class: "net.corda.core.node.NodeInfo"
      deserialized:
        addresses:
        - "localhost:10005"
        legalIdentitiesAndCerts:
        - "O=BankOfCorda, L=London, C=GB"
        platformVersion: 4
        serial: 1527851068715
    signatures:
    - !!binary |-
      VFRy4frbgRDbCpK1Vo88PyUoj01vbRnMR3ROR2abTFk7yJ14901aeScX/CiEP+CDGiMRsdw01cXt\nhKSobAY7Dw==

**-\\-format=JSON**
::

    net.corda.nodeapi.internal.SignedNodeInfo
    {
      "raw" : {
        "class" : "net.corda.core.node.NodeInfo",
        "deserialized" : {
          "addresses" : [ "localhost:10005" ],
          "legalIdentitiesAndCerts" : [ "O=BankOfCorda, L=London, C=GB" ],
          "platformVersion" : 4,
          "serial" : 1527851068715
        }
      },
      "signatures" : [ "VFRy4frbgRDbCpK1Vo88PyUoj01vbRnMR3ROR2abTFk7yJ14901aeScX/CiEP+CDGiMRsdw01cXthKSobAY7Dw==" ]
    }

Notice the file is actually a serialised ``SignedNodeInfo`` object, which has a ``raw`` property of type ``SerializedBytes<NodeInfo>``.
This property is materialised into a ``NodeInfo`` and is output under the ``deserialized`` field.
