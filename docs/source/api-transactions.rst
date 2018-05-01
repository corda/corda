.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Transactions
=================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-transactions`.

.. contents::

Transaction lifecycle
---------------------
Between its creation and its final inclusion on the ledger, a transaction will generally occupy one of three states:

* ``TransactionBuilder``. A transaction's initial state. This is the only state during which the transaction is
  mutable, so we must add all the required components before moving on.

* ``SignedTransaction``. The transaction now has one or more digital signatures, making it immutable. This is the
  transaction type that is passed around to collect additional signatures and that is recorded on the ledger.

* ``LedgerTransaction``. The transaction has been "resolved" - for example, its inputs have been converted from
  references to actual states - allowing the transaction to be fully inspected.

We can visualise the transitions between the three stages as follows:

.. image:: resources/transaction-flow.png

Transaction components
----------------------
A transaction consists of six types of components:

* 1+ states:

  * 0+ input states
  * 0+ output states

* 1+ commands
* 0+ attachments
* 0 or 1 time-window

  * A transaction with a time-window must also have a notary

Each component corresponds to a specific class in the Corda API. The following section describes each component class,
and how it is created.

Input states
^^^^^^^^^^^^
An input state is added to a transaction as a ``StateAndRef``, which combines:

* The ``ContractState`` itself
* A ``StateRef`` identifying this ``ContractState`` as the output of a specific transaction

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 21
        :end-before: DOCEND 21
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 21
        :end-before: DOCEND 21
        :dedent: 12

A ``StateRef`` uniquely identifies an input state, allowing the notary to mark it as historic. It is made up of:

* The hash of the transaction that generated the state
* The state's index in the outputs of that transaction

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 20
        :end-before: DOCEND 20
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 20
        :end-before: DOCEND 20
        :dedent: 12

The ``StateRef`` links an input state back to the transaction that created it. This means that transactions form
"chains" linking each input back to an original issuance transaction. This allows nodes verifying the transaction
to "walk the chain" and verify that each input was generated through a valid sequence of transactions.

.. note:: Corda supports a maximum of 2000 inputs for any given transaction.

Output states
^^^^^^^^^^^^^
Since a transaction's output states do not exist until the transaction is committed, they cannot be referenced as the
outputs of previous transactions. Instead, we create the desired output states as ``ContractState`` instances, and
add them to the transaction directly:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 22
        :end-before: DOCEND 22
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 22
        :end-before: DOCEND 22
        :dedent: 12

In cases where an output state represents an update of an input state, we may want to create the output state by basing
it on the input state:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 23
        :end-before: DOCEND 23
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 23
        :end-before: DOCEND 23
        :dedent: 12

Before our output state can be added to a transaction, we need to associate it with a contract. We can do this by
wrapping the output state in a ``StateAndContract``, which combines:

* The ``ContractState`` representing the output states
* A ``String`` identifying the contract governing the state

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 47
        :end-before: DOCEND 47
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 47
        :end-before: DOCEND 47
        :dedent: 12

Commands
^^^^^^^^
A command is added to the transaction as a ``Command``, which combines:

* A ``CommandData`` instance indicating the command's type
* A ``List<PublicKey>`` representing the command's required signers

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 24
        :end-before: DOCEND 24
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 24
        :end-before: DOCEND 24
        :dedent: 12

Attachments
^^^^^^^^^^^
Attachments are identified by their hash:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 25
        :end-before: DOCEND 25
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 25
        :end-before: DOCEND 25
        :dedent: 12

The attachment with the corresponding hash must have been uploaded ahead of time via the node's RPC interface.

Time-windows
^^^^^^^^^^^^
Time windows represent the period during which the transaction must be notarised. They can have a start and an end
time, or be open at either end:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 26
        :end-before: DOCEND 26
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 26
        :end-before: DOCEND 26
        :dedent: 12

We can also define a time window as an ``Instant`` plus/minus a time tolerance (e.g. 30 seconds):

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 42
        :end-before: DOCEND 42
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 42
        :end-before: DOCEND 42
        :dedent: 12

Or as a start-time plus a duration:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 43
        :end-before: DOCEND 43
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 43
        :end-before: DOCEND 43
        :dedent: 12

TransactionBuilder
------------------

Creating a builder
^^^^^^^^^^^^^^^^^^
The first step when creating a transaction proposal is to instantiate a ``TransactionBuilder``.

If the transaction has input states or a time-window, we need to instantiate the builder with a reference to the notary
that will notarise the inputs and verify the time-window:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 19
       :end-before: DOCEND 19
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 19
       :end-before: DOCEND 19
       :dedent: 12

We discuss the selection of a notary in :doc:`api-flows`.

If the transaction does not have any input states or a time-window, it does not require a notary, and can be
instantiated without one:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 46
        :end-before: DOCEND 46
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 46
        :end-before: DOCEND 46
        :dedent: 12

Adding items
^^^^^^^^^^^^
The next step is to build up the transaction proposal by adding the desired components.

We can add components to the builder using the ``TransactionBuilder.withItems`` method:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/TransactionBuilder.kt
       :language: kotlin
       :start-after: DOCSTART 1
       :end-before: DOCEND 1

``withItems`` takes a ``vararg`` of objects and adds them to the builder based on their type:

* ``StateAndRef`` objects are added as input states
* ``TransactionState`` and ``StateAndContract`` objects are added as output states

  * Both ``TransactionState`` and ``StateAndContract`` are wrappers around a ``ContractState`` output that link the
    output to a specific contract

* ``Command`` objects are added as commands
* ``SecureHash`` objects are added as attachments
* A ``TimeWindow`` object replaces the transaction's existing ``TimeWindow``, if any

Passing in objects of any other type will cause an ``IllegalArgumentException`` to be thrown.

Here's an example usage of ``TransactionBuilder.withItems``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 27
       :end-before: DOCEND 27
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 27
       :end-before: DOCEND 27
       :dedent: 12

There are also individual methods for adding components.

Here are the methods for adding inputs and attachments:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 28
        :end-before: DOCEND 28
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 28
        :end-before: DOCEND 28
        :dedent: 12

An output state can be added as a ``ContractState``, contract class name and notary:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 49
        :end-before: DOCEND 49
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 49
        :end-before: DOCEND 49
        :dedent: 12

We can also leave the notary field blank, in which case the transaction's default notary is used:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 50
        :end-before: DOCEND 50
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 50
        :end-before: DOCEND 50
        :dedent: 12

Or we can add the output state as a ``TransactionState``, which already specifies the output's contract and notary:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 51
        :end-before: DOCEND 51
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 51
        :end-before: DOCEND 51
        :dedent: 12

Commands can be added as a ``Command``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 52
        :end-before: DOCEND 52
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 52
        :end-before: DOCEND 52
        :dedent: 12

Or as ``CommandData`` and a ``vararg PublicKey``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
        :language: kotlin
        :start-after: DOCSTART 53
        :end-before: DOCEND 53
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
        :language: java
        :start-after: DOCSTART 53
        :end-before: DOCEND 53
        :dedent: 12

For the time-window, we can set a time-window directly:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 44
       :end-before: DOCEND 44
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 44
       :end-before: DOCEND 44
       :dedent: 12

Or define the time-window as a time plus a duration (e.g. 45 seconds):

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 45
       :end-before: DOCEND 45
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 45
       :end-before: DOCEND 45
       :dedent: 12

Signing the builder
^^^^^^^^^^^^^^^^^^^
Once the builder is ready, we finalize it by signing it and converting it into a ``SignedTransaction``.

We can either sign with our legal identity key:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 29
       :end-before: DOCEND 29
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 29
       :end-before: DOCEND 29
       :dedent: 12

Or we can also choose to use another one of our public keys:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 30
       :end-before: DOCEND 30
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 30
       :end-before: DOCEND 30
       :dedent: 12

Either way, the outcome of this process is to create an immutable ``SignedTransaction`` with our signature over it.

SignedTransaction
-----------------
A ``SignedTransaction`` is a combination of:

* An immutable transaction
* A list of signatures over that transaction

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/SignedTransaction.kt
       :language: kotlin
       :start-after: DOCSTART 1
       :end-before: DOCEND 1

Before adding our signature to the transaction, we'll want to verify both the transaction's contents and the
transaction's signatures.

Verifying the transaction's contents
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
If a transaction has inputs, we need to retrieve all the states in the transaction's dependency chain before we can
verify the transaction's contents. This is because the transaction is only valid if its dependency chain is also valid.
We do this by requesting any states in the chain that our node doesn't currently have in its local storage from the
proposer(s) of the transaction. This process is handled by a built-in flow called ``ReceiveTransactionFlow``.
See :doc:`api-flows` for more details.

We can now verify the transaction's contents to ensure that it satisfies the contracts of all the transaction's input
and output states:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 33
       :end-before: DOCEND 33
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 33
       :end-before: DOCEND 33
       :dedent: 16

Checking that the transaction meets the contract constraints is only part of verifying the transaction's contents. We
will usually also want to perform our own additional validation of the transaction contents before signing, to ensure
that the transaction proposal represents an agreement we wish to enter into.

However, the ``SignedTransaction`` holds its inputs as ``StateRef`` instances, and its attachments as ``SecureHash``
instances, which do not provide enough information to properly validate the transaction's contents. We first need to
resolve the ``StateRef`` and ``SecureHash`` instances into actual ``ContractState`` and ``Attachment`` instances, which
we can then inspect.

We achieve this by using the ``ServiceHub`` to convert the ``SignedTransaction`` into a ``LedgerTransaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 32
       :end-before: DOCEND 32
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 32
       :end-before: DOCEND 32
       :dedent: 16

We can now perform our additional verification. Here's a simple example:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 34
       :end-before: DOCEND 34
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 34
       :end-before: DOCEND 34
       :dedent: 16

Verifying the transaction's signatures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Aside from verifying that the transaction's contents are valid, we also need to check that the signatures are valid. A
valid signature over the hash of the transaction prevents tampering.

We can verify that all the transaction's required signatures are present and valid as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 35
       :end-before: DOCEND 35
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 35
       :end-before: DOCEND 35
       :dedent: 16

However, we'll often want to verify the transaction's existing signatures before all of them have been collected. For
this we can use ``SignedTransaction.verifySignaturesExcept``, which takes a ``vararg`` of the public keys for
which the signatures are allowed to be missing:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 36
       :end-before: DOCEND 36
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 36
       :end-before: DOCEND 36
       :dedent: 16

There is also an overload of ``SignedTransaction.verifySignaturesExcept``, which takes a ``Collection`` of the
public keys for which the signatures are allowed to be missing:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 54
       :end-before: DOCEND 54
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 54
       :end-before: DOCEND 54
       :dedent: 16


If the transaction is missing any signatures without the corresponding public keys being passed in, a
``SignaturesMissingException`` is thrown.

We can also choose to simply verify the signatures that are present:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 37
       :end-before: DOCEND 37
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 37
       :end-before: DOCEND 37
       :dedent: 16

Be very careful, however - this function neither guarantees that the signatures that are present are required, nor
checks whether any signatures are missing.

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Once we are satisfied with the contents and existing signatures over the transaction, we add our signature to the
``SignedTransaction`` to indicate that we approve the transaction.

We can sign using our legal identity key, as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 38
       :end-before: DOCEND 38
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 38
       :end-before: DOCEND 38
       :dedent: 12

Or we can choose to sign using another one of our public keys:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 39
       :end-before: DOCEND 39
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 39
       :end-before: DOCEND 39
       :dedent: 12

We can also generate a signature over the transaction without adding it to the transaction directly.

We can do this with our legal identity key:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 40
       :end-before: DOCEND 40
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 40
       :end-before: DOCEND 40
       :dedent: 12

Or using another one of our public keys:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 41
       :end-before: DOCEND 41
       :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 41
       :end-before: DOCEND 41
       :dedent: 12

Notarising and recording
^^^^^^^^^^^^^^^^^^^^^^^^
Notarising and recording a transaction is handled by a built-in flow called ``FinalityFlow``. See :doc:`api-flows` for
more details.