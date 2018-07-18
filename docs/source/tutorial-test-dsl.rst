.. highlight:: kotlin
.. role:: kotlin(code)
    :language: kotlin
.. raw:: html


   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract test
=======================

This tutorial will take you through the steps required to write a contract test using Kotlin and Java.

The testing DSL allows one to define a piece of the ledger with transactions referring to each other, and ways of
verifying their correctness.

Testing single transactions
---------------------------

We start with the empty ledger:

.. container:: codeset

    .. sourcecode:: kotlin

        class CommercialPaperTest{
            @Test
            fun emptyLedger() {
                ledger {
                }
            }
            ...
        }

    .. sourcecode:: java

        import org.junit.Test;

        import static net.corda.testing.NodeTestUtils.ledger;

        public class CommercialPaperTest {
            @Test
            public void emptyLedger() {
                ledger(l -> {
                    return null;
                });
            }
        }

The DSL keyword ``ledger`` takes a closure that can build up several transactions and may verify their overall
correctness. A ledger is effectively a fresh world with no pre-existing transactions or services within it.

We will start with defining helper function that returns a ``CommercialPaper`` state:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 4

It's a ``CommercialPaper`` issued by ``MEGA_CORP`` with face value of $1000 and maturity date in 7 days.

Let's add a ``CommercialPaper`` transaction:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCPDoesntCompile() {
            val inState = getPaper()
            ledger {
                transaction {
                    input(CommercialPaper.CP_PROGRAM_ID) { inState }
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCPDoesntCompile() {
            ICommercialPaperState inState = getPaper();
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                });
                return Unit.INSTANCE;
            });
        }

We can add a transaction to the ledger using the ``transaction`` primitive. The transaction in turn may be defined by
specifying ``input``s, ``output``s, ``command``s and ``attachment``s.

The above ``input`` call is a bit special; transactions don't actually contain input states, just references
to output states of other transactions. Under the hood the above ``input`` call creates a dummy transaction in the
ledger (that won't be verified) which outputs the specified state, and references that from this transaction.

The above code however doesn't compile:

.. container:: codeset

    .. sourcecode:: kotlin

        Error:(29, 17) Kotlin: Type mismatch: inferred type is Unit but EnforceVerifyOrFail was expected

    .. sourcecode:: java

        Error:(35, 27) java: incompatible types: bad return type in lambda expression missing return value

This is deliberate: The DSL forces us to specify either ``verifies()`` or ```fails with`("some text")`` on the
last line of ``transaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 4

Let's take a look at a transaction that fails.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 3
        :end-before: DOCEND 3
        :dedent: 4

When run, that code produces the following error:

.. container:: codeset

    .. sourcecode:: kotlin

        net.corda.core.contracts.TransactionVerificationException$ContractRejection: java.lang.IllegalArgumentException: Failed requirement: the state is propagated

    .. sourcecode:: java

        net.corda.core.contracts.TransactionVerificationException$ContractRejection: java.lang.IllegalStateException: the state is propagated

The transaction verification failed, because we wanted to move paper but didn't specify an output - but the state should be propagated.
However we can specify that this is an intended behaviour by changing ``verifies()`` to ```fails with`("the state is propagated")``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 4

We can continue to build the transaction until it ``verifies``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 4

``output`` specifies that we want the input state to be transferred to ``ALICE`` and ``command`` adds the
``Move`` command itself, signed by the current owner of the input state, ``MEGA_CORP_PUBKEY``.

We constructed a complete signed commercial paper transaction and verified it. Note how we left in the ``fails with``
line - this is fine, the failure will be tested on the partially constructed transaction.

What should we do if we wanted to test what happens when the wrong party signs the transaction? If we simply add a
``command`` it will permanently ruin the transaction... Enter ``tweak``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 6
        :end-before: DOCEND 6
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 6
        :end-before: DOCEND 6
        :dedent: 4

``tweak`` creates a local copy of the transaction. This makes possible to locally "ruin" the transaction while not
modifying the original one, allowing testing of different error conditions.

We now have a neat little test that tests a single transaction. This is already useful, and in fact testing of a single
transaction in this way is very common. There is even a shorthand top-level ``transaction`` primitive that creates a
ledger with a single transaction:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 4

Chaining transactions
---------------------

Now that we know how to define a single transaction, let's look at how to define a chain of them:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 8
        :end-before: DOCEND 8
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 8
        :end-before: DOCEND 8
        :dedent: 4

In this example we declare that ``ALICE`` has $900 but we don't care where from. For this we can use
``unverifiedTransaction``. Note how we don't need to specify ``verifies()``.

Notice that we labelled output with ``"alice's $900"``, also in transaction named ``"Issuance"``
we labelled a commercial paper with ``"paper"``. Now we can subsequently refer to them in other transactions, e.g.
by ``input("alice's $900")`` or ``"paper".output<ICommercialPaperState>()``.

The last transaction named ``"Trade"`` exemplifies simple fact of selling the ``CommercialPaper`` to Alice for her $900,
$100 less than the face value at 10% interest after only 7 days.

We can also test whole ledger calling ``verifies()`` and ``fails()`` on the ledger level.
To do so let's create a simple example that uses the same input twice:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 4

The transactions ``verifies()`` individually, however the state was spent twice! That's why we need the global ledger
verification (``fails()`` at the end). As in previous examples we can use ``tweak`` to create a local copy of the whole ledger:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 10
        :end-before: DOCEND 10
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 10
        :end-before: DOCEND 10
        :dedent: 4
