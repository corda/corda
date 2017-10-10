.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the contract
====================

In Corda, the ledger is updated via transactions. Each transaction is a proposal to mark zero or more existing
states as historic (the inputs), while creating zero or more new states (the outputs).

It's easy to imagine that most CorDapps will want to impose some constraints on how their states evolve over time:

* A cash CorDapp would not want to allow users to create transactions that generate money out of thin air (at least
  without the involvement of a central bank or commercial bank)
* A loan CorDapp might not want to allow the creation of negative-valued loans
* An asset-trading CorDapp would not want to allow users to finalise a trade without the agreement of their counterparty

In Corda, we impose constraints on what transactions are allowed using contracts. These contracts are very different
to the smart contracts of other distributed ledger platforms. In Corda, contracts do not represent the current state of
the ledger. Instead, like a real-world contract, they simply impose rules on what kinds of agreements are allowed.

Every state is associated with a contract. A transaction is invalid if it does not satisfy the contract of every
input and output state in the transaction.

The Contract interface
----------------------
Just as every Corda state must implement the ``ContractState`` interface, every contract must implement the
``Contract`` interface:

.. container:: codeset

    .. code-block:: kotlin

        interface Contract {
            // Implements the contract constraints in code.
            @Throws(IllegalArgumentException::class)
            fun verify(tx: LedgerTransaction)
        }

You can read about function declarations in Kotlin `here <https://kotlinlang.org/docs/reference/functions.html>`_.

We can see that ``Contract`` expresses its constraints through a ``verify`` function that takes a transaction as input,
and:

  * Throws an ``IllegalArgumentException`` if it rejects the transaction proposal
  * Returns silently if it accepts the transaction proposal

Controlling IOU evolution
-------------------------
What would a good contract for an ``IOUState`` look like? There is no right or wrong answer - it depends on how you
want your CorDapp to behave.

For our CorDapp, let's impose the constraint that we only want to allow the creation of IOUs. We don't want nodes to
transfer them or redeem them for cash. One way to enforce this behaviour would be by imposing the following constraints:

* A transaction involving IOUs must consume zero inputs, and create one output of type ``IOUState``
* The transaction should also include a ``Create`` command, indicating the transaction's intent (more on commands
  shortly)
* For the transactions's output IOU state:

  * Its value must be non-negative
  * The lender and the borrower cannot be the same entity
  * The IOU's lender must sign the transaction

We can picture this transaction as follows:

  .. image:: resources/simple-tutorial-transaction.png
     :scale: 15%
     :align: center

Defining IOUContract
--------------------

Let's write a contract that enforces these constraints. We'll do this by modifying either ``TemplateContract.java`` or
``App.kt`` and updating ``TemplateContract`` to define an ``IOUContract``:

.. container:: codeset

    .. code-block:: kotlin

        ...

        import net.corda.core.contracts.*

        ...

        class IOUContract : Contract {
            // Our Create command.
            class Create : CommandData

            override fun verify(tx: LedgerTransaction) {
                val command = tx.commands.requireSingleCommand<Create>()

                requireThat {
                    // Constraints on the shape of the transaction.
                    "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                    "There should be one output state of type IOUState." using (tx.outputs.size == 1)

                    // IOU-specific constraints.
                    val out = tx.outputs.single().data as IOUState
                    "The IOU's value must be non-negative." using (out.value > 0)
                    "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)

                    // Constraints on the signers.
                    "There must only be one signer." using (command.signers.toSet().size == 1)
                    "The signer must be the lender." using (command.signers.contains(out.lender.owningKey))
                }
            }
        }

    .. code-block:: java

        package com.template.contract;

        import com.template.state.IOUState;
        import net.corda.core.contracts.CommandWithParties;
        import net.corda.core.contracts.CommandData;
        import net.corda.core.contracts.Contract;
        import net.corda.core.transactions.LedgerTransaction;
        import net.corda.core.identity.Party;

        import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
        import static net.corda.core.contracts.ContractsDSL.requireThat;

        public class IOUContract implements Contract {
            // Our Create command.
            public static class Create implements CommandData {}

            @Override
            public void verify(LedgerTransaction tx) {
                final CommandWithParties<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

                requireThat(check -> {
                    // Constraints on the shape of the transaction.
                    check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                    check.using("There should be one output state of type IOUState.", tx.getOutputs().size() == 1);

                    // IOU-specific constraints.
                    final IOUState out = (IOUState) tx.getOutputs().get(0).getData();
                    final Party lender = out.getLender();
                    final Party borrower = out.getBorrower();
                    check.using("The IOU's value must be non-negative.",out.getValue() > 0);
                    check.using("The lender and the borrower cannot be the same entity.", lender != borrower);

                    // Constraints on the signers.
                    check.using("There must only be one signer.", command.getSigners().size() == 1);
                    check.using("The signer must be the lender.", command.getSigners().contains(lender.getOwningKey()));

                    return null;
                });
            }
        }

