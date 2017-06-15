.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the flow
================
A flow describes the sequence of steps for agreeing a specific ledger update. By installing new flows on our node, we
allow the node to handle new business processes.

We'll have to define two flows to issue an ``IOUState`` onto the ledger:
* One to be run by the node initiating the creation of the IOU
* One to be run by the node responding to an IOU creation request

FlowLogic
---------
Each flow is implemented as a ``FlowLogic`` subclass. You define the steps taken by the flow by overriding
``FlowLogic.call``.

We will define two ``FlowLogic`` instances communicating as a pair. The first will be called ``Initiator``, and will
be run by the sender of the IOU. The other will be called ``Acceptor``, and will be run by the recipient:

.. container:: codeset

    .. code-block:: kotlin

        package com.iou

        import co.paralleluniverse.fibers.Suspendable
        import net.corda.core.flows.FlowLogic
        import net.corda.core.flows.InitiatedBy
        import net.corda.core.flows.InitiatingFlow
        import net.corda.core.flows.StartableByRPC
        import net.corda.core.identity.Party
        import net.corda.core.transactions.SignedTransaction
        import net.corda.core.utilities.ProgressTracker

        object IOUFlow {
            @InitiatingFlow
            @StartableByRPC
            class Initiator(val iouValue: Int,
                            val otherParty: Party): FlowLogic<SignedTransaction>() {

                /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
                override val progressTracker = ProgressTracker()

                /** The flow logic is encapsulated within the call() method. */
                @Suspendable
                override fun call(): SignedTransaction { }
            }

            @InitiatedBy(Initiator::class)
            class Acceptor(val otherParty: Party) : FlowLogic<Unit>() {

                @Suspendable
                override fun call() { }
            }
        }

    .. code-block:: java

        package com.iou;

        import co.paralleluniverse.fibers.Suspendable;
        import net.corda.core.flows.*;
        import net.corda.core.identity.Party;
        import net.corda.core.transactions.SignedTransaction;
        import net.corda.core.utilities.ProgressTracker;

        public class IOUFlow {
            @InitiatingFlow
            @StartableByRPC
            public static class Initiator extends FlowLogic<SignedTransaction> {
                private final Integer iouValue;
                private final Party otherParty;

                /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
                private final ProgressTracker progressTracker = new ProgressTracker();

                public Initiator(Integer iouValue, Party otherParty) {
                    this.iouValue = iouValue;
                    this.otherParty = otherParty;
                }

                /** The flow logic is encapsulated within the call() method. */
                @Suspendable
                @Override
                public SignedTransaction call() throws FlowException { }
            }

            @InitiatedBy(Initiator.class)
            public static class Acceptor extends FlowLogic<Void> {

                private final Party otherParty;

                public Acceptor(Party otherParty) {
                    this.otherParty = otherParty;
                }

                @Suspendable
                @Override
                public Void call() throws FlowException { }
            }
        }

We can see that we have two ``FlowLogic`` subclasses, each overriding ``FlowLogic.call``. There's a few things to note:

* ``FlowLogic.call`` has a return type that matches the generic passed to ``FlowLogic`` - this is the return type of
  running the flow
* The ``FlowLogic`` subclasses can have constructor parameters, which can be used as arguments to ``FlowLogic.call``
* ``FlowLogic.call`` is annotated ``@Suspendable`` - this means that the flow will be check-pointed and serialised to
  disk when it encounters a long-running operation, allowing your node to move on to running other flows. Forgetting
  this annotation out will lead to some very weird error messages
* There are also a few more annotations, on the ``FlowLogic`` subclasses themselves:
  * ``@InitiatingFlow`` means that this flow can be started directly by the node
  * ``StartableByRPC`` allows the node owner to start this flow via an RPC call
  * ``@InitiatedBy(myClass: Class)`` means that this flow will only start in response to a message sent by another
    node running the ``myClass`` flow

Flow outline
------------
Now that we've defined our ``FlowLogic`` subclasses, what are the steps we need to take to issue a new IOU onto
the ledger?

On the initiator side, we need to:
  1. Create a valid transaction proposal for the creation of a new IOU
  2. Verifying the transaction
  3. Sign the transaction ourselves
  4. Gather the acceptor's signature
  5. Get the transaction notarised, to protect against double-spends
  6. Record the notarised transaction in our vault
  7. Send the notarised transaction to the acceptor so that they can record it too

On the acceptor side, we need to:
  1. Receive the partially-signed transaction from the initiator
  2. Verify its contents and signatures
  3. Append our signature and send it back to the initiator
  4. Wait to receive back the notarised transaction from the initiator
  5. Record the notarised transaction in our vault

