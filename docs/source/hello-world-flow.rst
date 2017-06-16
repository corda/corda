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

Let's start writing our flows. We'll do this by modifying either ``TemplateFlow.java`` or ``TemplateFlow.kt``.

FlowLogic
---------
Each flow is implemented as a ``FlowLogic`` subclass. You define the steps taken by the flow by overriding
``FlowLogic.call``.

We will define two ``FlowLogic`` instances communicating as a pair. The first will be called ``Initiator``, and will
be run by the sender of the IOU. The other will be called ``Acceptor``, and will be run by the recipient. We group
them together using a class (in Java) or a singleton object (in Kotlin) to show that they are conceptually related.

Overwrite the existing template code with the following:

.. container:: codeset

    .. code-block:: kotlin

        package com.template

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

        package com.template;

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

* ``FlowLogic.call`` has a return type that matches the type parameter passed to ``FlowLogic`` - this is the return
  type of running the flow
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
  2. Verify the transaction
  3. Sign the transaction ourselves
  4. Gather the acceptor's signature
  5. Optionally get the transaction notarised, to:

     * Protect against double-spends for transactions with inputs
     * Timestamp transactions that have a ``TimeWindow``

  6. Record the transaction in our vault
  7. Send the transaction to the acceptor so that they can record it too

On the acceptor side, we need to:

  1. Receive the partially-signed transaction from the initiator
  2. Verify its contents and signatures
  3. Append our signature and send it back to the initiator
  4. Wait to receive back the transaction from the initiator
  5. Record the transaction in our vault

Subflows
^^^^^^^^
Although our flow requirements look complex, we can delegate to existing flows to handle many of these tasks. A flow
that is invoked within the context of a larger flow to handle a repeatable task is called a *subflow*.

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
node's ``ServiceHub``. In particular, ``ServiceHub.networkMapCache`` provides information about the other nodes on the
network and the services that they offer.

Transaction components
~~~~~~~~~~~~~~~~~~~~~~
Now that we have our ``TransactionBuilder``, we need to create its components. Remember that we're trying to build
the following transaction:

  .. image:: resources/tutorial-transaction.png
     :scale: 25%
     :align: center

So we'll need the following:

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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty);
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

``FinalityFlow`` also returns a list of the notarised transactions. We extract the single item from this list and
return it.

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

As ``SignTransactionFlow`` is an abstract class, we have to subclass it and override
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
transaction that we wish to perform before we sign it. For example, we may want to:

* Check that the transaction contains an ``IOUState``
* Check that the IOU's value isn't too high

Well done! You've finished the flows!

Flow tests
----------
As with contracts, deploying nodes to manually test flows is not efficient. Instead, we can use Corda's flow-test
DSL to quickly test our flows. The flow-test DSL works by creating a network of lightweight, "mock" node
implementations on which we run our flows.

The first thing we need to do is create this mock network. Open either ``test/kotlin/com/template/flow/FlowTests.kt`` or
``test/java/com/template/contract/ContractTests.java``, and overwrite the existing code with:

.. container:: codeset

    .. code-block:: kotlin

        package com.template

        import net.corda.core.contracts.TransactionVerificationException
        import net.corda.core.getOrThrow
        import net.corda.testing.node.MockNetwork
        import net.corda.testing.node.MockNetwork.MockNode
        import org.junit.After
        import org.junit.Before
        import org.junit.Test
        import kotlin.test.assertEquals
        import kotlin.test.assertFailsWith

        class IOUFlowTests {
            lateinit var net: MockNetwork
            lateinit var a: MockNode
            lateinit var b: MockNode
            lateinit var c: MockNode

            @Before
            fun setup() {
                net = MockNetwork()
                val nodes = net.createSomeNodes(2)
                a = nodes.partyNodes[0]
                b = nodes.partyNodes[1]
                b.registerInitiatedFlow(IOUFlow.Acceptor::class.java)
                net.runNetwork()
            }

            @After
            fun tearDown() {
                net.stopNodes()
            }
        }

    .. code-block:: java

        package com.template;

        import com.google.common.collect.ImmutableList;
        import com.google.common.util.concurrent.ListenableFuture;
        import net.corda.core.contracts.ContractState;
        import net.corda.core.contracts.TransactionState;
        import net.corda.core.contracts.TransactionVerificationException;
        import net.corda.core.transactions.SignedTransaction;
        import net.corda.testing.node.MockNetwork;
        import net.corda.testing.node.MockNetwork.BasketOfNodes;
        import net.corda.testing.node.MockNetwork.MockNode;
        import org.junit.After;
        import org.junit.Before;
        import org.junit.Rule;
        import org.junit.Test;
        import org.junit.rules.ExpectedException;

        import java.util.List;

        import static org.hamcrest.CoreMatchers.instanceOf;
        import static org.junit.Assert.assertEquals;

        public class IOUFlowTests {
            private MockNetwork net;
            private MockNode a;
            private MockNode b;

            @Before
            public void setup() {
                net = new MockNetwork();
                BasketOfNodes nodes = net.createSomeNodes(2);
                a = nodes.getPartyNodes().get(0);
                b = nodes.getPartyNodes().get(1);
                b.registerInitiatedFlow(IOUFlow.Acceptor.class);
                net.runNetwork();
            }

            @After
            public void tearDown() {
                net.stopNodes();
            }

            @Rule
            public final ExpectedException exception = ExpectedException.none();
        }

This creates an in-memory network with mocked-out components. The network has two nodes, plus network map and notary
nodes. We register any responder flows (in our case, ``IOUFlow.Acceptor``) on our nodes as well.

Our first test will be to check that the flow rejects invalid IOUs:

