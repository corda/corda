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

* ``TransactionBuilder``, a builder for a transaction in construction
* ``WireTransaction``, an immutable transaction
* ``SignedTransaction``, an immutable transaction with 1+ associated signatures
* ``LedgerTransaction``, a transaction that can be checked for validity

Here are the possible transitions between transaction states:

.. image:: resources/transaction-flow.png

TransactionBuilder
------------------
Creating a builder
^^^^^^^^^^^^^^^^^^
The first step when creating a transaction is to instantiate a ``TransactionBuilder``. We can create a builder for each
transaction type as follows:

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
Once we have a ``TransactionBuilder``, we need to gather together the various transaction components the transaction
will include.

Input states
~~~~~~~~~~~~
Input states are added to a transaction as ``StateAndRef`` instances. A ``StateAndRef`` combines:

* A ``ContractState`` representing the input state itself
* A ``StateRef`` pointing to the input among the outputs of the transaction that created it

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

A ``StateRef`` uniquely identifies an input state, allowing the notary to mark it as historic. It is made up of:

* The hash of the transaction that generated the state
* The state's index in the outputs of that transaction

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

The ``StateRef`` create a chain of pointers from the input states back to the transactions that created them. This
allows a node to work backwards and verify the entirety of the transaction chain.

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

In many cases (e.g. when we have a transaction that updates an existing state), we may want to create an output by
copying from the input state:

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
Commands are added to the transaction as ``Command`` instances. ``Command`` combines:

* A ``CommandData`` instance representing the type of the command
* A list of the command's required signers

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
Time windows represent the period of time during which the transaction must be notarised. They can have a start and an
end time, or be open at either end:

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

We can also define a time window as an ``Instant`` +/- a time tolerance (e.g. 30 seconds):

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 42
       :end-before: DOCEND 42
       :dedent: 12

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
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 43
       :end-before: DOCEND 43
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

You can also pass in objects one-by-one. This is the only way to add attachments:

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

To set the transaction builder's time-window, we can either set a time-window directly:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 44
       :end-before: DOCEND 44
       :dedent: 12

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
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 45
       :end-before: DOCEND 45
       :dedent: 12

Signing the builder
^^^^^^^^^^^^^^^^^^^
Once the builder is ready, we finalize it by signing it and converting it into a ``SignedTransaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 29
       :end-before: DOCEND 29
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 29
       :end-before: DOCEND 29
       :dedent: 12

This will sign the transaction with your legal identity key. You can also choose to use another one of your public keys:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 30
       :end-before: DOCEND 30
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 30
       :end-before: DOCEND 30
       :dedent: 12

Either way, the outcome of this process is to create a ``SignedTransaction``, which can no longer be modified.

SignedTransaction
-----------------
A ``SignedTransaction`` is a combination of:

* An immutable ``WireTransaction``
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
To verify a transaction, we need to retrieve any states in the transaction chain that our node doesn't
currently have in its local storage from the proposer(s) of the transaction. This process is handled by a built-in flow
called ``ResolveTransactionsFlow``. See :doc:`api-flows` for more details.

When verifying a ``SignedTransaction``, we don't verify the ``SignedTransaction`` *per se*, but rather the
``WireTransaction`` it contains. We extract this ``WireTransaction`` as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 31
       :end-before: DOCEND 31
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 31
       :end-before: DOCEND 31
       :dedent: 12

However, this still isn't enough. The ``WireTransaction`` holds its inputs as ``StateRef`` instances, and its
attachments as hashes. These do not provide enough information to properly validate the transaction's contents. To
resolve these into actual ``ContractState`` and ``Attachment`` instances, we need to use the ``ServiceHub`` to convert
the ``WireTransaction`` into a ``LedgerTransaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 32
       :end-before: DOCEND 32
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 32
       :end-before: DOCEND 32
       :dedent: 12

We can now *verify* the transaction to ensure that it satisfies the contracts of all the transaction's input and output
states:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 33
       :end-before: DOCEND 33
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 33
       :end-before: DOCEND 33
       :dedent: 12

We will generally also want to conduct some additional validation of the transaction, beyond what is provided for in
the contract. Here's an example of how we might do this:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 34
       :end-before: DOCEND 34
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 34
       :end-before: DOCEND 34
       :dedent: 12

Verifying the transaction's signatures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
We also need to verify that the transaction has all the required signatures, and that these signatures are valid, to
prevent tampering. We do this using ``SignedTransaction.verifyRequiredSignatures``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 35
       :end-before: DOCEND 35
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 35
       :end-before: DOCEND 35
       :dedent: 12

Alternatively, we can use ``SignedTransaction.verifySignaturesExcept``, which takes a ``vararg`` of the public keys for
which the signatures are allowed to be missing:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 36
       :end-before: DOCEND 36
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 36
       :end-before: DOCEND 36
       :dedent: 12

If the transaction is missing any signatures without the corresponding public keys being passed in, a
``SignaturesMissingException`` is thrown.

We can also choose to simply verify the signatures that are present:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 37
       :end-before: DOCEND 37
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 37
       :end-before: DOCEND 37
       :dedent: 12

However, BE VERY CAREFUL - this function provides no guarantees that the signatures are correct, or that none are
missing.

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Once we are satisfied with the contents and existing signatures over the transaction, we can add our signature to the
``SignedTransaction`` using:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 38
       :end-before: DOCEND 38
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 38
       :end-before: DOCEND 38
       :dedent: 12

As with the ``TransactionBuilder``, we can also choose to sign using another one of our public keys:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 39
       :end-before: DOCEND 39
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 39
       :end-before: DOCEND 39
       :dedent: 12

We can also generate a signature over the transaction without adding it to the transaction directly by using:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 40
       :end-before: DOCEND 40
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 40
       :end-before: DOCEND 40
       :dedent: 12

Or using another one of our public keys, as follows:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/FlowCookbook.kt
       :language: kotlin
       :start-after: DOCSTART 41
       :end-before: DOCEND 41
       :dedent: 12

    .. literalinclude:: ../../docs/source/example-code/src/main/java/net/corda/docs/FlowCookbookJava.java
       :language: java
       :start-after: DOCSTART 41
       :end-before: DOCEND 41
       :dedent: 12

Notarising and recording
^^^^^^^^^^^^^^^^^^^^^^^^
Notarising and recording a transaction is handled by a built-in flow called ``FinalityFlow``. See
:doc:`api-flows` for more details.
