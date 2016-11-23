.. highlight:: kotlin

Using attachments
=================

Attachments are (typically large) Zip/Jar files referenced within a transaction, but not included in the transaction
itself. These files can be requested from the originating node as needed, although in many cases will be cached within
nodes already. Examples include:

* Contract executable code
* Metadata about a transaction, such as PDF version of an invoice being settled
* Shared information to be permanently recorded on the ledger

To add attachments the file must first be added to the node's storage service using ``StorageService.importAttachment()``,
which returns a unique ID that can be added using ``TransactionBuilder.addAttachment()``. Attachments can also be
uploaded and downloaded via HTTP, to enable integration with external systems. For instructions on HTTP upload/download
please see ":doc:`node-administration`".

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

The demo code is in the file "src/main/kotlin/net.corda.demos/attachment/AttachmentDemo.kt", with the core logic
contained within the two functions ``runRecipient()`` and ``runSender()``. We'll look at the recipient function first;
this subscribes to notifications of new validated transactions, and if it receives a transaction containing attachments,
loads the first attachment from storage, and checks it matches the expected attachment ID. ``ResolveTransactionsFlow``
has already fetched all attachments from the remote node, and as such the attachments are available from the node's
storage service. Once the attachment is verified, the node shuts itself down.

.. sourcecode:: kotlin

    private fun runRecipient(node: Node) {
        val serviceHub = node.services

        // Normally we would receive the transaction from a more specific flow, but in this case we let [FinalityFlow]
        // handle receiving it for us.
        serviceHub.storageService.validatedTransactions.updates.subscribe { event ->
            // When the transaction is received, it's passed through [ResolveTransactionsFlow], which first fetches any
            // attachments for us, then verifies the transaction. As such, by the time it hits the validated transaction store,
            // we have a copy of the attachment.
            val tx = event.tx
            if (tx.attachments.isNotEmpty()) {
                val attachment = serviceHub.storageService.attachments.openAttachment(tx.attachments.first())
                assertEquals(PROSPECTUS_HASH, attachment?.id)

                println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(event.tx)}")
                thread {
                    node.stop()
                }
            }
        }
    }

The sender correspondingly builds a transaction with the attachment, then calls ``FinalityFlow`` to complete the
transaction and send it to the recipient node:


.. sourcecode:: kotlin

    private fun runSender(node: Node, otherSide: Party) {
        val serviceHub = node.services
        // Make sure we have the file in storage
        if (serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH) == null) {
            net.corda.demos.Role::class.java.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = node.storage.attachments.importAttachment(it)
                assertEquals(PROSPECTUS_HASH, id)
            }
        }

        // Create a trivial transaction that just passes across the attachment - in normal cases there would be
        // inputs, outputs and commands that refer to this attachment.
        val ptx = TransactionType.General.Builder(notary = null)
        ptx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

        // Despite not having any states, we have to have at least one signature on the transaction
        ptx.signWith(ALICE_KEY)

        // Send the transaction to the other recipient
        val tx = ptx.toSignedTransaction()
        serviceHub.startFlow(LOG_SENDER, FinalityFlow(tx, emptySet(), setOf(otherSide))).success {
            thread {
                Thread.sleep(1000L) // Give the other side time to request the attachment
                node.stop()
            }
        }.failure {
            println("Failed to relay message ")
        }
    }
