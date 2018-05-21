.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing oracle services
=======================

This article covers *oracles*: network services that link the ledger to the outside world by providing facts that
affect the validity of transactions.

The current prototype includes an example oracle that provides an interest rate fixing service. It is used by the
IRS trading demo app.

Introduction to oracles
-----------------------

Oracles are a key concept in the block chain/decentralised ledger space. They can be essential for many kinds of
application, because we often wish to condition the validity of a transaction on some fact being true or false, but the ledger itself
has a design that is essentially functional: all transactions are *pure* and *immutable*. Phrased another way, a
contract cannot perform any input/output or depend on any state outside of the transaction itself. For example, there is no
way to download a web page or interact with the user from within a contract. It must be this way because everyone must
be able to independently check a transaction and arrive at an identical conclusion regarding its validity for the ledger to maintain its
integrity: if a transaction could evaluate to "valid" on one computer and then "invalid" a few minutes later on a
different computer, the entire shared ledger concept wouldn't work.

But transaction validity does often depend on data from the outside world - verifying that an
interest rate swap is paying out correctly may require data on interest rates, verifying that a loan has reached
maturity requires knowledge about the current time, knowing which side of a bet receives the payment may require
arbitrary facts about the real world (e.g. the bankruptcy or solvency of a company or country), and so on.

We can solve this problem by introducing services that create digitally signed data structures which assert facts.
These structures can then be used as an input to a transaction and distributed with the transaction data itself. Because
the statements are themselves immutable and signed, it is impossible for an oracle to change its mind later and
invalidate transactions that were previously found to be valid. In contrast, consider what would happen if a contract
could do an HTTP request: it's possible that an answer would change after being downloaded, resulting in loss of
consensus.

The two basic approaches
~~~~~~~~~~~~~~~~~~~~~~~~

The architecture provides two ways of implementing oracles with different tradeoffs:

1. Using commands
2. Using attachments

When a fact is encoded in a command, it is embedded in the transaction itself. The oracle then acts as a co-signer to
the entire transaction. The oracle's signature is valid only for that transaction, and thus even if a fact (like a
stock price) does not change, every transaction that incorporates that fact must go back to the oracle for signing.

When a fact is encoded as an attachment, it is a separate object to the transaction and is referred to by hash.
Nodes download attachments from peers at the same time as they download transactions, unless of course the node has
already seen that attachment, in which case it won't fetch it again. Contracts have access to the contents of
attachments when they run.

.. note:: Currently attachments do not support digital signing, but this is a planned feature.

As you can see, both approaches share a few things: they both allow arbitrary binary data to be provided to transactions
(and thus contracts). The primary difference is whether the data is a freely reusable, standalone object or whether it's
integrated with a transaction.

Here's a quick way to decide which approach makes more sense for your data source:

* Is your data *continuously changing*, like a stock price, the current time, etc? If yes, use a command.
* Is your data *commercially valuable*, like a feed which you are not allowed to resell unless it's incorporated into
  a business deal? If yes, use a command, so you can charge money for signing the same fact in each unique business
  context.
* Is your data *very small*, like a single number? If yes, use a command.
* Is your data *large*, *static* and *commercially worthless*, for instance, a holiday calendar? If yes, use an
  attachment.
* Is your data *intended for human consumption*, like a PDF of legal prose, or an Excel spreadsheet? If yes, use an
  attachment.

Asserting continuously varying data
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Let's look at the interest rates oracle that can be found in the ``NodeInterestRates`` file. This is an example of
an oracle that uses a command because the current interest rate fix is a constantly changing fact.

The obvious way to implement such a service is this:

1. The creator of the transaction that depends on the interest rate sends it to the oracle.
2. The oracle inserts a command with the rate and signs the transaction.
3. The oracle sends it back.

But this has a problem - it would mean that the oracle has to be the first entity to sign the transaction, which might impose
ordering constraints we don't want to deal with (being able to get all parties to sign in parallel is a very nice thing).
So the way we actually implement it is like this:

1. The creator of the transaction that depends on the interest rate asks for the current rate. They can abort at this point
   if they want to.
