.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract using clauses
================================

This tutorial will take you through restructuring the commercial paper contract to use clauses. You should have
already completed ":doc:`tutorial-contract`".
As before, the example is focused on basic implementation of commercial paper, which is essentially a simpler version of a corporate
bond. A company issues CP with a particular face value, say $100, but sells it for less, say $90. The paper can be redeemed
for cash at a given date in the future. Thus this example would have a 10% interest rate with a single repayment.
Whole Kotlin code can be found in ``CommercialPaper.kt``.

What are clauses and why to use them?
-------------------------------------

Clauses are essentially micro-contracts which contain independent verification logic, and can be logically composed
together to form a contract. Clauses are designed to enable re-use of common verification parts, for example issuing state objects
is generally the same for all fungible contracts, so a common issuance clause can be inherited for each contract's
issue clause. This cuts down on scope for error, and improves consistency of behaviour. By splitting verification logic
into smaller chunks, they can also be readily tested in isolation.

How clauses work?
-----------------

We have different types of clauses, the most basic are the ones that define verification logic for particular command set.
We will see them later as elementary building blocks that commercial paper consist of - ``Move``, ``Issue`` and ``Redeem``.
As a developer you need to identify reusable parts of your contract and decide how they should be combined. It is where
composite clauses become useful. They gather many clause subcomponents and resolve how and which of them should be checked.

For example, assume that we want to verify a transaction using all constraints defined in separate clauses. We need to
wrap classes that define them into ``AllComposition`` composite clause. It assures that all clauses from that combination
match with commands in a transaction - only then verification logic can be executed.
It may be a little confusing, but composite clause is also a clause and you can even wrap it in the special grouping clause.
In ``CommercialPaper`` it looks like that:

.. image:: resources/commPaperClauses.png

The most basic types of composite clauses are ``AllComposition``, ``AnyComposition`` and ``FirstComposition``.
In this tutorial we will use ``GroupClauseVerifier`` and ``AnyComposition``. It's important to understand how they work.
Charts showing execution and more detailed information can be found in :doc:`clauses`.

.. _verify_ref:

Commercial paper class
----------------------

We start from defining ``CommercialPaper`` class. As in previous tutorial we need some elementary parts: ``Commands`` interface,
``generateMove``, ``generateIssue``, ``generateRedeem`` - so far so good that stays the same. The new part is verification and
``Clauses`` interface (you will see them later in code). Let's start from the basic structure:

.. container:: codeset

   .. sourcecode:: kotlin

        class CommercialPaper : Contract {
            override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

            override fun verify(tx: TransactionForContract) = verifyClause(tx, Clauses.Group(), tx.commands.select<Commands>())

            interface Commands : CommandData {
                data class Move(override val contractHash: SecureHash? = null) : FungibleAsset.Commands.Move, Commands
                class Redeem : TypeOnlyCommandData(), Commands
                data class Issue(override val nonce: Long = random63BitValue()) : IssueCommand, Commands
            }

   .. sourcecode:: java

      public class CommercialPaper implements Contract {
          @Override
          public SecureHash getLegalContractReference() {
              return SecureHash.Companion.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
          }

          @Override
          public void verify(@NotNull TransactionForContract tx) throws IllegalArgumentException {
              ClauseVerifier.verifyClause(tx, new Clauses.Group(), extractCommands(tx));
          }

        public interface Commands extends CommandData {
            class Move implements Commands {
                @Override
                public boolean equals(Object obj) { return obj instanceof Move; }
            }

            class Redeem implements Commands {
                @Override
                public boolean equals(Object obj) { return obj instanceof Redeem; }
            }

            class Issue implements Commands {
                @Override
                public boolean equals(Object obj) { return obj instanceof Issue; }
            }
        }

As you can see we used ``verifyClause`` function with ``Clauses.Group()`` in place of previous verification.
It's an entry point to running clause logic. ``verifyClause`` takes the transaction, a clause (usually a composite one)
to verify, and a collection of commands the clause is expected to handle all of. This list of commands is important because
``verifyClause`` checks that none of the commands are left unprocessed at the end, and raises an error if they are.

Simple Clauses
--------------

Let's move to constructing contract logic in terms of clauses language. Commercial paper contract has three commands and
three corresponding behaviours: ``Issue``, ``Move`` and ``Redeem``. Each of them has a specific set of requirements that must be satisfied -
perfect material for defining clauses. For brevity we will show only ``Move`` clause, rest is constructed in similar manner
and included in the ``CommercialPaper.kt`` code.

.. container:: codeset

   .. sourcecode:: kotlin

        interface Clauses {
            class Move: Clause<State, Commands, Issued<Terms>>() {
                override val requiredCommands: Set<Class<out CommandData>>
                    get() = setOf(Commands.Move::class.java)

                override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: List<AuthenticatedObject<Commands>>,
                                groupingKey: Issued<Terms>?): Set<Commands> {
                    val command = commands.requireSingleCommand<Commands.Move>()
                    val input = inputs.single()
                    requireThat {
                        "the transaction is signed by the owner of the CP" by (input.owner in command.signers)
                        "the state is propagated" by (outputs.size == 1)
                        // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                        // the input ignoring the owner field due to the grouping.
                    }
                    return setOf(command.value)
                }
            }
            ...

   .. sourcecode:: java

        public interface Clauses {
            class Move extends Clause<State, Commands, State> {
                @NotNull
                @Override
                public Set<Class<? extends CommandData>> getRequiredCommands() {
                    return Collections.singleton(Commands.Move.class);
                }

                @NotNull
                @Override
                public Set<Commands> verify(@NotNull TransactionForContract tx,
                                               @NotNull List<? extends State> inputs,
                                               @NotNull List<? extends State> outputs,
                                               @NotNull List<? extends AuthenticatedObject<? extends Commands>> commands,
                                               @NotNull State groupingKey) {
                    AuthenticatedObject<Commands.Move> cmd = requireSingleCommand(tx.getCommands(), Commands.Move.class);
                    // There should be only a single input due to aggregation above
                    State input = single(inputs);

                    if (!cmd.getSigners().contains(input.getOwner()))
                        throw new IllegalStateException("Failed requirement: the transaction is signed by the owner of the CP");

                    // Check the output CP state is the same as the input state, ignoring the owner field.
                    if (outputs.size() != 1) {
                        throw new IllegalStateException("the state is propagated");
                    }
                    // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                    // the input ignoring the owner field due to the grouping.
                    return Collections.singleton(cmd.getValue());
                }
            }
            ...

We took part of code for ``Command.Move`` verification from previous tutorial and put it into the verify function
of ``Move`` class. Notice that this class must extend the ``Clause`` abstract class, which defines
the ``verify`` function, and the ``requiredCommands`` property used to determine the conditions under which a clause
is triggered. In the above example it means that the clause will run verification when the ``Commands.Move`` is present in a transaction.

.. note:: Notice that commands refer to all input and output states in a transaction. For clause to be executed, transaction has
    to include all commands from ``requiredCommands`` set.

Few important changes:

-   ``verify`` function returns the set of commands which it has processed. Normally this returned set is identical to the
    ``requiredCommands`` used to trigger the clause, however in some cases the clause may process further optional commands
    which it needs to report that it has handled.

-   Verification takes new parameters. Usually inputs and outputs are some subset of the original transaction entries
    passed to the clause by outer composite or grouping clause. ``groupingKey`` is a key used to group original states.

As a simple example imagine input states:

1. 1000 GBP issued by Bank of England
2. 500 GBP issued by Bank of England
3. 1000 GBP issued by Bank of Scotland

We will group states by Issuer so in the first group we have inputs 1 and 2, in second group input number 3. Grouping keys are:
'GBP issued by Bank of England' and 'GBP issued by Bank of Scotland'.

How the states can be grouped and passed in that form to the ``Move`` clause? That leads us to the concept of ``GroupClauseVerifier``.

Group clause
------------

We may have a transaction with similar but unrelated state evolutions which need to be validated independently. It
makes sense to check ``Move`` command on groups of related inputs and outputs (see example above). Thus, we need to collect
relevant states together.
For this we extend the standard ``GroupClauseVerifier`` and specify how to group input/output states, as well as the top-level
clause to run on each group. In our example a top-level is a composite clause - ``AnyCompostion`` that delegates verification to
it's subclasses (wrapped move, issue, redeem). Any in this case means that it will take 0 or more clauses that match transaction commands.

.. container:: codeset

   .. sourcecode:: kotlin

        class Group : GroupClauseVerifier<State, Commands, Issued<Terms>>(
            AnyComposition(
                Redeem(),
                Move(),
                Issue())) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, Issued<Terms>>>
                    = tx.groupStates<State, Issued<Terms>> { it.token }
        }

   .. sourcecode:: java

        class Group extends GroupClauseVerifier<State, Commands, State> {
            public Group() {
                super(new AnyComposition<>(
                    new Clauses.Redeem(),
                    new Clauses.Move(),
                    new Clauses.Issue()
                ));
            }

            @NotNull
            @Override
            public List<InOutGroup<State, State>> groupStates(@NotNull TransactionForContract tx) {
                return tx.groupStates(State.class, State::withoutOwner);
            }
        }

For the ``CommercialPaper`` contract, ``Group`` is the main clause for the contract, and is passed directly into
``verifyClause`` (see the example code at the top of this tutorial). We used ``groupStates`` function here, it's worth reminding
how it works: :ref:`state_ref`.

Summary
-------

In summary the top level contract ``CommercialPaper`` specifies a single grouping clause of type
``CommercialPaper.Clauses.Group`` which in turn specifies ``GroupClause`` implementations for each type of command
(``Redeem``, ``Move`` and ``Issue``). This reflects the flow of verification: in order to verify a ``CommercialPaper``
we first group states, check which commands are specified, and run command-specific verification logic accordingly.

.. image:: resources/commPaperExecution.png

Debugging
---------

Debugging clauses which have been composed together can be complicated due to the difficulty in knowing which clauses
have been matched, whether specific clauses failed to match or passed verification, etc. There is "trace" level
logging code in the clause verifier which evaluates which clauses will be matched and logs them, before actually
performing the validation. To enable this, ensure trace level logging is enabled on the ``Clause`` interface.