Subflows
^^^^^^^^
Although our flows look complex, we can delegate to existing flows to handle many of these tasks. A flow that is
invoked within the context of a larger flow to handle a repeatable task is called a *subflow*.

In our initiator flow, we can automate step 4 by invoking ``SignTransactionFlow``, and we can automate steps 5, 6 and
7  using ``FinalityFlow``. Meanwhile, the *entirety* of the acceptor's flow can be automated using
``CollectSignaturesFlow``.

All we need to do is write the steps to handle the initiator creating and signing the proposed transaction.

Writing the initiator's flow
----------------------------
Let's work through the steps of the initiator's flow one-by-one.

Building the transaction
^^^^^^^^^^^^^^^^^^^^^^^^
We'll approach building the transaction in three steps:

* Creating a transaction builder
* Creating the transaction's components
* Adding the components to the builder

TransactionBuilder
~~~~~~~~~~~~~~~~~~
To start building the proposed transaction, we need a ``TransactionBuilder``. This is a mutable transaction class to
which we can add inputs, outputs, commands, and any other components the transaction needs.

We create a ``TransactionBuilder`` in ``Initiator.call`` as follows:

.. container:: codeset

    .. code-block:: kotlin

        // Additional import.
        import net.corda.core.transactions.TransactionBuilder

        ...

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity
        }

    .. code-block:: java

        // Additional import.
        import net.corda.core.transactions.TransactionBuilder;

        ...

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);
        }

In the first line, we create a ``TransactionBuilder``. We will also want our transaction to have a notary, in order
to prevent double-spends. In the second line, we retrieve the identity of the notary who will be notarising our
transaction and add it to the builder.

You can see that the notary's identity is being retrieved from the node's ``ServiceHub``. Whenever we need
information within a flow - whether it's about our own node, its contents, or the rest of the network - we use the
node's ``ServiceHub``. ``ServiceHub.networkMapCache`` in particular provides information about the other nodes on the
network and the services that they offer.

Transaction components
~~~~~~~~~~~~~~~~~~~~~~
Now that we have our ``TransactionBuilder``, we need to create its components:

* The output ``IOUState``
* A ``Create`` command listing both the IOU's sender and recipient as signers

We create these components as follows:

.. container:: codeset

    .. code-block:: kotlin

        // Additional import.
        import net.corda.core.contracts.Command

        ...

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })
        }

    .. code-block:: java

        // Additional imports.
        import com.google.common.collect.ImmutableList;
        import net.corda.core.contracts.Command;
        import java.security.PublicKey;
        import java.util.List;

        ...

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);
        }

To build the state, we start by retrieving our own identity (again, we get this information from the ``ServiceHub``,
via ``ServiceHub.myInfo``). We then build the ``IOUState``, using our identity, the ``IOUContract``, and the IOU
value and counterparty from the ``FlowLogic``'s constructor parameters.

We also create the command, which pairs the ``IOUContract.Create`` command with the public keys of ourselves and the
counterparty. If this command is included in the transaction, both ourselves and the counterparty will be required
signers.

Adding the components
~~~~~~~~~~~~~~~~~~~~~
Finally, we add the items to the transaction using the ``TransactionBuilder.withItems`` method:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);
        }

``TransactionBuilder.withItems`` takes a `vararg` of:
* `ContractState` objects, which are added to the builder as output states
* `StateRef` objects (references to the outputs of previous transactions), which are added to the builder as input
  state references
* `Command` objects, which are added to the builder as commands

It will modify the ``TransactionBuilder`` in-place to add these components to it.

Verifying the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^
We've now built our proposed transaction. Before we sign it, we should check that it represents a valid ledger update
proposal by verifying the transaction, which will execute each of the transaction's contracts:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();
        }

To verify the transaction, we must:
* Convert the builder into an immutable ``WireTransaction``
* Convert the ``WireTransaction`` into a ``LedgerTransaction`` using the ``ServiceHub``. This step resolves the
  transaction's input state references and attachment references into actual states and attachments (in case their
  contents are needed to verify the transaction
* Call ``LedgerTransaction.verify`` to test whether the transaction is valid based on the contract of every input and
  output state in the transaction

If the verification fails, we have built an invalid transaction. Our flow will then end, throwing a
``TransactionVerificationException``.

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Now that we are satisfied that our transaction proposal is valid, we sign it. Once the transaction is signed,
no-one will be able to modify the transaction without invalidating our signature. This effectively makes the
transaction immutable.

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);
        }

The call to ``ServiceHub.signInitialTransaction`` returns a ``SignedTransaction`` - an object that pairs the
transaction itself with a list of signatures over that transaction.

We can now safely send the builder to our counterparty. If the counterparty tries to modify the transaction, the
transaction's hash will change, our digital signature will no longer be valid, and the transaction will not be accepted
as a valid ledger update.

