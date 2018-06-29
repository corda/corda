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

.. sourcecode:: kotlin

    @CordaSerializable
    data class FixOf(val name: String, val forDay: LocalDate, val ofTenor: Tenor)

.. sourcecode:: kotlin

    /** A [Fix] represents a named interest rate, on a given day, for a given duration. It can be embedded in a tx. */
    data class Fix(val of: FixOf, val value: BigDecimal) : CommandData

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

.. container:: codeset

    .. code-block:: kotlin

        fun sign(ftx: FilteredTransaction): TransactionSignature {
            ftx.verify()
            // Performing validation of obtained filtered components.
            fun commandValidator(elem: Command<*>): Boolean {
                require(services.myInfo.legalIdentities.first().owningKey in elem.signers && elem.value is Fix) {
                    "Oracle received unknown command (not in signers or not Fix)."
                }
                val fix = elem.value as Fix
                val known = knownFixes[fix.of]
                if (known == null || known != fix)
                    throw UnknownFix(fix.of)
                return true
            }

            fun check(elem: Any): Boolean {
                return when (elem) {
                    is Command<*> -> commandValidator(elem)
                    else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                }
            }

            require(ftx.checkWithFun(::check))
            ftx.checkCommandVisibility(services.myInfo.legalIdentities.first().owningKey)
            // It all checks out, so we can return a signature.
            //
            // Note that we will happily sign an invalid transaction, as we are only being presented with a filtered
            // version so we can't resolve or check it ourselves. However, that doesn't matter much, as if we sign
            // an invalid transaction the signature is worthless.
            return services.createSignature(ftx, services.myInfo.legalIdentities.first().owningKey)
        }

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

.. container:: codeset

    .. code-block:: kotlin

        @CordaService
        class Oracle(private val services: AppServiceHub) : SingletonSerializeAsToken() {
            private val mutex = ThreadBox(InnerState())

            init {
                // Set some default fixes to the Oracle, so we can smoothly run the IRS Demo without uploading fixes.
                // This is required to avoid a situation where the runnodes version of the demo isn't in a good state
                // upon startup.
                addDefaultFixes()
            }

The Corda node scans for any class with this annotation and initialises them. The only requirement is that the class provide
a constructor with a single parameter of type ``ServiceHub``.

.. container:: codeset

    .. code-block:: kotlin

        @InitiatedBy(RatesFixFlow.FixSignFlow::class)
        class FixSignHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = otherPartySession.receive<RatesFixFlow.SignRequest>().unwrap { it }
                val oracle = serviceHub.cordaService(Oracle::class.java)
                otherPartySession.send(oracle.sign(request.ftx))
            }
        }

        @InitiatedBy(RatesFixFlow.FixQueryFlow::class)
        class FixQueryHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
            object RECEIVED : ProgressTracker.Step("Received fix request")
            object SENDING : ProgressTracker.Step("Sending fix response")

            override val progressTracker = ProgressTracker(RECEIVED, SENDING)

            @Suspendable
            override fun call() {
                val request = otherPartySession.receive<RatesFixFlow.QueryRequest>().unwrap { it }
                progressTracker.currentStep = RECEIVED
                val oracle = serviceHub.cordaService(Oracle::class.java)
                val answers = oracle.query(request.queries)
                progressTracker.currentStep = SENDING
                otherPartySession.send(answers)
            }
        }

These two flows leverage the oracle to provide the querying and signing operations. They get reference to the oracle,
which will have already been initialised by the node, using ``ServiceHub.cordaService``. Both flows are annotated with
``@InitiatedBy``. This tells the node which initiating flow (which are discussed in the next section) they are meant to
be executed with.

Providing sub-flows for querying and signing
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

We mentioned the client sub-flow briefly above.  They are the mechanism that clients, in the form of other flows, will
use to interact with your oracle.  Typically there will be one for querying and one for signing.  Let's take a look at
those for ``NodeInterestRates.Oracle``.

.. container:: codeset

    .. code-block:: kotlin

        @InitiatingFlow
        class FixQueryFlow(val fixOf: FixOf, val oracle: Party) : FlowLogic<Fix>() {
            @Suspendable
            override fun call(): Fix {
                val oracleSession = initiateFlow(oracle)
                // TODO: add deadline to receive
                val resp = oracleSession.sendAndReceive<List<Fix>>(QueryRequest(listOf(fixOf)))

                return resp.unwrap {
                    val fix = it.first()
                    // Check the returned fix is for what we asked for.
                    check(fix.of == fixOf)
                    fix
                }
            }
        }

        @InitiatingFlow
        class FixSignFlow(val tx: TransactionBuilder, val oracle: Party,
                          val partialMerkleTx: FilteredTransaction) : FlowLogic<TransactionSignature>() {
            @Suspendable
            override fun call(): TransactionSignature {
                val oracleSession = initiateFlow(oracle)
                val resp = oracleSession.sendAndReceive<TransactionSignature>(SignRequest(partialMerkleTx))
                return resp.unwrap { sig ->
                    check(oracleSession.counterparty.owningKey.isFulfilledBy(listOf(sig.by)))
                    tx.toWireTransaction(serviceHub).checkSignature(sig)
                    sig
                }
            }
        }

You'll note that the ``FixSignFlow`` requires a ``FilterTransaction`` instance which includes only ``Fix`` commands.
You can find a further explanation of this in :doc:`key-concepts-oracles`. Below you will see how to build such a
transaction with hidden fields.

