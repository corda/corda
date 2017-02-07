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
application, because we often wish to condition a transaction on some fact being true or false, but the ledger itself
has a design that is essentially functional: all transactions are *pure* and *immutable*. Phrased another way, a
smart contract cannot perform any input/output or depend on any state outside of the transaction itself. There is no
way to download a web page or interact with the user, in a smart contract. It must be this way because everyone must
be able to independently check a transaction and arrive at an identical conclusion for the ledger to maintain its
integrity: if a transaction could evaluate to "valid" on one computer and then "invalid" a few minutes later on a
different computer, the entire shared ledger concept wouldn't work.

But it is often essential that transactions do depend on data from the outside world, for example, verifying that an
interest rate swap is paying out correctly may require data on interest rates, verifying that a loan has reached
maturity requires knowledge about the current time, knowing which side of a bet receives the payment may require
arbitrary facts about the real world (e.g. the bankruptcy or solvency of a company or country) ... and so on.

We can solve this problem by introducing services that create digitally signed data structures which assert facts.
These structures can then be used as an input to a transaction and distributed with the transaction data itself. Because
the statements are themselves immutable and signed, it is impossible for an oracle to change its mind later and
invalidate transactions that were previously found to be valid. In contrast, consider what would happen if a contract
could do an HTTP request: it's possible that an answer would change after being downloaded, resulting in loss of
consensus (breaks).

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

The obvious way to implement such a service is like this:

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

