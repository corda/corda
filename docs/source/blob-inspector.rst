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

``SerializedBytes`
~~~~~~~~~~~~~~~~~~

One thing to note is that the binary blob may contain embedded ``SerializedBytes`` objects. Rather than printing these
out as a Base64 string, the blob inspector will first materialise them into Java objects and then output those. You will
see this when dealing with classes such as ``SignedData`` or other structures that attach a signature, such as the
``nodeInfo-*`` files or the ``network-parameters`` file in the node's directory. For example, the output of a node-info
file may look like:

.. container:: codeset

    .. sourcecode:: yaml

        net.corda.nodeapi.internal.SignedNodeInfo
        ---
        raw:
          class: "net.corda.core.node.NodeInfo"
          deserialized:
            addresses:
            - "localhost:10011"
            legalIdentitiesAndCerts:
            - "O=BankOfCorda, L=New York, C=US"
            platformVersion: 4
            serial: 1527074180971
        signatures:
        - !!binary |
          dmoAnnzcv0MzRN+3ZSCDcCJIAbXnoYy5mFWB3Nijndzu/dzIoYdIawINXbNSY/5z2XloDK01vZRV
          TreFZCbZAg==

    .. sourcecode:: json

        net.corda.nodeapi.internal.SignedNodeInfo
        {
          "raw" : {
            "class" : "net.corda.core.node.NodeInfo",
            "deserialized" : {
              "addresses" : [ "localhost:10011" ],
              "legalIdentitiesAndCerts" : [ "O=BankOfCorda, L=New York, C=US" ],
              "platformVersion" : 4,
              "serial" : 1527074180971
            }
          },
          "signatures" : [ "dmoAnnzcv0MzRN+3ZSCDcCJIAbXnoYy5mFWB3Nijndzu/dzIoYdIawINXbNSY/5z2XloDK01vZRVTreFZCbZAg==" ]
        }

Notice the file is actually a serialised ``SignedNodeInfo`` object, which has a ``raw`` property of type ``SerializedBytes<NodeInfo>``.
This property is materialised into a ``NodeInfo`` and is output under the ``deserialized`` field.