Gathering counterparty signatures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The final step in order to create a valid transaction proposal is to collect the counterparty's signature. As
discussed, we can automate this process by invoking the built-in ``CollectSignaturesFlow``:

.. container:: codeset

    .. code-block:: kotlin

        // Additional import.
        import net.corda.flows.CollectSignaturesFlow

        ...

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the signatures.
            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.tracker()))
        }

    .. code-block:: java

        // Additional import.
        import net.corda.flows.CollectSignaturesFlow;

        ...

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Gathering the signatures.
            final SignedTransaction signedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.Companion.tracker()));
        }

``CollectSignaturesFlow`` gathers signatures from every participant listed on the transaction, and returns a
``SignedTransaction`` with all the required signatures.

Finalising the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^^
We now have a valid transaction signed by all the required parties. All that's left to do is to have it notarised and
recorded by all the relevant parties. From then on, it will become a permanent part of the ledger. Again, instead
of handling this process manually, we'll use a built-in flow called ``FinalityFlow``:

.. container:: codeset

    .. code-block:: kotlin

        // Additional import.
        import net.corda.flows.FinalityFlow

        ...

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the signatures.
            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.tracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(signedTx)).single()
        }

    .. code-block:: java

        // Additional import.
        import net.corda.flows.FinalityFlow;

        ...

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Gathering the signatures.
            final SignedTransaction signedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.Companion.tracker()));

            // Finalising the transaction.
            return subFlow(new FinalityFlow(signedTx)).get(0);
        }

``FinalityFlow`` completely automates the process of:
* Notarising the transaction
* Recording it in our vault
* Sending it to the counterparty for them to record as well

That completes the initiator side of the flow.

Writing the acceptor's flow
---------------------------
The acceptor's side of the flow is much simpler. We need to:
1. Receive a signed transaction from the counterparty
2. Verify the transaction
3. Sign the transaction
4. Send the updated transaction back to the counterparty

As we just saw, the process of building and finalising the transaction will be completely handled by the initiator flow.

SignTransactionFlow
~~~~~~~~~~~~~~~~~~~
We can automate all four steps of the acceptor's flow by invoking ``SignTransactionFlow``. ``SignTransactionFlow`` is
a flow that is registered by default on every node to respond to messages from ``CollectSignaturesFlow`` (which is
invoked by the initiator flow).

``SignTransactionFlow`` is an abstract class. We have to subclass it and override
``SignTransactionFlow.checkTransaction``:

.. container:: codeset

    .. code-block:: kotlin

        // Additional import.
        import net.corda.flows.SignTransactionFlow

        ...

        @InitiatedBy(Initiator::class)
        class Acceptor(val otherParty: Party) : FlowLogic<Unit>() {

            @Suspendable
            override fun call() {
                // Stage 1 - Verifying and signing the transaction.
                subFlow(object : SignTransactionFlow(otherParty, tracker()) {
                    override fun checkTransaction(stx: SignedTransaction) {
                        // Define custom verification logic here.
                    }
                })
            }
        }

    .. code-block:: java

        // Additional import.
        import net.corda.flows.SignTransactionFlow;

        ...

        @InitiatedBy(Initiator.class)
        public static class Acceptor extends FlowLogic<Void> {

            private final Party otherParty;

            public Acceptor(Party otherParty) {
                this.otherParty = otherParty;
            }

            @Suspendable
            @Override
            public Void call() throws FlowException {
                // Stage 1 - Verifying and signing the transaction.

                class signTxFlow extends SignTransactionFlow {
                    private signTxFlow(Party otherParty, ProgressTracker progressTracker) {
                        super(otherParty, progressTracker);
                    }

                    @Override
                    protected void checkTransaction(SignedTransaction signedTransaction) {
                        // Define custom verification logic here.
                    }
                }

                subFlow(new signTxFlow(otherParty, SignTransactionFlow.Companion.tracker()));

                return null;
            }
        }

``SignTransactionFlow`` already checks the transaction's signatures, and whether the transaction is contractually
valid. The purpose of ``SignTransactionFlow.checkTransaction`` is to define any additional verification of the
transaction that we wish to perform before we sign it. For example, we may not wish to sign an IOU if its value is
too high.


// TODO: Talk about flow tests


Progress so far
---------------
We now have a flow that we can kick off on our node to completely automate the process of issuing an IOU onto the
ledger. Under the hood, this flow takes the form of two communicating ``FlowLogic`` subclasses.

We now have a complete CorDapp, made up of:
* The ``IOUState``, representing IOUs on the ledger
* The ``IOUContract``, controlling the evolution of ``IOUState`` objects over time
* The ``IOUFlow``, which transforms the creation of a new IOU on the ledger into a push-button process

The final step is to spin up some nodes and test our CorDapp.