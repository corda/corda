.. highlight:: kotlin

Using attachments
=================

Attachments are (typically large) Zip/Jar files referenced within a transaction, but not included in the transaction
itself. These files can be requested from the originating node as needed, although in many cases will be cached within
nodes already. Examples include:

* Contract executable code
* Metadata about a transaction, such as PDF version of an invoice being settled
* Shared information to be permanently recorded on the ledger

To add attachments the file must first be added to uploaded to the node, which returns a unique ID that can be added
using ``TransactionBuilder.addAttachment()``. Attachments can be uploaded and downloaded via RPC and HTTP. For
instructions on HTTP upload/download please see ":doc:`node-administration`".

Normally attachments on transactions are fetched automatically via the ``ResolveTransactionsFlow`` when verifying
received transactions. Attachments are needed in order to validate a transaction (they include, for example, the
contract code), so must be fetched before the validation process can run. ``ResolveTransactionsFlow`` calls
``FetchTransactionsFlow`` to perform the actual retrieval.

It is encouraged that where possible attachments are reusable data, so that nodes can meaningfully cache them.

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

.. sourcecode:: kotlin

   fun recipient(rpc: CordaRPCOps) {
       println("Waiting to receive transaction ...")
       val stx = rpc.verifiedTransactions().second.toBlocking().first()
       val wtx = stx.tx
       if (wtx.attachments.isNotEmpty()) {
           assertEquals(PROSPECTUS_HASH, wtx.attachments.first())
           require(rpc.attachmentExists(PROSPECTUS_HASH))
           println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(wtx)}")
       } else {
           println("Error: no attachments found in ${wtx.id}")
       }
   }

The sender correspondingly builds a transaction with the attachment, then calls ``FinalityFlow`` to complete the
transaction and send it to the recipient node:

.. sourcecode:: kotlin

   fun sender(rpc: CordaRPCOps) {
       // Get the identity key of the other side (the recipient).
       val otherSide: Party = rpc.partyFromName("Bank B")!!

       // Make sure we have the file in storage
       // TODO: We should have our own demo file, not share the trader demo file
       if (!rpc.attachmentExists(PROSPECTUS_HASH)) {
           Thread.currentThread().contextClassLoader.getResourceAsStream("bank-of-london-cp.jar").use {
               val id = rpc.uploadAttachment(it)
               assertEquals(PROSPECTUS_HASH, id)
           }
       }

       // Create a trivial transaction that just passes across the attachment - in normal cases there would be
       // inputs, outputs and commands that refer to this attachment.
       val ptx = TransactionType.General.Builder(notary = null)
       require(rpc.attachmentExists(PROSPECTUS_HASH))
       ptx.addAttachment(PROSPECTUS_HASH)
       // TODO: Add a dummy state and specify a notary, so that the tx hash is randomised each time and the demo can be repeated.

       // Despite not having any states, we have to have at least one signature on the transaction
       ptx.signWith(ALICE_KEY)

       // Send the transaction to the other recipient
       val stx = ptx.toSignedTransaction()
       println("Sending ${stx.id}")
       val protocolHandle = rpc.startFlow(::FinalityFlow, stx, setOf(otherSide))
       protocolHandle.progress.subscribe(::println)
       protocolHandle.returnValue.toBlocking().first()
   }


This side is a bit more complex. Firstly it looks up its counterparty by name in the network map. Then, if the node
doesn't already have the attachment in its storage, we upload it from a JAR resource and check the hash was what
we expected. Then a trivial transaction is built that has the attachment and a single signature and it's sent to
the other side using the FinalityFlow. The result of starting the flow is a stream of progress messages and a
``returnValue`` observable that can be used to watch out for the flow completing successfully.
