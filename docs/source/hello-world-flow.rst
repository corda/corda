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
Our flow needs to take the following steps for a borrower to issue a new IOU onto the ledger:

  1. Create a valid transaction proposal for the creation of a new IOU
  2. Verify the transaction
  3. Sign the transaction ourselves
  4. Record the transaction in our vault
  5. Send the transaction to the IOU's lender so that they can record it too

Subflows
^^^^^^^^
Although our flow requirements look complex, we can delegate to existing flows to handle many of these tasks. A flow
that is invoked within the context of a larger flow to handle a repeatable task is called a *subflow*.

In our initiator flow, we can automate steps 4 and 5 using ``FinalityFlow``.

All we need to do is write the steps to handle the creation and signing of the proposed transaction.

FlowLogic
---------
Flows are implemented as ``FlowLogic`` subclasses. You define the steps taken by the flow by overriding
``FlowLogic.call``.

We'll write our flow in either ``TemplateFlow.java`` or ``App.kt``. Overwrite both the existing flows in the template
with the following:

.. container:: codeset

    .. code-block:: kotlin

        ...

        import net.corda.core.utilities.ProgressTracker
        import net.corda.core.transactions.TransactionBuilder
        import net.corda.core.flows.*

        ...

        @InitiatingFlow
        @StartableByRPC
        class IOUFlow(val iouValue: Int,
                      val otherParty: Party) : FlowLogic<Unit>() {

            /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
            override val progressTracker = ProgressTracker()

            /** The flow logic is encapsulated within the call() method. */
            @Suspendable
            override fun call() {
                // We retrieve the notary identity from the network map.
                val notary = serviceHub.networkMapCache.notaryIdentities[0]

                // We create a transaction builder
                val txBuilder = TransactionBuilder(notary = notary)

                // We create the transaction components.
                val outputState = IOUState(iouValue, ourIdentity, otherParty)
                val outputContract = IOUContract::class.jvmName
                val outputContractAndState = StateAndContract(outputState, outputContract)
                val cmd = Command(IOUContract.Create(), ourIdentity.owningKey)

                // We add the items to the builder.
                txBuilder.withItems(outputContractAndState, cmd)

                // Verifying the transaction.
                txBuilder.verify(serviceHub)

                // Signing the transaction.
                val signedTx = serviceHub.signInitialTransaction(txBuilder)

                // Finalising the transaction.
                subFlow(FinalityFlow(signedTx))
            }
        }

    .. code-block:: java

        package com.template.flow;

        import co.paralleluniverse.fibers.Suspendable;
        import com.template.contract.IOUContract;
        import com.template.state.IOUState;
        import net.corda.core.contracts.Command;
        import net.corda.core.contracts.StateAndContract;
        import net.corda.core.flows.*;
        import net.corda.core.identity.Party;
        import net.corda.core.transactions.SignedTransaction;
        import net.corda.core.transactions.TransactionBuilder;
        import net.corda.core.utilities.ProgressTracker;

        @InitiatingFlow
        @StartableByRPC
        public class IOUFlow extends FlowLogic<Void> {
            private final Integer iouValue;
            private final Party otherParty;

            /**
             * The progress tracker provides checkpoints indicating the progress of the flow to observers.
             */
            private final ProgressTracker progressTracker = new ProgressTracker();

            public IOUFlow(Integer iouValue, Party otherParty) {
                this.iouValue = iouValue;
                this.otherParty = otherParty;
            }

            @Override
            public ProgressTracker getProgressTracker() {
                return progressTracker;
            }

            /**
             * The flow logic is encapsulated within the call() method.
             */
            @Suspendable
            @Override
            public Void call() throws FlowException {
                // We retrieve the notary identity from the network map.
                final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

                // We create a transaction builder.
                final TransactionBuilder txBuilder = new TransactionBuilder();
                txBuilder.setNotary(notary);

                // We create the transaction components.
                IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
                String outputContract = IOUContract.class.getName();
                StateAndContract outputContractAndState = new StateAndContract(outputState, outputContract);
                Command cmd = new Command<>(new IOUContract.Create(), getOurIdentity().getOwningKey());

                // We add the items to the builder.
                txBuilder.withItems(outputContractAndState, cmd);

                // Verifying the transaction.
                txBuilder.verify(getServiceHub());

                // Signing the transaction.
                final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

                // Finalising the transaction.
                subFlow(new FinalityFlow(signedTx));

                return null;
            }
        }

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

Retrieving participant information
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The identity of our counterparty is passed in as a constructor argument. However, we need to use the ``ServiceHub`` to
retrieve our identity, as well as the identity of the notary we'll be using for our transaction.

You can see that the notary's identity is being retrieved from the node's ``ServiceHub``. Whenever we need
information within a flow - whether it's about our own node, its contents, or the rest of the network - we use the
node's ``ServiceHub``. In particular, ``ServiceHub.networkMapCache`` provides information about the other nodes on the
network and the services that they offer.

Building the transaction
^^^^^^^^^^^^^^^^^^^^^^^^
We'll build our transaction proposal in two steps:

* Creating a transaction builder
* Adding the desired items to the builder

Creating a transaction builder
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
To start building the proposed transaction, we need a ``TransactionBuilder``. This is a mutable transaction class to
which we can add inputs, outputs, commands, and any other items the transaction needs. We create a
``TransactionBuilder`` that uses the notary we retrieved earlier.

Transaction items
~~~~~~~~~~~~~~~~~
Now that we have our ``TransactionBuilder``, we need to add the desired items. Remember that we're trying to build
the following transaction:

  .. image:: resources/simple-tutorial-transaction.png
     :scale: 15%
     :align: center

So we'll need the following:

* The output ``IOUState`` and its associated contract
* A ``Create`` command listing the IOU's lender as a signer

The command we use pairs the ``IOUContract.Create`` command defined earlier with our public key. Including this command
in the transaction makes us one of the transaction's required signers.

We add these items to the transaction using the ``TransactionBuilder.withItems`` method, which takes a ``vararg`` of:

* ``StateAndContract`` or ``TransactionState`` objects, which are added to the builder as output states
* ``StateAndRef`` objects (references to the outputs of previous transactions), which are added to the builder as input
  state references
* ``Command`` objects, which are added to the builder as commands
* ``SecureHash`` objects, which are added to the builder as attachments
* ``TimeWindow`` objects, which set the time-window of the transaction

It will modify the ``TransactionBuilder`` in-place to add these components to it.

Verifying the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^
We've now built our proposed transaction. Before we sign it, we should check that it represents a valid ledger update
proposal by verifying the transaction, which will execute each of the transaction's contracts.

If the verification fails, we have built an invalid transaction. Our flow will then end, throwing a
``TransactionVerificationException``.

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