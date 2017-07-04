.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Transactions
=================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-transactions`.

Transaction types
-----------------
There are two types of transaction in Corda:

* ``TransactionType.NotaryChange``, used to change the notary for a set of states
* ``TransactionType.General``, for transactions other than notary-change transactions

Notary-change transactions
^^^^^^^^^^^^^^^^^^^^^^^^^^
A single Corda network will usually have multiple notary services. To commit a transaction, we require a signature
from the notary service associated with each input state. If we tried to commit a transaction where the input
states were associated with different notary services, the transaction would require a signature from multiple notary
services, creating a complicated multi-phase commit scenario. To prevent this, every input state in a transaction
must be associated the same notary.

However, we will often need to create a transaction involving input states associated with different notaries. Before
we can create this transaction, we will need to change the notary service associated with each state by:

* Deciding which notary service we want to notarise the transaction
* For each set of inputs states that point to the same notary service that isn't the desired notary service, creating a
  ``TransactionType.NotaryChange`` transaction that:

  * Consumes the input states pointing to the old notary
  * Outputs the same states, but that now point to the new notary

* Using the outputs of the notary-change transactions as inputs to a standard ``TransactionType.General`` transaction

In practice, this process is handled automatically by a built-in flow called ``NotaryChangeFlow``. See
:doc:`api-flows` for more details.

Transaction workflow
--------------------
There are four states the transaction can occupy:

* ``TransactionBuilder``, a mutable transaction-in-construction
* ``WireTransaction``, an immutable transaction
* ``SignedTransaction``, an immutable transaction with 1+ associated signatures
* ``LedgerTransaction``, a transaction that can be checked for validity

Here are the possible transitions between transaction states:

.. image:: resources/transaction-flow.png

TransactionBuilder
------------------
Creating a builder
^^^^^^^^^^^^^^^^^^
The first step when creating a transaction is to instantiate a ``TransactionBuilder``.

We create a builder for the two different transaction types as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 19
       :end-before: DOCEND 19
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 19
       :end-before: DOCEND 19
       :dedent: 12

Transaction components
^^^^^^^^^^^^^^^^^^^^^^
Input states
~~~~~~~~~~~~
The outputs of previous transactions are referenced using a ``StateRef``, which pairs the hash of the transaction that
generated the state with the state's index in the outputs of that transaction:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 20
       :end-before: DOCEND 20
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 20
       :end-before: DOCEND 20
       :dedent: 12

A ``StateRef`` uniquely identifies an input state, allowing the notary to mark it as historic.

Input states are then added to a transaction as ``StateAndRef`` instances. A ``StateAndRef`` combines a
``ContractState`` representing the input state with a pointer to the transaction that created it:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 21
       :end-before: DOCEND 21
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 21
       :end-before: DOCEND 21
       :dedent: 12

This series of pointers from the input states back to the transactions that created them is what allows a node to work
backwards and verify the entirety of the transaction chain.

Output states
~~~~~~~~~~~~~
Since a transaction's output states do not exist until the transaction is committed, they cannot be referenced as the
outputs of previous transactions. Instead, we create the desired output states as ``ContractState`` instances, and
add them to the transaction:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 22
       :end-before: DOCEND 22
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 22
       :end-before: DOCEND 22
       :dedent: 12

In many cases (e.g. when we have a transaction that's purpose is to update an existing state), we may also want to
create an output by copying from the input state:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 23
       :end-before: DOCEND 23
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 23
       :end-before: DOCEND 23
       :dedent: 12

Commands
~~~~~~~~
Commands are added to the transaction as ``Command`` instances. ``Command`` combines a ``CommandData`` instance
representing the type of the command with a list of the command's required signers. We define a command as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 24
       :end-before: DOCEND 24
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 24
       :end-before: DOCEND 24
       :dedent: 12

Attachments
~~~~~~~~~~~
Attachments are identified by their hash. The attachment with the corresponding hash must have been uploaded ahead of
time via the node's RPC interface:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 25
       :end-before: DOCEND 25
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 25
       :end-before: DOCEND 25
       :dedent: 12

Time-windows
~~~~~~~~~~~~
Time windows represent the period of time during which a transaction must be notarised. They can have a start and an end
time, or be open at either end:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 26
       :end-before: DOCEND 26
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 26
       :end-before: DOCEND 26
       :dedent: 12

Adding items
^^^^^^^^^^^^
The transaction builder is mutable. We add items to it using the ``TransactionBuilder.withItems`` method:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/TransactionBuilder.kt
       :language: kotlin
       :start-after: DOCSTART 1
       :end-before: DOCEND 1

``withItems`` takes a ``vararg`` of objects and adds them to the builder based on their type:

* ``StateAndRef`` objects are added as input states
* ``TransactionState`` and ``ContractState`` objects are added as output states
* ``Command`` objects are added as commands

Passing in objects of any other type will cause an ``IllegalArgumentException`` to be thrown.

Here's an example usage of ``TransactionBuilder.withItems``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 27
       :end-before: DOCEND 27
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 27
       :end-before: DOCEND 27
       :dedent: 12

You can also pass in objects one-by-one. This is required for attachments and time-windows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 28
       :end-before: DOCEND 28
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 28
       :end-before: DOCEND 28
       :dedent: 12

Signing the builder
^^^^^^^^^^^^^^^^^^^
Once the builder is ready, we finalize it by signing it and converting it into a ``SignedTransaction``:

.. container:: codeset

   .. sourcecode:: kotlin

        // Finalizes the builder by signing it with our primary signing key.
        val signedTx1 = serviceHub.signInitialTransaction(unsignedTx)

        // Finalizes the builder by signing it with a different key.
        val signedTx2 = serviceHub.signInitialTransaction(unsignedTx, otherKey)

        // Finalizes the builder by signing it with a set of keys.
        val signedTx3 = serviceHub.signInitialTransaction(unsignedTx, otherKeys)

   .. sourcecode:: java

        // Finalizes the builder by signing it with our primary signing key.
        final SignedTransaction signedTx1 = getServiceHub().signInitialTransaction(unsignedTx);

        // Finalizes the builder by signing it with a different key.
        final SignedTransaction signedTx2 = getServiceHub().signInitialTransaction(unsignedTx, otherKey);

        // Finalizes the builder by signing it with a set of keys.
        final SignedTransaction signedTx3 = getServiceHub().signInitialTransaction(unsignedTx, otherKeys);

SignedTransaction
-----------------
A ``SignedTransaction`` is a combination of an immutable ``WireTransaction`` and a list of signatures over that
transaction:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/SignedTransaction.kt
       :language: kotlin
       :start-after: DOCSTART 1
       :end-before: DOCEND 1

Verifying the signatures
^^^^^^^^^^^^^^^^^^^^^^^^
The signatures on a ``SignedTransaction`` have not necessarily been checked for validity. We check them using
``SignedTransaction.verifySignatures``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/SignedTransaction.kt
       :language: kotlin
       :start-after: DOCSTART 2
       :end-before: DOCEND 2

``verifySignatures`` takes a ``vararg`` of the public keys for which the signatures are allowed to be missing. If the
transaction is missing any signatures without the corresponding public keys being passed in, a
``SignaturesMissingException`` is thrown.

Verifying the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^
Verifying a transaction is a multi-step process:

* We check the transaction's signatures:

.. container:: codeset

   .. sourcecode:: kotlin

        subFlow(ResolveTransactionsFlow(transactionToVerify, partyWithTheFullChain))

   .. sourcecode:: java

        subFlow(new ResolveTransactionsFlow(transactionToVerify, partyWithTheFullChain));

* Before verifying the transaction, we need to retrieve from the proposer(s) of the transaction any parts of the
  transaction chain that our node doesn't currently have in its local storage:

.. container:: codeset

   .. sourcecode:: kotlin

        subFlow(ResolveTransactionsFlow(transactionToVerify, partyWithTheFullChain))

   .. sourcecode:: java

        subFlow(new ResolveTransactionsFlow(transactionToVerify, partyWithTheFullChain));

* To verify the transaction, we first need to resolve any state references and attachment hashes by converting the
  ``SignedTransaction`` into a ``LedgerTransaction``. We can then verify the fully-resolved transaction:

.. container:: codeset

   .. sourcecode:: kotlin

        partSignedTx.tx.toLedgerTransaction(serviceHub).verify()

   .. sourcecode:: java

        partSignedTx.getTx().toLedgerTransaction(getServiceHub()).verify();

* We will generally also want to conduct some custom validation of the transaction, beyond what is provided for in the
  contract:

.. container:: codeset

   .. sourcecode:: kotlin

        val ledgerTransaction = partSignedTx.tx.toLedgerTransaction(serviceHub)
        val inputStateAndRef = ledgerTransaction.inputs.single()
        val input = inputStateAndRef.state.data as MyState
        if (input.value > 1000000) {
            throw FlowException("Proposed input value too high!")
        }

   .. sourcecode:: java

        final LedgerTransaction ledgerTransaction = partSignedTx.getTx().toLedgerTransaction(getServiceHub());
        final StateAndRef inputStateAndRef = ledgerTransaction.getInputs().get(0);
        final MyState input = (MyState) inputStateAndRef.getState().getData();
        if (input.getValue() > 1000000) {
            throw new FlowException("Proposed input value too high!");
        }

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
We add an additional signature to an existing ``SignedTransaction`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        val fullySignedTx = serviceHub.addSignature(partSignedTx)

   .. sourcecode:: java

        SignedTransaction fullySignedTx = getServiceHub().addSignature(partSignedTx);

We can also generate a signature over the transaction without adding it to the transaction directly by using:

.. container:: codeset

   .. sourcecode:: kotlin

        val signature = serviceHub.createSignature(partSignedTx)

   .. sourcecode:: java

        DigitalSignature.WithKey signature = getServiceHub().createSignature(partSignedTx);

Notarising and recording
^^^^^^^^^^^^^^^^^^^^^^^^
Notarising and recording a transaction is handled by a built-in flow called ``FinalityFlow``. See
:doc:`api-flows` for more details.