2. They insert a command with that rate and the time it was obtained into the transaction.
3. They then send it to the oracle for signing, along with everyone else, potentially in parallel. The oracle checks that
   the command has the correct data for the asserted time, and signs if so.

This same technique can be adapted to other types of oracle.

The oracle consists of a core class that implements the query/sign operations (for easy unit testing), and then a separate
class that binds it to the network layer.

Here is an extract from the ``NodeInterestRates.Oracle`` class and supporting types:

.. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/contracts/FinanceTypes.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1

.. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/contracts/FinanceTypes.kt
    :language: kotlin
    :start-after: DOCSTART 2
    :end-before: DOCEND 2

.. sourcecode:: kotlin

   class Oracle {
       fun query(queries: List<FixOf>): List<Fix>

       fun sign(ftx: FilteredTransaction): TransactionSignature
   }

The fix contains a timestamp (the ``forDay`` field) that identifies the version of the data being requested. Since
there can be an arbitrary delay between a fix being requested via ``query`` and the signature being requested via
``sign``, this timestamp allows the Oracle to know which, potentially historical, value it is being asked to sign for.  This is an
important technique for continuously varying data.

Hiding transaction data from the oracle
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Because the transaction is sent to the oracle for signing, ordinarily the oracle would be able to see the entire contents
of that transaction including the inputs, output contract states and all the commands, not just the one (in this case)
relevant command.  This is an obvious privacy leak for the other participants.  We currently solve this using a
``FilteredTransaction``, which implements a Merkle Tree.  These reveal only the necessary parts of the transaction to the
oracle but still allow it to sign it by providing the Merkle hashes for the remaining parts.  See :doc:`key-concepts-oracles`
for more details.

Pay-per-play oracles
~~~~~~~~~~~~~~~~~~~~

Because the signature covers the transaction, and transactions may end up being forwarded anywhere, the fact itself
is independently checkable. However, this approach can still be useful when the data itself costs money, because the act
of issuing the signature in the first place can be charged for (e.g. by requiring the submission of a fresh
``Cash.State`` that has been re-assigned to a key owned by the oracle service). Because the signature covers the
*transaction* and not only the *fact*, this allows for a kind of weak pseudo-DRM over data feeds. Whilst a
contract could in theory include a transaction parsing and signature checking library, writing a contract in this way
would be conclusive evidence of intent to disobey the rules of the service (*res ipsa loquitur*). In an environment
where parties are legally identifiable, usage of such a contract would by itself be sufficient to trigger some sort of
punishment.

Implementing an oracle with continuously varying data
-----------------------------------------------------

Implement the core classes
~~~~~~~~~~~~~~~~~~~~~~~~~~

The key is to implement your oracle in a similar way to the ``NodeInterestRates.Oracle`` outline we gave above with
both a ``query`` and a ``sign`` method.  Typically you would want one class that encapsulates the parameters to the ``query``
method (``FixOf``, above), and a ``CommandData`` implementation (``Fix``, above) that encapsulates both an instance of
that parameter class and an instance of whatever the result of the ``query`` is (``BigDecimal`` above).

The ``NodeInterestRates.Oracle`` allows querying for multiple ``Fix`` objects but that is not necessary and is
provided for the convenience of callers who need multiple fixes and want to be able to do it all in one query request.

Assuming you have a data source and can query it, it should be very easy to implement your ``query`` method and the
parameter and ``CommandData`` classes.

Let's see how the ``sign`` method for ``NodeInterestRates.Oracle`` is written:

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1
   :dedent: 8

Here we can see that there are several steps:

1. Ensure that the transaction we have been sent is indeed valid and passes verification, even though we cannot see all
   of it
2. Check that we only received commands as expected, and each of those commands expects us to sign for them and is of
   the expected type (``Fix`` here)
3. Iterate over each of the commands we identified in the last step and check that the data they represent matches
   exactly our data source.  The final step, assuming we have got this far, is to generate a signature for the
   transaction and return it

Binding to the network
~~~~~~~~~~~~~~~~~~~~~~

.. note:: Before reading any further, we advise that you understand the concept of flows and how to write them and use
   them. See :doc:`flow-state-machines`.  Likewise some understanding of Cordapps, plugins and services will be helpful.
   See :doc:`running-a-node`.

