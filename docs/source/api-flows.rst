.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Flows
==========

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-flows`.

An example flow
---------------
Let's imagine a flow for agreeing a basic ledger update between Alice and Bob. This flow will have two sides:

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
In practice, a flow is implemented as one or more communicating ``FlowLogic`` subclasses. Each ``FlowLogic`` subclass
must override ``FlowLogic.call()``, which describes the actions it will take as part of the flow.

So in the example above, we would have an ``Initiator`` ``FlowLogic`` subclass and a ``Responder`` ``FlowLogic``
subclass. The actions of the initiator's side of the flow would be defined in ``Initiator.call``, and the actions
of the responder's side of the flow would be defined in ``Responder.call``.

FlowLogic annotations
^^^^^^^^^^^^^^^^^^^^^
Any flow that you wish to start either directly via RPC or as a subflow must be annotated with the
``@InitiatingFlow`` annotation. Additionally, if you wish to start the flow via RPC, you must annotate it with the
``@StartableByRPC`` annotation.

Any flow that responds to a message from another flow must be annotated with the ``@InitiatedBy`` annotation.
``@InitiatedBy`` takes the class of the flow it is responding to as its single parameter.

So in our example, we would have:

.. container:: codeset

   .. sourcecode:: kotlin

        @InitiatingFlow
        @StartableByRPC
        class Initiator(): FlowLogic<Unit>() {

        ...

        @InitiatedBy(Initiator::class)
        class Responder(val otherParty: Party) : FlowLogic<Unit>() {

   .. sourcecode:: java

        @InitiatingFlow
        @StartableByRPC
        public static class Initiator extends FlowLogic<Unit> {

        ...

        @InitiatedBy(Initiator.class)
        public static class Responder extends FlowLogic<Void> {

Additionally, any flow that is started by a ``SchedulableState`` must be annotated with the ``@SchedulableFlow``
annotation.

ServiceHub
----------
Within ``FlowLogic.call``, the flow developer has access to the node's ``ServiceHub``, which provides access to the
various services the node provides. See :doc:`api-service-hub` for information about the services the ``ServiceHub``
offers.

Some common tasks performed using the ``ServiceHub`` are:

* Looking up your own identity or the identity of a counterparty using the ``networkMapCache``
* Identifying the providers of a given service (e.g. a notary service) using the ``networkMapCache``
* Retrieving states to use in a transaction using the ``vaultService``
* Retrieving attachments and past transactions to use in a transaction using the ``storageService``
* Creating a timestamp using the ``clock``
* Signing a transaction using the ``keyManagementService``

Common flow tasks
-----------------
There are a number of common tasks that you will need to perform within ``FlowLogic.call`` in order to agree ledger
updates. This section details the API for the most common tasks.

Retrieving information about other nodes
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
We use the network map to retrieve information about other nodes on the network:

.. container:: codeset

   .. sourcecode:: kotlin

        val networkMap = serviceHub.networkMapCache

        val allNodes = networkMap.partyNodes
        val allNotaryNodes = networkMap.notaryNodes
        val randomNotaryNode = networkMap.getAnyNotary()

        val alice = networkMap.getNodeByLegalName(X500Name("CN=Alice,O=Alice,L=London,C=GB"))
        val bob = networkMap.getNodeByLegalIdentityKey(bobsKey)

   .. sourcecode:: java

        final NetworkMapCache networkMap = getServiceHub().getNetworkMapCache();

        final List<NodeInfo> allNodes = networkMap.getPartyNodes();
        final List<NodeInfo> allNotaryNodes = networkMap.getNotaryNodes();
        final Party randomNotaryNode = networkMap.getAnyNotary(null);

        final NodeInfo alice = networkMap.getNodeByLegalName(new X500Name("CN=Alice,O=Alice,L=London,C=GB"));
        final NodeInfo bob = networkMap.getNodeByLegalIdentityKey(bobsKey);

Communication between parties
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
``FlowLogic`` instances communicate using three functions:

* ``send(otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``
* ``receive(receiveType: Class<R>, otherParty: Party)``
    * Receives an object of type ``receiveType`` from the ``otherParty``
* ``sendAndReceive(receiveType: Class<R>, otherParty: Party, payload: Any)``
    * Sends the ``payload`` object to the ``otherParty``, and receives an object of type ``receiveType`` back

Each ``FlowLogic`` subclass can be annotated to respond to messages from a given *counterparty* flow using the
``@InitiatedBy`` annotation. When a node first receives a message from a given ``FlowLogic.call()`` invocation, it
responds as follows:

* The node checks whether they have a ``FlowLogic`` subclass that is registered to respond to the ``FlowLogic`` that
  is sending the message:

    a. If yes, the node starts an instance of this ``FlowLogic`` by invoking ``FlowLogic.call()``
    b. Otherwise, the node ignores the message

* The counterparty steps through their ``FlowLogic.call()`` method until they encounter a call to ``receive()``, at
  which point they process the message from the initiator

Upon calling ``receive()``/``sendAndReceive()``, the ``FlowLogic`` is suspended until it receives a response.

UntrustworthyData
~~~~~~~~~~~~~~~~~
``send()`` and ``sendAndReceive()`` return a payload wrapped in an ``UntrustworthyData`` instance. This is a
reminder that any data received off the wire is untrustworthy and must be verified.

We verify the ``UntrustworthyData`` and retrieve its payload by calling ``unwrap``:

.. container:: codeset

   .. sourcecode:: kotlin

        val partSignedTx = receive<SignedTransaction>(otherParty).unwrap { partSignedTx ->
                val wireTx = partSignedTx.verifySignatures(keyPair.public, notaryPubKey)
                wireTx.toLedgerTransaction(serviceHub).verify()
                partSignedTx
            }

   .. sourcecode:: java

        final SignedTransaction partSignedTx = receive(SignedTransaction.class, otherParty)
            .unwrap(tx -> {
                try {
                    final WireTransaction wireTx = tx.verifySignatures(keyPair.getPublic(), notaryPubKey);
                    wireTx.toLedgerTransaction(getServiceHub()).verify();
                } catch (SignatureException ex) {
                    throw new FlowException(tx.getId() + " failed signature checks", ex);
                }
                return tx;
            });

Subflows
--------
Corda provides a number of built-in flows that should be used for handling common tasks. The most important are:

* ``CollectSignaturesFlow``, which should be used to collect a transaction's required signatures
* ``FinalityFlow``, which should be used to notarise and record a transaction
* ``ResolveTransactionsFlow``, which should be used to verify the chain of inputs to a transaction
* ``ContractUpgradeFlow``, which should be used to change a state's contract
* ``NotaryChangeFlow``, which should be used to change a state's notary

These flows are designed to be used as building blocks in your own flows. You invoke them by calling
``FlowLogic.subFlow`` from within your flow's ``call`` method. Here is an example from ``TwoPartyDealFlow.kt``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/TwoPartyDealFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 12

In this example, we are starting a ``CollectSignaturesFlow``, passing in a partially signed transaction, and
receiving back a fully-signed version of the same transaction.

Subflows in our example flow
^^^^^^^^^^^^^^^^^^^^^^^^^^^^
In practice, many of the actions in our example flow would be automated using subflows:

* Parts 2-4 of ``Initiator.call`` should be automated by invoking ``CollectSignaturesFlow``
* Part 5 of ``Initiator.call`` should be automated by invoking ``FinalityFlow``
* Part 1 of ``Responder.call`` should be automated by invoking ``SignTransactionFlow``
* Part 2 of ``Responder.call`` will be handled automatically when the counterparty invokes ``FinalityFlow``

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

Suspending flows
----------------
In order for nodes to be able to run multiple flows concurrently, and to allow flows to survive node upgrades and
restarts, flows need to be checkpointable and serializable to disk.

This is achieved by marking any function invoked from within ``FlowLogic.call()`` with an ``@Suspendable`` annotation.

We can see an example in ``CollectSignaturesFlow``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/flows/CollectSignaturesFlow.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1