.. _filtering_ref:

Using an oracle
---------------

The oracle is invoked through sub-flows to query for values, add them to the transaction as commands and then get
the transaction signed by the oracle.  Following on from the above examples, this is all encapsulated in a sub-flow
called ``RatesFixFlow``.  Here's the ``call`` method of that flow.

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): TransactionSignature {
            progressTracker.currentStep = progressTracker.steps[1]
            val fix = subFlow(FixQueryFlow(fixOf, oracle))
            progressTracker.currentStep = WORKING
            checkFixIsNearExpected(fix)
            tx.addCommand(fix, oracle.owningKey)
            beforeSigning(fix)
            progressTracker.currentStep = SIGNING
            val mtx = tx.toWireTransaction(serviceHub).buildFilteredTransaction(Predicate { filtering(it) })
            return subFlow(FixSignFlow(tx, oracle, mtx))
        }

As you can see, this:

1. Queries the oracle for the fact using the client sub-flow for querying defined above
2. Does some quick validation
3. Adds the command to the transaction containing the fact to be signed for by the oracle
4. Calls an extension point that allows clients to generate output states based on the fact from the oracle
5. Builds filtered transaction based on filtering function extended from ``RatesFixFlow``
6. Requests the signature from the oracle using the client sub-flow for signing from above

Here's an example of it in action from ``FixingFlow.Fixer``.

.. container:: codeset

    .. code-block:: kotlin

        val addFixing = object : RatesFixFlow(ptx, handshake.payload.oracle, fixOf, BigDecimal.ZERO, BigDecimal.ONE) {
            @Suspendable
            override fun beforeSigning(fix: Fix) {
                newDeal.generateFix(ptx, StateAndRef(txState, handshake.payload.ref), fix)

                // We set the transaction's time-window: it may be that none of the contracts need this!
                // But it can't hurt to have one.
                ptx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            }

            @Suspendable
            override fun filtering(elem: Any): Boolean {
                return when (elem) {
                // Only expose Fix commands in which the oracle is on the list of requested signers
                // to the oracle node, to avoid leaking privacy
                    is Command<*> -> handshake.payload.oracle.owningKey in elem.signers && elem.value is Fix
                    else -> false
                }
            }
        }
        val sig = subFlow(addFixing)

.. note::
    When overriding be careful when making the sub-class an anonymous or inner class (object declarations in Kotlin),
    because that kind of classes can access variables from the enclosing scope and cause serialization problems when
    checkpointed.

Testing
-------

The ``MockNetwork`` allows the creation of ``MockNode`` instances, which are simplified nodes which can be used for
testing (see :doc:`api-testing`). When creating the ``MockNetwork`` you supply a list of packages to scan for CorDapps.
Make sure the packages you provide include your oracle service, and it will automatically be installed in the test nodes.
Then you can create an oracle node on the ``MockNetwork`` and insert any initialisation logic you want to use. In this
case, our ``Oracle`` service is in the ``net.corda.irs.api`` package, so the following test setup will install
the service in each node. Then an oracle node with an oracle service which is initialised with some data is created on
the mock network:

.. container:: codeset

    .. code-block:: kotlin

        fun setUp() {
            mockNet = MockNetwork(cordappPackages = listOf("net.corda.finance.contracts", "net.corda.irs"))
            aliceNode = mockNet.createPartyNode(ALICE_NAME)
            oracleNode = mockNet.createNode(MockNodeParameters(legalName = BOB_NAME)).apply {
                transaction {
                    services.cordaService(NodeInterestRates.Oracle::class.java).knownFixes = TEST_DATA
                }
            }
        }

You can then write tests on your mock network to verify the nodes interact with your Oracle correctly.

.. container:: codeset

    .. code-block:: kotlin

        @Test
        fun verify_that_the_oracle_signs_the_transaction_if_the_interest_rate_within_allowed_limit() {
            // Create a partial transaction
            val tx = TransactionBuilder(DUMMY_NOTARY)
                    .withItems(TransactionState(1000.DOLLARS.CASH issuedBy dummyCashIssuer.party ownedBy alice.party, Cash.PROGRAM_ID, DUMMY_NOTARY))
            // Specify the rate we wish to get verified by the oracle
            val fixOf = NodeInterestRates.parseFixOf("LIBOR 2016-03-16 1M")

            // Create a new flow for the fix
            val flow = FilteredRatesFlow(tx, oracle, fixOf, BigDecimal("0.675"), BigDecimal("0.1"))
            // Run the mock network and wait for a result
            mockNet.runNetwork()
            val future = aliceNode.startFlow(flow)
            mockNet.runNetwork()
            future.getOrThrow()

            // We should now have a valid rate on our tx from the oracle.
            val fix = tx.toWireTransaction(aliceNode.services).commands.map { it  }.first()
            assertEquals(fixOf, (fix.value as Fix).of)
            // Check that the response contains the valid rate, which is within the supplied tolerance
            assertEquals(BigDecimal("0.678"), (fix.value as Fix).value)
            // Check that the transaction has been signed by the oracle
            assertContains(fix.signers, oracle.owningKey)
        }

See `here <https://github.com/corda/corda/samples/irs-demo/cordapp/src/test/kotlin/net/corda/irs/api/OracleNodeTearOffTests.kt>`_ for more examples.