The first step is to create the oracle as a service by annotating its class with ``@CordaService``.  Let's see how that's
done:

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
   :language: kotlin
   :start-after: DOCSTART 3
   :end-before: DOCEND 3
   :dedent: 4

The Corda node scans for any class with this annotation and initialises them. The only requirement is that the class provide
a constructor with a single parameter of type ``ServiceHub``.

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
   :language: kotlin
   :start-after: DOCSTART 2
   :end-before: DOCEND 2
   :dedent: 4

These two flows leverage the oracle to provide the querying and signing operations. They get reference to the oracle,
which will have already been initialised by the node, using ``ServiceHub.cordaService``. Both flows are annotated with
``@InitiatedBy``. This tells the node which initiating flow (which are discussed in the next section) they are meant to
be executed with.

Providing sub-flows for querying and signing
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We mentioned the client sub-flow briefly above.  They are the mechanism that clients, in the form of other flows, will
use to interact with your oracle.  Typically there will be one for querying and one for signing.  Let's take a look at
those for ``NodeInterestRates.Oracle``.

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/flows/RatesFixFlow.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1
   :dedent: 4

You'll note that the ``FixSignFlow`` requires a ``FilterTransaction`` instance which includes only ``Fix`` commands.
You can find a further explanation of this in :doc:`key-concepts-oracles`. Below you will see how to build such a
transaction with hidden fields.

.. _filtering_ref:

Using an oracle
---------------

The oracle is invoked through sub-flows to query for values, add them to the transaction as commands and then get
the transaction signed by the oracle.  Following on from the above examples, this is all encapsulated in a sub-flow
called ``RatesFixFlow``.  Here's the ``call`` method of that flow.

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/flows/RatesFixFlow.kt
   :language: kotlin
   :start-after: DOCSTART 2
   :end-before: DOCEND 2
   :dedent: 4

As you can see, this:

1. Queries the oracle for the fact using the client sub-flow for querying defined above
2. Does some quick validation
3. Adds the command to the transaction containing the fact to be signed for by the oracle
4. Calls an extension point that allows clients to generate output states based on the fact from the oracle
5. Builds filtered transaction based on filtering function extended from ``RatesFixFlow``
6. Requests the signature from the oracle using the client sub-flow for signing from above

Here's an example of it in action from ``FixingFlow.Fixer``.

.. literalinclude:: ../../samples/irs-demo/cordapp/src/main/kotlin/net/corda/irs/flows/FixingFlow.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1
   :dedent: 4

.. note::
    When overriding be careful when making the sub-class an anonymous or inner class (object declarations in Kotlin),
    because that kind of classes can access variables from the enclosing scope and cause serialization problems when
    checkpointed.

Testing
-------

The ``MockNetwork`` allows the creation of ``MockNode`` instances, which are simplified nodes which can be used for
testing (see :doc:`api-testing`). When creating the ``MockNetwork`` you supply a list of packages to scan for CorDapps.
Make sure the packages you provide include your oracle service, and it automatically be installed in the test nodes.
Then you can create an oracle node on the ``MockNetwork`` and insert any initialisation logic you want to use. In this
case, our ``Oracle`` service is in the ``net.corda.irs.api`` package, so the following test setup will install
the service in each node. Then an oracle node with an oracle service which is initialised with some data is created on
the mock network:

.. literalinclude:: ../../samples/irs-demo/cordapp/src/test/kotlin/net/corda/irs/api/OracleNodeTearOffTests.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1
   :dedent: 4

You can then write tests on your mock network to verify the nodes interact with your Oracle correctly.

.. literalinclude:: ../../samples/irs-demo/cordapp/src/test/kotlin/net/corda/irs/api/OracleNodeTearOffTests.kt
   :language: kotlin
   :start-after: DOCSTART 2
   :end-before: DOCEND 2
   :dedent: 4

See `here <https://github.com/corda/corda/samples/irs-demo/cordapp/src/test/kotlin/net/corda/irs/api/OracleNodeTearOffTests.kt>`_ for more examples.
