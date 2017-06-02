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
* ``SignedTransaction``, a ``WireTransaction`` with 1+ associated signatures
* ``LedgerTransaction``, a resolved ``WireTransaction`` that can be checked for contract validity

Here are the possible transitions between transaction states:

.. image:: resources/transaction-flow.png

TransactionBuilder
------------------
Creating a builder
^^^^^^^^^^^^^^^^^^
The first step when building a transaction is to create a ``TransactionBuilder``:

.. container:: codeset

   .. sourcecode:: kotlin

        // A general transaction builder.
        val generalTxBuilder = TransactionType.General.Builder()

        // A notary-change transaction builder.
        val notaryChangeTxBuilder = TransactionType.NotaryChange.Builder()

   .. sourcecode:: java

        // A general transaction builder.
        final TransactionBuilder generalTxBuilder = new TransactionType.General.Builder();

        // A notary-change transaction builder.
        final TransactionBuilder notaryChangeTxBuilder = new TransactionType.NotaryChange.Builder();

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

You can also add the following items to the transaction:

* ``TimeWindow`` objects, using ``TransactionBuilder.setTime``
* ``SecureHash`` objects referencing the hash of an attachment stored on the node, using
  ``TransactionBuilder.addAttachment``

Input states
~~~~~~~~~~~~
Input states are added to a transaction as ``StateAndRef`` instances, rather than as ``ContractState`` instances.

A ``StateAndRef`` combines a ``ContractState`` with a pointer to the transaction that created it. This series of
pointers from the input states back to the transactions that created them is what allows a node to work backwards and
verify the entirety of the transaction chain. It is defined as:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
       :language: kotlin
       :start-after: DOCSTART 7
       :end-before: DOCEND 7

Where ``StateRef`` is defined as:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
       :language: kotlin
       :start-after: DOCSTART 8
       :end-before: DOCEND 8

``StateRef.index`` is the state's position in the outputs of the transaction that created it. In this way, a
``StateRef`` allows a notary service to uniquely identify the existing states that a transaction is marking as historic.

Output states
~~~~~~~~~~~~~
Since a transaction's output states do not exist until the transaction is committed, they cannot be referenced as the
outputs of previous transactions. Instead, we create the desired output states as ``ContractState`` instances, and
add them to the transaction.

Commands
~~~~~~~~
Commands are added to the transaction as ``Command`` instances. ``Command`` combines a ``CommandData``
instance representing the type of the command with a list of the command's required signers. It is defined as:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
       :language: kotlin
       :start-after: DOCSTART 9
       :end-before: DOCEND 9

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

Verifying a transaction
^^^^^^^^^^^^^^^^^^^^^^^
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

Signing a transaction
^^^^^^^^^^^^^^^^^^^^^
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