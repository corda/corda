.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the flow
================
A flow describes the sequence of steps for agreeing a specific ledger update. By installing new flows on our node, we
allow the node to handle new business processes. Our flow will allow a node to issue an ``IOUState`` onto the ledger.

Flow outline
------------
Our flow needs to take the following steps for a lender to issue a new IOU onto the ledger:

  1. Create a transaction proposal for the creation of a new IOU on ledger
  2. Sign the transaction proposal
  3. Record the transaction
  4. Send the transaction to the IOU's borrower so that they can record it too

Subflows
^^^^^^^^
Tasks like recording a transaction or sending a transaction to a counterparty are common tasks in Corda. Instead of
forcing each developer to reimplement this logic, we can invoke existing flows to handle these tasks. A flow that is
invoked within the context of a larger flow to handle a repeatable task is called a *subflow*.

In our case, we can automate steps 3 and 4 of the initiator's flow using ``FinalityFlow``.

All we need to do is write the steps to handle the creation and signing of the proposed transaction.

FlowLogic
---------
Flows must subclass ``FlowLogic``. You define the steps taken by the flow by overriding ``FlowLogic.call``.

We'll write our flow in either ``TemplateFlow.java`` or ``App.kt``. Delete both the existing flows in the template, and
replace them with the following:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/helloworld/flow.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/helloworld/IOUFlow.java
        :language: java
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

If you're following along in Java, you'll also need to rename ``TemplateFlow.java`` to ``IOUFlow.java``.

We now have our own ``FlowLogic`` subclass that overrides ``FlowLogic.call``. There's a few things to note:

* ``FlowLogic.call`` has a return type that matches the type parameter passed to ``FlowLogic`` - this is type returned
  by running the flow
* ``FlowLogic`` subclasses can have constructor parameters, which can be used as arguments to ``FlowLogic.call``
* ``FlowLogic.call`` is annotated ``@Suspendable`` - this means that the flow will be check-pointed and serialised to
  disk when it encounters a long-running operation, allowing your node to move on to running other flows. Forgetting
  this annotation out will lead to some very weird error messages
* There are also a few more annotations, on the ``FlowLogic`` subclass itself:

  * ``@InitiatingFlow`` means that this flow can be started directly by the node
  * ``@StartableByRPC`` allows the node owner to start this flow via an RPC call

* We override the progress tracker, even though we are not providing any progress tracker steps yet. The progress
  tracker is required for the node shell to establish when the flow has ended

Let's walk through the steps of ``FlowLogic.call`` one-by-one:

Choosing a notary
^^^^^^^^^^^^^^^^^
Every transaction requires a notary. The first thing we do in our flow is retrieve the identity of a notary from the
node's ``ServiceHub``. Whenever we need information within a flow - whether it's about our own node, its contents, or
the rest of the network - we use the node's ``ServiceHub``. In particular, ``ServiceHub.networkMapCache`` provides
information about the other nodes on the network and the services that they offer.

Building the transaction
^^^^^^^^^^^^^^^^^^^^^^^^
We'll build our transaction proposal in two steps:

* Creating the state and command for the transaction
* Adding these components to a transaction builder

Transaction items
~~~~~~~~~~~~~~~~~
Our transaction will have the following structure:

  .. image:: resources/simple-tutorial-transaction.png
     :scale: 15%
     :align: center

So we'll need the following:

* The output ``IOUState`` and its associated contract
* An ``Action`` command listing the IOU's lender as a signer

We've already defined the ``IOUState``, but we haven't talked about commands yet. Commands serve two functions:

* They indicate the transaction’s intent. This will be crucial when we discuss the concept of a ``Contract`` in a
  future tutorial
* They allow us to define the required signers for the transaction. For example, IOU creation might require signatures
  from the lender only, whereas the transfer of an IOU might require signatures from both the IOU’s borrower and lender

The command we use pairs the ``TemplateContract.Action`` command defined earlier with the public key of the lender.
Including this command in the transaction makes us one of the transaction's required signers.

Creating a transaction builder
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To start building the proposed transaction, we need a ``TransactionBuilder``. This is a mutable transaction class to
which we can add inputs, outputs, commands, and any other items the transaction needs. We create a
``TransactionBuilder`` that uses the notary we retrieved earlier.

Once we have the ``TransactionBuilder``, we add our components:

* The command is added directly using ``TransactionBuilder.addCommand``
* The output ``IOUState`` is added using ``TransactionBuilder.addOutputState``. As well as the output state itself,
  this method takes a reference to a contract. Here, we are passing in a reference to the ``TemplateContract``. We will
  discuss contracts more fully in a future tutorial

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Now that we have a valid transaction proposal, we need to sign it. Once the transaction is signed, no-one will be able
to modify the transaction without invalidating our signature, effectively making the transaction immutable.

The call to ``ServiceHub.toSignedTransaction`` returns a ``SignedTransaction`` - an object that pairs the
transaction itself with a list of signatures over that transaction.

Finalising the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^^
Now that we have a valid signed transaction, all that's left to do is to have it notarised and recorded by all the
relevant parties. By doing so, it will become a permanent part of the ledger. As discussed, we'll handle this process
automatically using a built-in flow called ``FinalityFlow``:

``FinalityFlow`` completely automates the process of:

* Notarising the transaction if required (i.e. if the transaction contains inputs and/or a time-window)
* Recording it in our vault
* Sending it to the other participants (i.e. the lender) for them to record as well

Our flow, and our CorDapp, are now ready!

Progress so far
---------------
We have now defined a flow that we can start on our node to completely automate the process of issuing an IOU onto the
ledger. The final step is to spin up some nodes and test our CorDapp.