.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Flows
==========

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-flows`.

.. contents::

An example flow
---------------
Before we discuss the API offered by the flow, let's consider what a standard flow may look like.

Imagine a flow for agreeing a basic ledger update between Alice and Bob. This flow will have two sides:

* An ``Initiator`` side, that will initiate the request to update the ledger
* A ``Responder`` side, that will respond to the request to update the ledger

Initiator
^^^^^^^^^
In our flow, the Initiator flow class will be doing the majority of the work:

*Part 1 - Build the transaction*

1. Choose a notary for the transaction
2. Create a transaction builder
3. Extract any input states from the vault and add them to the builder
4. Create any output states and add them to the builder
5. Add any commands, attachments and timestamps to the builder

*Part 2 - Sign the transaction*

6. Sign the transaction builder
7. Convert the builder to a signed transaction

*Part 3 - Verify the transaction*

8. Verify the transaction by running its contracts

*Part 4 - Gather the counterparty's signature*

9. Send the transaction to the counterparty
10. Wait to receive back the counterparty's signature
11. Add the counterparty's signature to the transaction
12. Verify the transaction's signatures

*Part 5 - Finalize the transaction*

13. Send the transaction to the notary
14. Wait to receive back the notarised transaction
15. Record the transaction locally
16. Store any relevant states in the vault
17. Send the transaction to the counterparty for recording

We can visualize the work performed by initiator as follows:

.. image:: resources/flow-overview.png

Responder
^^^^^^^^^
To respond to these actions, the responder takes the following steps:

*Part 1 - Sign the transaction*

1. Receive the transaction from the counterparty
2. Verify the transaction's existing signatures
3. Verify the transaction by running its contracts
4. Generate a signature over the transaction
5. Send the signature back to the counterparty

*Part 2 - Record the transaction*

6. Receive the notarised transaction from the counterparty
7. Record the transaction locally
8. Store any relevant states in the vault

FlowLogic
---------
In practice, a flow is implemented as one or more communicating ``FlowLogic`` subclasses. The ``FlowLogic``
subclass's constructor can take any number of arguments of any type. The generic of ``FlowLogic`` (e.g.
``FlowLogic<SignedTransaction>``) indicates the flow's return type.

.. container:: codeset

   .. sourcecode:: kotlin

        class Initiator(val arg1: Boolean,
                        val arg2: Int,
                        val counterparty: Party): FlowLogic<SignedTransaction>() { }

        class Responder(val otherParty: Party) : FlowLogic<Unit>() { }

   .. sourcecode:: java

        public static class Initiator extends FlowLogic<SignedTransaction> {
            private final boolean arg1;
            private final int arg2;
            private final Party counterparty;

            public Initiator(boolean arg1, int arg2, Party counterparty) {
                this.arg1 = arg1;
                this.arg2 = arg2;
                this.counterparty = counterparty;
            }

        }

        public static class Responder extends FlowLogic<Void> { }

FlowLogic annotations
---------------------
Any flow that you wish to start either directly via RPC or as a subflow must be annotated with the
``@InitiatingFlow`` annotation. Additionally, if you wish to start the flow via RPC, you must annotate it with the
``@StartableByRPC`` annotation:

.. container:: codeset

   .. sourcecode:: kotlin

        @InitiatingFlow
        @StartableByRPC
        class Initiator(): FlowLogic<Unit>() { }

   .. sourcecode:: java

        @InitiatingFlow
        @StartableByRPC
        public static class Initiator extends FlowLogic<Unit> { }

Meanwhile, any flow that responds to a message from another flow must be annotated with the ``@InitiatedBy`` annotation.
``@InitiatedBy`` takes the class of the flow it is responding to as its single parameter:

.. container:: codeset

   .. sourcecode:: kotlin

        @InitiatedBy(Initiator::class)
        class Responder(val otherParty: Party) : FlowLogic<Unit>() { }

   .. sourcecode:: java

        @InitiatedBy(Initiator.class)
        public static class Responder extends FlowLogic<Void> { }

Additionally, any flow that is started by a ``SchedulableState`` must be annotated with the ``@SchedulableFlow``
annotation.

Call
----
Each ``FlowLogic`` subclass must override ``FlowLogic.call()``, which describes the actions it will take as part of
the flow. For example, the actions of the initiator's side of the flow would be defined in ``Initiator.call``, and the
actions of the responder's side of the flow would be defined in ``Responder.call``.

In order for nodes to be able to run multiple flows concurrently, and to allow flows to survive node upgrades and
restarts, flows need to be checkpointable and serializable to disk. This is achieved by marking ``FlowLogic.call()``,
as well as any function invoked from within ``FlowLogic.call()``, with an ``@Suspendable`` annotation.

.. container:: codeset

   .. sourcecode:: kotlin

        class Initiator(val counterparty: Party): FlowLogic<Unit>() {
            @Suspendable
            override fun call() { }
        }

   .. sourcecode:: java

        public static class InitiatorFlow extends FlowLogic<Void> {
            private final Party counterparty;

            public Initiator(Party counterparty) {
                this.counterparty = counterparty;
            }

            @Suspendable
            @Override
            public Void call() throws FlowException { }

        }

ServiceHub
----------
Within ``FlowLogic.call``, the flow developer has access to the node's ``ServiceHub``, which provides access to the
various services the node provides. We will use the ``ServiceHub`` extensively in the examples that follow. You can
also see :doc:`api-service-hub` for information about the services the ``ServiceHub`` offers.

Common flow tasks
-----------------
There are a number of common tasks that you will need to perform within ``FlowLogic.call`` in order to agree ledger
updates. This section details the API for common tasks.

Transaction building
^^^^^^^^^^^^^^^^^^^^
The majority of the work performed during a flow will be to build, verify and sign a transaction. We cover this in
:doc:`api-transactions`.

Retrieving information about other nodes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
We can retrieve information about other nodes on the network and the services they offer using
``ServiceHub.networkMapCache``.

Notaries
~~~~~~~~
Remember that a transaction generally needs a notary to:

* Prevent double-spends if the transaction has inputs
* Serve as a timestamping authority if the transaction has a time-window

There are several ways to retrieve a notary from the network map:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 12

Specific counterparties
~~~~~~~~~~~~~~~~~~~~~~~
We can also use the network map to retrieve a specific counterparty:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 12

Specific services
~~~~~~~~~~~~~~~~~
Finally, we can use the map to identify nodes providing a specific service (e.g. a regulator or an oracle):

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 3
        :end-before: DOCEND 3
        :dedent: 12

Communication between parties
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
``FlowLogic`` instances communicate using three functions:

* ``send(otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``
* ``receive(receiveType: Class<R>, otherParty: Party)``
    * Receives an object of type ``receiveType`` from the ``otherParty``
* ``sendAndReceive(receiveType: Class<R>, otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``, and receives an object of type ``receiveType`` back

Send
~~~~
We can send arbitrary data to a counterparty:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 12

If this is the first ``send``, the counterparty will either:

1. Ignore the message if they are not registered to respond to messages from this flow.
2. Start the flow they have registered to respond to this flow, and run the flow until the first call to ``receive``,
   at which point they process the message. In other words, we are assuming that the counterparty is registered to
   respond to this flow, and has a corresponding ``receive`` call.

Receive
~~~~~~~
We can also wait to receive arbitrary data of a specific type from a counterparty. Again, this implies a corresponding
``send`` call in the counterparty's flow. A few scenarios:

* We never receive a message back. In the current design, the flow is paused until the node's owner kills the flow.
* Instead of sending a message back, the counterparty throws a ``FlowException``. This exception is propagated back
  to us, and we can use the error message to establish what happened.
* We receive a message back, but it's of the wrong type. In this case, a ``FlowException`` is thrown.
* We receive back a message of the correct type. All is good.

Upon calling ``receive`` (or ``sendAndReceive``), the ``FlowLogic`` is suspended until it receives a response.

We receive the data wrapped in an ``UntrustworthyData`` instance. This is a reminder that the data we receive may not
be what it appears to be! We must unwrap the ``UntrustworthyData`` using a lambda:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 12

We're not limited to sending to and receiving from a single counterparty. A flow can send messages to as many parties
as it likes, and each party can invoke a different response flow:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 6
        :end-before: DOCEND 6
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 6
        :end-before: DOCEND 6
        :dedent: 12

SendAndReceive
~~~~~~~~~~~~~~
We can also use a single call to send data to a counterparty and wait to receive data of a specific type back. The
type of data sent doesn't need to match the type of the data received back:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 12

Counterparty response
~~~~~~~~~~~~~~~~~~~~~
Suppose we're now on the ``Responder`` side of the flow. We just received the following series of messages from the
``Initiator``:

1. They sent us an ``Any`` instance
2. They waited to receive an ``Integer`` instance back
3. They sent a ``String`` instance and waited to receive a ``Boolean`` instance back

Our side of the flow must mirror these calls. We could do this as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 8
        :end-before: DOCEND 8
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 8
        :end-before: DOCEND 8
        :dedent: 12

Subflows
--------
Corda provides a number of built-in flows that should be used for handling common tasks. The most important are:

* ``CollectSignaturesFlow``, which should be used to collect a transaction's required signatures
* ``FinalityFlow``, which should be used to notarise and record a transaction
* ``ResolveTransactionsFlow``, which should be used to verify the chain of inputs to a transaction
* ``ContractUpgradeFlow``, which should be used to change a state's contract
* ``NotaryChangeFlow``, which should be used to change a state's notary

These flows are designed to be used as building blocks in your own flows. You invoke them by calling
``FlowLogic.subFlow`` from within your flow's ``call`` method. Let's look at three very common examples.

FinalityFlow
^^^^^^^^^^^^
``FinalityFlow`` allows us to notarise the transaction and get it recorded in the vault of the participants of all
the transaction's states:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 12

We can also choose to send the transaction to additional parties who aren't one of the state's participants:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 10
        :end-before: DOCEND 10
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 10
        :end-before: DOCEND 10
        :dedent: 12

Only one party has to call ``FinalityFlow`` for a given transaction to be recorded by all participants. It does
**not** need to be called by each participant individually.

CollectSignaturesFlow/SignTransactionFlow
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The list of parties who need to sign a transaction is dictated by the transaction's commands. Once we've signed a
transaction ourselves, we can automatically gather the signatures of the other required signers using
``CollectSignaturesFlow``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 15
        :end-before: DOCEND 15
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 15
        :end-before: DOCEND 15
        :dedent: 12

Each required signer will need to respond by invoking its own ``SignTransactionFlow`` subclass to check the
transaction and provide their signature if they are satisfied:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 16
        :end-before: DOCEND 16
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 16
        :end-before: DOCEND 16
        :dedent: 12

ResolveTransactionsFlow
^^^^^^^^^^^^^^^^^^^^^^^
Verifying a transaction will also verify every transaction in the transaction's dependency chain. So if we receive a
transaction from a counterparty and it has any dependencies, we'd need to download all of these dependencies
using``ResolveTransactionsFlow`` before verifying it:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 13
        :end-before: DOCEND 13
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 13
        :end-before: DOCEND 13
        :dedent: 12

We can also resolve a `StateRef` dependency chain:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 14
        :end-before: DOCEND 14
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 14
        :end-before: DOCEND 14
        :dedent: 12

FlowException
-------------
Suppose a node throws an exception while running a flow. Any counterparty flows waiting for a message from the node
(i.e. as part of a call to ``receive`` or ``sendAndReceive``) will be notified that the flow has unexpectedly
ended and will themselves end. However, the exception thrown will not be propagated back to the counterparties.

If you wish to notify any waiting counterparties of the cause of the exception, you can do so by throwing a
``FlowException``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/flows/FlowException.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

The flow framework will automatically propagate the ``FlowException`` back to the waiting counterparties.

There are many scenarios in which throwing a ``FlowException`` would be appropriate:

* A transaction doesn't ``verify()``
* A transaction's signatures are invalid
* The transaction does not match the parameters of the deal as discussed
* You are reneging on a deal

ProgressTracker
---------------
We can give our flow a progress tracker. This allows us to see the flow's progress visually in our node's CRaSH shell.

To provide a progress tracker, we have to override ``FlowLogic.progressTracker`` in our flow:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 17
        :end-before: DOCEND 17
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 17
        :end-before: DOCEND 17
        :dedent: 8

We then update the progress tracker's current step as we progress through the flow as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 18
        :end-before: DOCEND 18
        :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 18
        :end-before: DOCEND 18
        :dedent: 12