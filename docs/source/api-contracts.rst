.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Contracts
==============

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

Contract
--------
Contracts are classes that implement the ``Contract`` interface. The ``Contract`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 5
        :end-before: DOCEND 5

``Contract`` has a single method, ``verify``, which takes a ``LedgerTransaction`` as input and returns
nothing. This function is used to check whether a transaction proposal is valid, as follows:

* We gather together the contracts of each of the transaction's input and output states
* We call each contract's ``verify`` function, passing in the transaction as an input
* The proposal is only valid if none of the ``verify`` calls throw an exception

``verify`` is executed in a sandbox:

* It does not have access to the enclosing scope
* The libraries available to it are whitelisted to disallow:
  * Network access
  * I/O such as disk or database access
  * Sources of randomness such as the current time or random number generators

This means that ``verify`` only has access to the properties defined on ``LedgerTransaction`` when deciding whether a
transaction is valid.

Here are the two simplest ``verify`` functions:

* A  ``verify`` that **accepts** all possible transactions:

.. container:: codeset

   .. sourcecode:: kotlin

        override fun verify(tx: LedgerTransaction) {
            // Always accepts!
        }

   .. sourcecode:: java

        @Override
        public void verify(LedgerTransaction tx) {
            // Always accepts!
        }

* A ``verify`` that **rejects** all possible transactions:

.. container:: codeset

   .. sourcecode:: kotlin

        override fun verify(tx: LedgerTransaction) {
            throw IllegalArgumentException("Always rejects!")
        }

   .. sourcecode:: java

        @Override
        public void verify(LedgerTransaction tx) {
            throw new IllegalArgumentException("Always rejects!");
        }

LedgerTransaction
-----------------
The ``LedgerTransaction`` object passed into ``verify`` has the following properties:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/transactions/LedgerTransaction.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Where:

* ``inputs`` are the transaction's inputs as ``List<StateAndRef<ContractState>>``
* ``outputs`` are the transaction's outputs as ``List<TransactionState<ContractState>>``
* ``commands`` are the transaction's commands and associated signers, as ``List<CommandWithParties<CommandData>>``
* ``attachments`` are the transaction's attachments as ``List<Attachment>``
* ``notary`` is the transaction's notary. This must match the notary of all the inputs
* ``timeWindow`` defines the window during which the transaction can be notarised

``LedgerTransaction`` exposes a large number of utility methods to access the transaction's contents:

* ``inputStates`` extracts the input ``ContractState`` objects from the list of ``StateAndRef``
* ``getInput``/``getOutput``/``getCommand``/``getAttachment`` extracts a component by index
* ``getAttachment`` extracts an attachment by ID
* ``inputsOfType``/``inRefsOfType``/``outputsOfType``/``outRefsOfType``/``commandsOfType`` extracts components based on
  their generic type
* ``filterInputs``/``filterInRefs``/``filterOutputs``/``filterOutRefs``/``filterCommands`` extracts components based on
  a predicate
* ``findInput``/``findInRef``/``findOutput``/``findOutRef``/``findCommand`` extracts the single component that matches
  a predicate, or throws an exception if there are multiple matches

requireThat
-----------
``verify`` can be written to manually throw an exception for each constraint:

.. container:: codeset

   .. sourcecode:: kotlin

        override fun verify(tx: LedgerTransaction) {
            if (tx.inputs.size > 0)
                throw IllegalArgumentException("No inputs should be consumed when issuing an X.")

            if (tx.outputs.size != 1)
                throw IllegalArgumentException("Only one output state should be created.")
        }

   .. sourcecode:: java

        public void verify(LedgerTransaction tx) {
            if (tx.getInputs().size() > 0)
                throw new IllegalArgumentException("No inputs should be consumed when issuing an X.");

            if (tx.getOutputs().size() != 1)
                throw new IllegalArgumentException("Only one output state should be created.");
        }

However, this is verbose. To impose a series of constraints, we can use ``requireThat`` instead:

.. container:: codeset

   .. sourcecode:: kotlin

        requireThat {
            "No inputs should be consumed when issuing an X." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputs.single() as XState
            "The sender and the recipient cannot be the same entity." using (out.sender != out.recipient)
            "All of the participants must be signers." using (command.signers.containsAll(out.participants))
            "The X's value must be non-negative." using (out.x.value > 0)
        }

   .. sourcecode:: java

        requireThat(require -> {
            require.using("No inputs should be consumed when issuing an X.",  tx.getInputs().isEmpty());
            require.using("Only one output state should be created.", tx.getOutputs().size() == 1);
            final XState out = (XState) tx.getOutputs().get(0);
            require.using("The sender and the recipient cannot be the same entity.", out.getSender() != out.getRecipient());
            require.using("All of the participants must be signers.", command.getSigners().containsAll(out.getParticipants()));
            require.using("The X's value must be non-negative.", out.getX().getValue() > 0);
            return null;
        });

For each <``String``, ``Boolean``> pair within ``requireThat``, if the boolean condition is false, an
``IllegalArgumentException`` is thrown with the corresponding string as the exception message. In turn, this
exception will cause the transaction to be rejected.

Commands
--------
``LedgerTransaction`` contains the commands as a list of ``CommandWithParties`` instances. ``CommandWithParties`` pairs
a ``CommandData`` with a list of required signers for the transaction:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 6
        :end-before: DOCEND 6

Where:

* ``signers`` is the list of each signer's ``PublicKey``
* ``signingParties`` is the list of the signer's identities, if known
* ``value`` is the object being signed (a command, in this case)

Branching verify with commands
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Generally, we will want to impose different constraints on a transaction based on its commands. For example, we will
want to impose different constraints on a cash issuance transaction to on a cash transfer transaction.

We can achieve this by extracting the command and using standard branching logic within ``verify``. Here, we extract
the single command of type ``XContract.Commands`` from the transaction, and branch ``verify`` accordingly:

.. container:: codeset

   .. sourcecode:: kotlin

        class XContract : Contract {
            interface Commands : CommandData {
                class Issue : TypeOnlyCommandData(), Commands
                class Transfer : TypeOnlyCommandData(), Commands
            }

            override fun verify(tx: LedgerTransaction) {
                val command = tx.findCommand<Commands> { true }

                when (command) {
                    is Commands.Issue -> {
                        // Issuance verification logic.
                    }
                    is Commands.Transfer -> {
                        // Transfer verification logic.
                    }
                }
            }
        }

   .. sourcecode:: java

        public class XContract implements Contract {
            public interface Commands extends CommandData {
                class Issue extends TypeOnlyCommandData implements Commands {}
                class Transfer extends TypeOnlyCommandData implements Commands {}
            }

            @Override
            public void verify(LedgerTransaction tx) {
                final Command<Commands> command = tx.findCommand(Commands.class, cmd -> true);

                if (command instanceof Commands.Issue) {
                    // Issuance verification logic.
                } else if (command instanceof Commands.Transfer) {
                    // Transfer verification logic.
                }
            }
        }