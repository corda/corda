.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the flow
================
A flow encodes a sequence of steps that a node can perform to achieve a specific ledger update. By installing new flows
on a node, we allow the node to handle new business processes. The flow we define will allow a node to issue an
``IOUState`` onto the ledger.

Flow outline
------------
The goal of our flow will be to orchestrate an IOU issuance transaction. Transactions in Corda are the atomic units of
change that update the ledger. Each transaction is a proposal to mark zero or more existing states as historic (the
inputs), while creating zero or more new states (the outputs).

The process of creating and applying this transaction to a ledger will be conducted by the IOU's lender, and will
require the following steps:

  1. Building the transaction proposal for the issuance of a new IOU onto a ledger
  2. Signing the transaction proposal
  3. Recording the transaction
  4. Sending the transaction to the IOU's borrower so that they can record it too

At this stage, we do not require the borrower to approve and sign IOU issuance transactions. We will be able to impose
this requirement when we look at contracts in the next tutorial.

Subflows
^^^^^^^^
Tasks like recording a transaction or sending a transaction to a counterparty are very common in Corda. Instead of
forcing each developer to reimplement their own logic to handle these tasks, Corda provides a number of library flows
to handle these tasks. We call these flows that are invoked in the context of a larger flow to handle a repeatable task
*subflows*.

In our case, we can automate steps 3 and 4 of the IOU issuance flow using ``FinalityFlow``.

FlowLogic
---------
All flows must subclass ``FlowLogic``. You then define the steps taken by the flow by overriding ``FlowLogic.call``.

Let's define our ``IOUFlow`` in either ``TemplateFlow.java`` or ``App.kt``. Delete the two existing flows in the
template (``Initiator`` and ``Responder``), and replace them with the following:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/tutorial/helloworld/flow.kt
        :language: kotlin
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/tutorial/helloworld/IOUFlow.java
        :language: java
        :start-after: DOCSTART 01
        :end-before: DOCEND 01

If you're following along in Java, you'll also need to rename ``TemplateFlow.java`` to ``IOUFlow.java``. Let's walk
through this code step-by-step.

We've defined our own ``FlowLogic`` subclass that overrides ``FlowLogic.call``. ``FlowLogic.call`` has a return type
that must match the type parameter passed to ``FlowLogic`` - this is type returned by running the flow.

``FlowLogic`` subclasses can optionally have constructor parameters, which can be used as arguments to
``FlowLogic.call``. In our case, we have two:

* ``iouValue``, which is the value of the IOU being issued
* ``otherParty``, the IOU's borrower (the node running the flow is the lender)

``FlowLogic.call`` is annotated ``@Suspendable`` - this allows the flow to be check-pointed and serialised to disk when
it encounters a long-running operation, allowing your node to move on to running other flows. Forgetting this
annotation out will lead to some very weird error messages!

There are also a few more annotations, on the ``FlowLogic`` subclass itself:

  * ``@InitiatingFlow`` means that this flow can be started directly by the node
  * ``@StartableByRPC`` allows the node owner to start this flow via an RPC call

Let's walk through the steps of ``FlowLogic.call`` itself. This is where we actually describe the procedure for
issuing the ``IOUState`` onto a ledger.

Choosing a notary
^^^^^^^^^^^^^^^^^
Every transaction requires a notary to prevent double-spends and serve as a timestamping authority. The first thing we
do in our flow is retrieve the a notary from the node's ``ServiceHub``. ``ServiceHub.networkMapCache`` provides
information about the other nodes on the network and the services that they offer.

.. note::

    Whenever we need information within a flow - whether it's about our own node's identity, the node's local storage,
    or the rest of the network - we generally obtain it via the node's ``ServiceHub``.

Building the transaction
^^^^^^^^^^^^^^^^^^^^^^^^
We'll build our transaction proposal in two steps:

* Creating the transaction's components
* Adding these components to a transaction builder

Transaction items
~~~~~~~~~~~~~~~~~
Our transaction will have the following structure:

  .. image:: resources/simple-tutorial-transaction.png
     :scale: 15%
     :align: center

* The output ``IOUState`` on the right represents the state we will be adding to the ledger. As you can see, there are
  no inputs - we are not consuming any existing ledger states in the creation of our IOU

* An ``Action`` command listing the IOU's lender as a signer

We've already talked about the ``IOUState``, but we haven't looked at commands yet. Commands serve two functions:

* They indicate the intent of a transaction - issuance, transfer, redemption, revocation. This will be crucial when we
  discuss contracts in the next tutorial
* They allow us to define the required signers for the transaction. For example, IOU creation might require signatures
  from the lender only, whereas the transfer of an IOU might require signatures from both the IOU’s borrower and lender

Each ``Command`` contains a command type plus a list of public keys. For now, we use the pre-defined
``TemplateContract.Action`` as our command type, and we list the lender as the only public key. This means that for
the transaction to be valid, the lender is required to sign the transaction.

Creating a transaction builder
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To actually build the proposed transaction, we need a ``TransactionBuilder``. This is a mutable transaction class to
which we can add inputs, outputs, commands, and any other items the transaction needs. We create a
``TransactionBuilder`` that uses the notary we retrieved earlier.

Once we have the ``TransactionBuilder``, we add our components:

* The command is added directly using ``TransactionBuilder.addCommand``
* The output ``IOUState`` is added using ``TransactionBuilder.addOutputState``. As well as the output state itself,
  this method takes a reference to the contract that will govern the evolution of the state over time. Here, we are
  passing in a reference to the ``TemplateContract``, which imposes no constraints. We will define a contract imposing
  real constraints in the next tutorial

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Now that we have a valid transaction proposal, we need to sign it. Once the transaction is signed, no-one will be able
to modify the transaction without invalidating this signature. This effectively makes the transaction immutable.

We sign the transaction using ``ServiceHub.signInitialTransaction``, which returns a ``SignedTransaction``. A
``SignedTransaction`` is an object that pairs a transaction with a list of signatures over that transaction.

Finalising the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^^
We now have a valid signed transaction. All that's left to do is to have it recorded by all the relevant parties. By
doing so, it will become a permanent part of the ledger. As discussed, we'll handle this process automatically using a
built-in flow called ``FinalityFlow``. ``FinalityFlow`` completely automates the process of:

* Notarising the transaction if required (i.e. if the transaction contains inputs and/or a time-window)
* Recording it in our vault
* Sending it to the other participants (i.e. the lender) for them to record as well

Progress so far
---------------
Our flow, and our CorDapp, are now ready! We have now defined a flow that we can start on our node to completely
automate the process of issuing an IOU onto the ledger. All that's left is to spin up some nodes and test our CorDapp.