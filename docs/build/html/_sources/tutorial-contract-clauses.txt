.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract using clauses
================================

This tutorial will take you through restructuring the commercial paper contract to use clauses. You should have
already completed ":doc:`tutorial-contract`".

Clauses are essentially micro-contracts which contain independent verification logic, and are composed together to form
a contract. With appropriate design, they can be made to be reusable, for example issuing contract state objects is
generally the same for all fungible contracts, so a single issuance clause can be shared. This cuts down on scope for
error, and improves consistency of behaviour.

Clauses can be composed of subclauses, either to combine clauses in different ways, or to apply specialised clauses.
In the case of commercial paper, we have a "Grouping" outermost clause, which will contain the "Issue", "Move" and
"Redeem" clauses. The result is a contract that looks something like this:

    1. Group input and output states together, and then apply the following clauses on each group:
        a. If an Issue command is present, run appropriate tests and end processing this group.
        b. If a Move command is present, run appropriate tests and end processing this group.
        c. If a Redeem command is present, run appropriate tests and end processing this group.

Commercial paper class
----------------------

To use the clause verification logic, the contract needs to call the ``verifyClauses()`` function, passing in the transaction,
a list of clauses to verify, and a collection of commands the clauses are expected to handle all of. This list of
commands is important because ``verifyClauses()`` checks that none of the commands are left unprocessed at the end, and
raises an error if they are. The following examples are trimmed to the modified class definition and added elements, for
brevity:

.. container:: codeset

   .. sourcecode:: kotlin

      class CommercialPaper : Contract {
          override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

          private fun extractCommands(tx: TransactionForContract): List<AuthenticatedObject<CommandData>>
              = tx.commands.select<Commands>()

          override fun verify(tx: TransactionForContract) = verifyClauses(tx, listOf(Clauses.Group()), extractCommands(tx))

   .. sourcecode:: java

      public class CommercialPaper implements Contract {
          @Override
          public SecureHash getLegalContractReference() {
              return SecureHash.Companion.sha256("https://en.wikipedia.org/wiki/Commercial_paper");
          }

          @Override
          public Collection<AuthenticatedObject<CommandData>> extractCommands(@NotNull TransactionForContract tx) {
              return tx.getCommands()
                      .stream()
                      .filter((AuthenticatedObject<CommandData> command) -> { return command.getValue() instanceof Commands; })
                      .collect(Collectors.toList());
          }

          @Override
          public void verify(@NotNull TransactionForContract tx) throws IllegalArgumentException {
              ClauseVerifier.verifyClauses(tx, Collections.singletonList(new Clause.Group()), extractCommands(tx));
          }

Clauses
-------

We'll tackle the inner clauses that contain the bulk of the verification logic, first, and the clause which handles
grouping of input/output states later. The inner clauses need to implement the ``GroupClause`` interface, which defines
the verify() function, and properties for key information on how the clause is processed. These properties specify the
command(s) which must be present in order for the clause to be matched, and what to do after processing the clause
depending on whether it was matched or not.

The ``verify()`` functions defined in the ``SingleClause`` and ``GroupClause`` interfaces is similar to the conventional
``Contract`` verification function, although it adds new parameters and returns the set of commands which it has processed.
Normally this returned set is identical to the commands matched in order to trigger the clause, however in some cases the
clause may process optional commands which it needs to report that it has handled, or may by designed to only process
the first (or otherwise) matched command.

The Move clause for the commercial paper contract is relatively simple, so lets start there:

.. container:: codeset

   .. sourcecode:: kotlin

        class Move: GroupClause<State, Issued<Terms>> {
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.END
            override val requiredCommands: Set<Class<out CommandData>>
                get() = setOf(Commands.Move::class.java)

            override fun verify(tx: TransactionForContract,
                                inputs: List<State>,
                                outputs: List<State>,
                                commands: Collection<AuthenticatedObject<CommandData>>,
                                token: Issued<Terms>): Set<CommandData> {
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

        public class Move implements GroupClause<State, State> {
            @Override
            public MatchBehaviour getIfNotMatched() {
                return MatchBehaviour.CONTINUE;
            }

            @Override
            public MatchBehaviour getIfMatched() {
                return MatchBehaviour.END;
            }

            @Override
            public Set<Class<? extends CommandData>> getRequiredCommands() {
                return Collections.singleton(Commands.Move.class);
            }

            @Override
            public Set<CommandData> verify(@NotNull TransactionForContract tx,
                                           @NotNull List<? extends State> inputs,
                                           @NotNull List<? extends State> outputs,
                                           @NotNull Collection<? extends AuthenticatedObject<? extends CommandData>> commands,
                                           @NotNull State token) {
                AuthenticatedObject<CommandData> cmd = requireSingleCommand(tx.getCommands(), JavaCommercialPaper.Commands.Move.class);
                // There should be only a single input due to aggregation above
                State input = single(inputs);

                requireThat(require -> {
                    require.by("the transaction is signed by the owner of the CP", cmd.getSigners().contains(input.getOwner()));
                    require.by("the state is propagated", outputs.size() == 1);
                    return Unit.INSTANCE;
                });
                // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                // the input ignoring the owner field due to the grouping.
                return Collections.singleton(cmd.getValue());
            }
        }

The post-processing ``MatchBehaviour`` options are:
    * CONTINUE
    * END
    * ERROR

In this case we process commands against each group, until the first matching clause is found, so we ``END`` on a match
and ``CONTINUE`` otherwise. ``ERROR`` can be used as a part of a clause which must always/never be matched.

Group Clause
------------

We need to wrap the move clause (as well as the issue and redeem clauses - see the relevant contract code for their
full specifications) in an outer clause. For this we extend the standard ``GroupClauseVerifier`` and specify how to
group input/output states, as well as the clauses to run on each group.


.. container:: codeset

   .. sourcecode:: kotlin

        class Group : GroupClauseVerifier<State, Issued<Terms>>() {
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.ERROR
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.END
            override val clauses: List<GroupClause<State, Issued<Terms>>>
                get() = listOf(
                        Clause.Redeem(),
                        Clause.Move(),
                        Clause.Issue()
                )

            override fun extractGroups(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, Issued<Terms>>>
                    = tx.groupStates<State, Issued<Terms>> { it.token }
        }

   .. sourcecode:: java

        public class Group extends GroupClauseVerifier<State, State> {
            @Override
            public MatchBehaviour getIfMatched() {
                return MatchBehaviour.END;
            }

            @Override
            public MatchBehaviour getIfNotMatched() {
                return MatchBehaviour.ERROR;
            }

            @Override
            public List<com.r3corda.core.contracts.clauses.GroupClause<State, State>> getClauses() {
                final List<GroupClause<State, State>> clauses = new ArrayList<>();

                clauses.add(new Clause.Redeem());
                clauses.add(new Clause.Move());
                clauses.add(new Clause.Issue());

                return clauses;
            }

            @Override
            public List<InOutGroup<State, State>> extractGroups(@NotNull TransactionForContract tx) {
                return tx.groupStates(State.class, State::withoutOwner);
            }
        }

We then pass this clause into the outer ``ClauseVerifier`` contract by returning it from the ``clauses`` property. We
also implement the ``extractCommands()`` function, which filters commands on the transaction down to the set the
contained clauses must handle (any unmatched commands at the end of clause verification results in an exception to be
thrown).

.. container:: codeset

   .. sourcecode:: kotlin

        override val clauses: List<SingleClause>
            get() = listOf(Clauses.Group())

        override fun extractCommands(tx: TransactionForContract): List<AuthenticatedObject<CommandData>>
            = tx.commands.select<Commands>()

   .. sourcecode:: java

        @Override
        public List<SingleClause> getClauses() {
            return Collections.singletonList(new Clause.Group());
        }

        @Override
        public Collection<AuthenticatedObject<CommandData>> extractCommands(@NotNull TransactionForContract tx) {
            return tx.getCommands()
                    .stream()
                    .filter((AuthenticatedObject<CommandData> command) -> { return command.getValue() instanceof Commands; })
                    .collect(Collectors.toList());
        }

Summary
-------

In summary the top level contract ``CommercialPaper`` specifies a single grouping clause of type
``CommercialPaper.Clauses.Group`` which in turn specifies ``GroupClause`` implementations for each type of command
(``Redeem``, ``Move`` and ``Issue``). This reflects the flow of verification: In order to verify a ``CommercialPaper``
we first group states, check which commands are specified, and run command-specific verification logic accordingly.