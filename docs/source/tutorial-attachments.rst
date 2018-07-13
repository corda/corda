.. highlight:: kotlin

Using attachments
=================

Attachments are ZIP/JAR files referenced from transaction by hash, but not included in the transaction
itself. These files are automatically requested from the node sending the transaction when needed and cached
locally so they are not re-requested if encountered again. Attachments typically contain:

* Contract code
* Metadata about a transaction, such as PDF version of an invoice being settled
* Shared information to be permanently recorded on the ledger

To add attachments the file must first be uploaded to the node, which returns a unique ID that can be added
using ``TransactionBuilder.addAttachment()``. Attachments can be uploaded and downloaded via RPC and the Corda
:doc:`shell`.

It is encouraged that where possible attachments are reusable data, so that nodes can meaningfully cache them.

Uploading and downloading
-------------------------

To upload an attachment to the node, or download an attachment named by its hash, you use :doc:`clientrpc`. This
is also available for interactive use via the shell. To **upload** run:

``>>> run uploadAttachment jar: path/to/the/file.jar``

or

``>>> run uploadAttachmentWithMetadata jar: path/to/the/file.jar, uploader: myself, filename: original_name.jar``

to include the metadata with the attachment which can be used to find it later on. Note, that currently both uploader
and filename are just plain strings (there is no connection between uploader and the RPC users for example).

The file is uploaded, checked and if successful the hash of the file is returned. This is how the attachment is
identified inside the node.

To download an attachment, you can do:

``>>> run openAttachment id: AB7FED7663A3F195A59A0F01091932B15C22405CB727A1518418BF53C6E6663A``

which will then ask you to provide a path to save the file to. To do the same thing programmatically, you
can pass a simple ``InputStream`` or ``SecureHash`` to the ``uploadAttachment``/``openAttachment`` RPCs from
a JVM client.

Searching for attachments
-------------------------

Attachments metadata can be used to query, in the similar manner as :doc:`api-vault-query`.

``AttachmentQueryCriteria`` can be used to build a query, utilizing set of operations per column, namely:

* Binary logical (AND, OR)
* Comparison (LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL)
* Equality (EQUAL, NOT_EQUAL)
* Likeness (LIKE, NOT_LIKE)
* Nullability (IS_NULL, NOT_NULL)
* Collection based (IN, NOT_IN)

``And`` and ``or`` operators can be used to build queries of arbitrary complexity. Example of such query:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/persistence/NodeAttachmentStorageTest.kt
        :language: kotlin
        :start-after: DOCSTART AttachmentQueryExample1
        :end-before: DOCEND AttachmentQueryExample1
        :dedent: 12

Protocol
--------

Normally attachments on transactions are fetched automatically via the ``ReceiveTransactionFlow``. Attachments
are needed in order to validate a transaction (they include, for example, the contract code), so must be fetched
before the validation process can run.

.. note:: Future versions of Corda may support non-critical attachments that are not used for transaction verification
   and which are shared explicitly. These are useful for attaching and signing auditing data with a transaction
   that isn't used as part of the contract logic.

Attachments demo
----------------

There is a worked example of attachments, which relays a simple document from one node to another. The "two party
trade flow" also includes an attachment, however it is a significantly more complex demo, and less well suited
for a tutorial.

The demo code is in the file ``samples/attachment-demo/src/main/kotlin/net/corda/attachmentdemo/AttachmentDemo.kt``,
with the core logic contained within the two functions ``recipient()`` and ``sender()``. The first thing it does is set
up an RPC connection to node B using a demo user account (this is all configured in the gradle build script for the demo
and the nodes will be created using the ``deployNodes`` gradle task as normal). The ``CordaRPCClient.use`` method is a
convenience helper intended for small tools that sets up an RPC connection scoped to the provided block, and brings all
the RPCs into scope. Once connected the sender/recipient functions are run with the RPC proxy as a parameter.

We'll look at the recipient function first.

The first thing it does is wait to receive a notification of a new transaction by calling the ``verifiedTransactions``
RPC, which returns both a snapshot and an observable of changes. The observable is made blocking and the next
transaction the node verifies is retrieved. That transaction is checked to see if it has the expected attachment
and if so, printed out.

.. container:: codeset

    .. literalinclude:: ../../samples/attachment-demo/src/main/kotlin/net/corda/attachmentdemo/AttachmentDemo.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

The sender correspondingly builds a transaction with the attachment, then calls ``FinalityFlow`` to complete the
transaction and send it to the recipient node:

.. container:: codeset

    .. literalinclude:: ../../samples/attachment-demo/src/main/kotlin/net/corda/attachmentdemo/AttachmentDemo.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2

This side is a bit more complex. Firstly it looks up its counterparty by name in the network map. Then, if the node
doesn't already have the attachment in its storage, we upload it from a JAR resource and check the hash was what
we expected. Then a trivial transaction is built that has the attachment and a single signature and it's sent to
the other side using the FinalityFlow. The result of starting the flow is a stream of progress messages and a
``returnValue`` observable that can be used to watch out for the flow completing successfully.