If you're following along in Java, you'll also need to rename ``TemplateContract.java`` to ``IOUContract.java``.

Let's walk through this code step by step.

The Create command
^^^^^^^^^^^^^^^^^^
The first thing we add to our contract is a *command*. Commands serve two functions:

* They indicate the transaction's intent, allowing us to perform different verification given the situation. For
  example, a transaction proposing the creation of an IOU could have to satisfy different constraints to one redeeming
  an IOU
* They allow us to define the required signers for the transaction. For example, IOU creation might require signatures
  from the lender only, whereas the transfer of an IOU might require signatures from both the IOU's borrower and lender

Our contract has one command, a ``Create`` command. All commands must implement the ``CommandData`` interface.

The ``CommandData`` interface is a simple marker interface for commands. In fact, its declaration is only two words
long (Kotlin interfaces do not require a body):

.. container:: codeset

    .. code-block:: kotlin

        interface CommandData

The verify logic
^^^^^^^^^^^^^^^^
Our contract also needs to define the actual contract constraints. For our IOU CorDapp, we won't concern ourselves with
writing valid legal prose to enforce the IOU agreement in court. Instead, we'll focus on implementing ``verify``.

Remember that our goal in writing the ``verify`` function is to write a function that, given a transaction:

* Throws an ``IllegalArgumentException`` if the transaction is considered invalid
* Does **not** throw an exception if the transaction is considered valid

In deciding whether the transaction is valid, the ``verify`` function only has access to the contents of the
transaction:

* ``tx.inputs``, which lists the inputs
* ``tx.outputs``, which lists the outputs
* ``tx.commands``, which lists the commands and their associated signers

Although we won't use them here, the ``verify`` function also has access to the transaction's attachments,
time-windows, notary and hash.

Based on the constraints enumerated above, we'll write a ``verify`` function that rejects a transaction if any of the
following are true:

* The transaction doesn't include a ``Create`` command
* The transaction has inputs
* The transaction doesn't have exactly one output
* The IOU itself is invalid
* The transaction doesn't require the lender's signature

Command constraints
~~~~~~~~~~~~~~~~~~~
Our first constraint is around the transaction's commands. We use Corda's ``requireSingleCommand`` function to test for
the presence of a single ``Create`` command. Here, ``requireSingleCommand`` performing a dual purpose:

* Asserting that there is exactly one ``Create`` command in the transaction
* Extracting the command and returning it

If the ``Create`` command isn't present, or if the transaction has multiple ``Create`` commands, contract
verification will fail.

Transaction constraints
~~~~~~~~~~~~~~~~~~~~~~~
We also want our transaction to have no inputs and only a single output - an issuance transaction.

To impose this and the subsequent constraints, we are using Corda's built-in ``requireThat`` function. ``requireThat``
provides a terse way to write the following:

* If the condition on the right-hand side doesn't evaluate to true...
* ...throw an ``IllegalArgumentException`` with the message on the left-hand side

As before, the act of throwing this exception would cause transaction verification to fail.

IOU constraints
~~~~~~~~~~~~~~~
We want to impose two constraints on the ``IOUState`` itself:

* Its value must be non-negative
* The lender and the borrower cannot be the same entity

We impose these constraints in the same ``requireThat`` block as before.

You can see that we're not restricted to only writing constraints in the ``requireThat`` block. We can also write
other statements - in this case, we're extracting the transaction's single ``IOUState`` and assigning it to a variable.

Signer constraints
~~~~~~~~~~~~~~~~~~
Finally, we require the lender's signature on the transaction. A transaction's required signers is equal to the union
of all the signers listed on the commands. We therefore extract the signers from the ``Create`` command we
retrieved earlier.

Progress so far
---------------
We've now written an ``IOUContract`` constraining the evolution of each ``IOUState`` over time:

* An ``IOUState`` can only be created, not transferred or redeemed
* Creating an ``IOUState`` requires an issuance transaction with no inputs, a single ``IOUState`` output, and a
  ``Create`` command
* The ``IOUState`` created by the issuance transaction must have a non-negative value, and the lender and borrower
  must be different entities

The final step in the creation of our CorDapp will be to write the ``IOUFlow`` that will allow a node to orchestrate
the creation of a new ``IOUState`` on the ledger, while only sharing information on a need-to-know basis.
