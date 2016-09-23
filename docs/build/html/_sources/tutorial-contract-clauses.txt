.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract using clauses
================================

This tutorial will take you through restructuring the commercial paper contract to use clauses. You should have
already completed ":doc:`tutorial-contract`".

Clauses are essentially micro-contracts which contain independent verification logic, and can be logically composed
together to form a contract. Clauses are designed to enable re-use of common logic, for example issuing state objects
is generally the same for all fungible contracts, so a common issuance clause can be inherited for each contract's
issue clause. This cuts down on scope for error, and improves consistency of behaviour. By splitting verification logic
into smaller chunks, they can also be readily tested in isolation.

Clauses can be composed of subclauses, for example the ``AllClause`` or ``AnyClause`` clauses take list of clauses
that they delegate to. Clauses can also change the scope of states and commands being verified, for example grouping
together fungible state objects and running a clause against each distinct group.

The commercial paper contract has a ``Group`` outermost clause, which contains the ``Issue``, ``Move`` and ``Redeem``
clauses. The result is a contract that looks something like this:

    1. Group input and output states together, and then apply the following clauses on each group:
        a. If an ``Issue`` command is present, run appropriate tests and end processing this group.
        b. If a ``Move`` command is present, run appropriate tests and end processing this group.
        c. If a ``Redeem`` command is present, run appropriate tests and end processing this group.

Commercial paper class
----------------------

To use the clause verification logic, the contract needs to call the ``verifyClause`` function, passing in the
transaction, a clause to verify, and a collection of commands the clauses are expected to handle all of. This list of
commands is important because ``verifyClause`` checks that none of the commands are left unprocessed at the end, and
raises an error if they are. The top level clause would normally be a composite clause (such as ``AnyComposition``,
``AllComposition``, etc.) which contains further clauses. The following examples are trimmed to the modified class
definition and added elements, for brevity:

.. container:: codeset

   .. sourcecode:: kotlin

      class CommercialPaper : Contract {
          override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

          override fun verify(tx: TransactionForContract) = verifyClause(tx, Clauses.Group(), tx.commands.select<Commands>())

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

Clauses
-------

We'll tackle the inner clauses that contain the bulk of the verification logic, first, and the clause which handles
grouping of input/output states later. The clauses must extend the ``Clause`` abstract class, which defines
the ``verify`` function, and the ``requiredCommands`` property used to determine the conditions under which a clause
is triggered. Composite clauses should extend the ``CompositeClause`` abstract class, which extends ``Clause`` to
add support for wrapping around multiple clauses.

The ``verify`` function defined in the ``Clause`` interface is similar to the conventional ``Contract`` verification
function, although it adds new parameters and returns the set of commands which it has processed. Normally this returned
set is identical to the ``requiredCommands`` used to trigger the clause, however in some cases the clause may process
further optional commands which it needs to report that it has handled.

The ``Move`` clause for the commercial paper contract is relatively simple, so we will start there:

.. container:: codeset

   .. sourcecode:: kotlin

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

   .. sourcecode:: java

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

Group Clause
------------

We need to wrap the move clause (as well as the issue and redeem clauses - see the relevant contract code for their
full specifications) in an outer clause that understands how to group contract states and objects. For this we extend
the standard ``GroupClauseVerifier`` and specify how to group input/output states, as well as the top-level to run on
each group. As with the top level clause on a contract, this is normally a composite clause that delegates to subclauses.


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

For the ``CommercialPaper`` contract, this is the top level clause for the contract, and is passed directly into
``verifyClause`` (see the example code at the top of this tutorial).

Summary
-------

In summary the top level contract ``CommercialPaper`` specifies a single grouping clause of type
``CommercialPaper.Clauses.Group`` which in turn specifies ``GroupClause`` implementations for each type of command
(``Redeem``, ``Move`` and ``Issue``). This reflects the flow of verification: In order to verify a ``CommercialPaper``
we first group states, check which commands are specified, and run command-specific verification logic accordingly.

Debugging
---------

Debugging clauses which have been composed together can be complicated due to the difficulty in knowing which clauses
have been matched, whether specific clauses failed to match or passed verification, etc. There is "trace" level
logging code in the clause verifier which evaluates which clauses will be matched and logs them, before actually
performing the validation. To enable this, ensure trace level logging is enabled on the ``Clause`` interface.