.. sourcecode:: kotlin

   /** A [FixOf] identifies the question side of a fix: what day, tenor and type of fix ("LIBOR", "EURIBOR" etc) */
   data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Duration)

   /** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
   data class Fix(val of: FixOf, val value: BigDecimal) : CommandData

   class Oracle {
       fun query(queries: List<FixOf>, deadline: Instant): List<Fix>

       fun sign(ftx: FilteredTransaction, merkleRoot: SecureHash): DigitalSignature.LegallyIdentifiable
   }

Because the fix contains a timestamp (the ``forDay`` field), that identifies the version of the data being requested,
there can be an arbitrary delay between a fix being requested via ``query`` and the signature being requested via ``sign``
as the Oracle can know which, potentially historical, value it is being asked to sign for.  This is an important
technique for continously varying data.

The ``query`` method takes a deadline, which is a point in time the requester is willing to wait until for the necessary
data to be available.  Not every oracle will need this.  This can be useful where data is expected to be available on a
particular schedule and we use scheduling functionality to automatically launch the processing associated with it.
We can schedule for the expected announcement (or publish) time and give a suitable deadline at which the lack of the
information being available and the delay to processing becomes significant and may need to be escalated.

Hiding transaction data from the oracle
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Because the transaction is sent to the oracle for signing, ordinarily the oracle would be able to see the entire contents
of that transaction including the inputs, output contract states and all the commands, not just the one (in this case)
relevant command.  This is an obvious privacy leak for the other participants.  We currently solve this with
``FilteredTransaction``-s and the use of Merkle Trees.  These reveal only the necessary parts of the transaction to the
oracle but still allow it to sign it by providing the Merkle hashes for the remaining parts.  See :doc:`merkle-trees` for
more details.

Pay-per-play oracles
~~~~~~~~~~~~~~~~~~~~

Because the signature covers the transaction, and transactions may end up being forwarded anywhere, the fact itself
is independently checkable. However, this approach can still be useful when the data itself costs money, because the act
of issuing the signature in the first place can be charged for (e.g. by requiring the submission of a fresh
``Cash.State`` that has been re-assigned to a key owned by the oracle service). Because the signature covers the
*transaction* and not only the *fact*, this allows for a kind of weak pseudo-DRM over data feeds. Whilst a smart
contract could in theory include a transaction parsing and signature checking library, writing a contract in this way
would be conclusive evidence of intent to disobey the rules of the service (*res ipsa loquitur*). In an environment
where parties are legally identifiable, usage of such a contract would by itself be sufficient to trigger some sort of
punishment.

Implementing an oracle with continuously varying data
-----------------------------------------------------

Implement the core classes
~~~~~~~~~~~~~~~~~~~~~~~~~~

The key is to implement your oracle in a similar way to the ``NodeInterestRates.Oracle`` outline we gave above with
both ``query`` and ``sign`` methods.  Typically you would want one class that encapsulates the parameters to the ``query``
method (``FixOf`` above), and a ``CommandData`` implementation (``Fix`` above) that encapsulates both an instance of
that parameter class and an instance of whatever the result of the ``query`` is (``BigDecimal`` above).

The ``NodeInterestRates.Oracle`` allows querying for multiple ``Fix``-es but that is not necessary and is
provided for the convenience of callers who might need multiple and can do it all in one query request.  Likewise
the *deadline* functionality is optional and can be avoided initially.

Let's see what parameters we pass to the constructor of this oracle.

.. sourcecode:: kotlin

   class Oracle(val identity: Party, private val signingKey: KeyPair, val clock: Clock) = TODO()

Here we see the oracle needs to have its own identity, so it can check which transaction commands it is expected to
sign for, and also needs a pair of signing keys with which it signs transactions.  The clock is used for the deadline
functionality which we will not discuss further here.

Assuming you have a data source and can query it, it should be very easy to implement your ``query`` method and the
parameter and ``CommandData`` classes.

Let's see how the ``sign`` method for ``NodeInterestRates.Oracle`` is written:

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1

Here we can see that there are several steps:

1. Ensure that the transaction we have been sent is indeed valid and passes verification, even though we cannot see all
   of it.
2. Check that we only received commands as expected, and each of those commands expects us to sign for them and is of
   the expected type (``Fix`` here).
3. Iterate over each of the commands we identified in the last step and check that the data they represent matches
   exactly our data source.  The final step, assuming we have got this far, is to generate a signature for the
   transaction and return it.

Binding to the network via a CorDapp plugin
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: Before reading any further, we advise that you understand the concept of flows and how to write them and use
   them. See :doc:`flow-state-machines`.  Likewise some understanding of Cordapps, plugins and services will be helpful.
   See :doc:`creating-a-cordapp`.

The first step is to create a service to host the oracle on the network.  Let's see how that's implemented:

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
   :language: kotlin
   :start-after: DOCSTART 2
   :end-before: DOCEND 2

This may look complicated, but really it's made up of some relatively simple elements (in the order they appear in the code):

1. Accept a ``PluginServiceHub`` in the constructor.  This is your interface to the Corda node.
2. Ensure you extend the abstract class ``SingletonSerializeAsToken`` (see :doc:`corda-plugins`).
3. Create an instance of your core oracle class that has the ``query`` and ``sign`` methods as discussed above.
4. Register your client sub-flows (in this case both in ``RatesFixFlow``.  See the next section) for querying and
   signing as initiating your service flows that actually do the querying and signing using your core oracle class instance.
5. Implement your service flows that call your core oracle class instance.

The final step is to register your service with the node via the plugin mechanism. Do this by
implementing a plugin.  Don't forget the resources file to register it with the ``ServiceLoader`` framework
(see :doc:`corda-plugins`).

.. sourcecode:: kotlin

   class Plugin : CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
   }

Providing client sub-flows for querying and signing
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We mentioned the client sub-flow briefly above.  They are the mechanism that clients, in the form of other flows, will
interact with your oracle.  Typically there will be one for querying and one for signing.  Let's take a look at
those for ``NodeInterestRates.Oracle``.

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/flows/RatesFixFlow.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1

You'll note that the ``FixSignFlow`` requires a ``FilterTransaction`` instance which includes only ``Fix`` commands.
You can find a further explanation of this in :doc:`merkle-trees`. Below you will see how to build such transaction with
hidden fields.

.. _filtering_ref:

Using an oracle
---------------

The oracle is invoked through sub-flows to query for values, add them to the transaction as commands and then get
the transaction signed by the oracle.  Following on from the above examples, this is all encapsulated in a sub-flow
called ``RatesFixFlow``.  Here's the ``call`` method of that flow.

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/flows/RatesFixFlow.kt
   :language: kotlin
   :start-after: DOCSTART 2
   :end-before: DOCEND 2

As you can see, this:

1. Queries the oracle for the fact using the client sub-flow for querying from above.
2. Does some quick validation.
3. Adds the command to the transaction containing the fact to be signed for by the oracle.
4. Calls an extension point that allows clients to generate output states based on the fact from the oracle.
5. Builds filtered transaction based on filtering function extended from ``RatesFixFlow``.
6. Requests the signature from the oracle using the client sub-flow for signing from above.
7. Adds the signature returned from the oracle.

Here's an example of it in action from ``FixingFlow.Fixer``.

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/flows/FixingFlow.kt
   :language: kotlin
   :start-after: DOCSTART 1
   :end-before: DOCEND 1

.. note::
    When overriding be careful when making the sub-class an anonymous or inner class (object declarations in Kotlin),
    because that kind of classes can access variables from the enclosing scope and cause serialization problems when
    checkpointed.