.. container:: codeset

    .. code-block:: kotlin

        @Test
        fun `flow rejects invalid IOUs`() {
            val flow = IOUFlow.Initiator(-1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()

            // The IOUContract specifies that IOUs cannot have negative values.
            assertFailsWith<TransactionVerificationException> {future.getOrThrow()}
        }

    .. code-block:: java

        @Test
        public void flowRejectsInvalidIOUs() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(-1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();

            exception.expectCause(instanceOf(TransactionVerificationException.class));
            future.get();
        }

This code causes node A to run the ``IOUFlow.Initiator`` flow. The call to ``MockNetwork.runNetwork`` is required to
simulate the running of a real network.

We then assert that because we passed in a negative IOU value to the flow's constructor, the flow should fail with a
``TransactionVerificationException``. In other words, we are asserting that at some point in flow, the transaction is
verified (remember that ``IOUContract`` forbids negative value IOUs), causing the flow to fail.

Because flows need to be instrumented by a library called `Quasar <http://docs.paralleluniverse.co/quasar/>`_ that
allows the flows to be checkpointed and serialized to disk, you need to run these tests using the provided
``Run Flow Tests - Java`` or ``Run Flow Tests - Kotlin`` run-configurations.

Here is the full suite of tests we'll use for the ``IOUFlow``:

.. container:: codeset

    .. code-block:: kotlin

        @Test
        fun `flow rejects invalid IOUs`() {
            val flow = IOUFlow.Initiator(-1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()

            // The IOUContract specifies that IOUs cannot have negative values.
            assertFailsWith<TransactionVerificationException> {future.getOrThrow()}
        }

        @Test
        fun `SignedTransaction returned by the flow is signed by the initiator`() {
            val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()

            val signedTx = future.getOrThrow()
            signedTx.verifySignatures(b.services.legalIdentityKey)
        }

        @Test
        fun `SignedTransaction returned by the flow is signed by the acceptor`() {
            val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()

            val signedTx = future.getOrThrow()
            signedTx.verifySignatures(a.services.legalIdentityKey)
        }

        @Test
        fun `flow records a transaction in both parties' vaults`() {
            val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()
            val signedTx = future.getOrThrow()

            // We check the recorded transaction in both vaults.
            for (node in listOf(a, b)) {
                assertEquals(signedTx, node.storage.validatedTransactions.getTransaction(signedTx.id))
            }
        }

        @Test
        fun `recorded transaction has no inputs and a single output, the input IOU`() {
            val flow = IOUFlow.Initiator(1, b.info.legalIdentity)
            val future = a.services.startFlow(flow).resultFuture
            net.runNetwork()
            val signedTx = future.getOrThrow()

            // We check the recorded transaction in both vaults.
            for (node in listOf(a, b)) {
                val recordedTx = node.storage.validatedTransactions.getTransaction(signedTx.id)
                val txOutputs = recordedTx!!.tx.outputs
                assert(txOutputs.size == 1)

                val recordedState = txOutputs[0].data as IOUState
                assertEquals(recordedState.value, 1)
                assertEquals(recordedState.sender, a.info.legalIdentity)
                assertEquals(recordedState.recipient, b.info.legalIdentity)
            }
        }

    .. code-block:: java

        @Test
        public void flowRejectsInvalidIOUs() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(-1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();

            exception.expectCause(instanceOf(TransactionVerificationException.class));
            future.get();
        }

        @Test
        public void signedTransactionReturnedByTheFlowIsSignedByTheInitiator() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();

            SignedTransaction signedTx = future.get();
            signedTx.verifySignatures(b.getServices().getLegalIdentityKey());
        }

        @Test
        public void signedTransactionReturnedByTheFlowIsSignedByTheAcceptor() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();

            SignedTransaction signedTx = future.get();
            signedTx.verifySignatures(a.getServices().getLegalIdentityKey());
        }

        @Test
        public void flowRecordsATransactionInBothPartiesVaults() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();
            SignedTransaction signedTx = future.get();

            for (MockNode node : ImmutableList.of(a, b)) {
                assertEquals(signedTx, node.storage.getValidatedTransactions().getTransaction(signedTx.getId()));
            }
        }

        @Test
        public void recordedTransactionHasNoInputsAndASingleOutputTheInputIOU() throws Exception {
            IOUFlow.Initiator flow = new IOUFlow.Initiator(1, b.info.getLegalIdentity());
            ListenableFuture<SignedTransaction> future = a.getServices().startFlow(flow).getResultFuture();
            net.runNetwork();
            SignedTransaction signedTx = future.get();

            for (MockNode node : ImmutableList.of(a, b)) {
                SignedTransaction recordedTx = node.storage.getValidatedTransactions().getTransaction(signedTx.getId());
                List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
                assert(txOutputs.size() == 1);

                IOUState recordedState = (IOUState) txOutputs.get(0).getData();
                assert(recordedState.getValue() == 1);
                assertEquals(recordedState.getSender(), a.info.getLegalIdentity());
                assertEquals(recordedState.getRecipient(), b.info.getLegalIdentity());
            }
        }

Run these tests and make sure they all pass. If they do, its very likely that we have a working CorDapp.

Progress so far
---------------
We now have a flow that we can kick off on our node to completely automate the process of issuing an IOU onto the
ledger. Under the hood, this flow takes the form of two communicating ``FlowLogic`` subclasses.

We now have a complete CorDapp, made up of:

* The ``IOUState``, representing IOUs on the ledger
* The ``IOUContract``, controlling the evolution of ``IOUState`` objects over time
* The ``IOUFlow``, which transforms the creation of a new IOU on the ledger into a push-button process

The final step is to spin up some nodes and test our CorDapp.