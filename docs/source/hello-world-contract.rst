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
  without the central bank's involvement)
* A loan CorDapp might not want to allow the creation of negative-valued loans
* An asset-trading CorDapp would not want to allow users to finalise a trade without the agreement of their counterparty

In Corda, we impose constraints on what transactions are allowed using contracts. These contracts are very different
to the smart contracts of other distributed ledger platforms. They do not represent the current state of the ledger.
Instead, like a real-world contract, they simply impose rules on what kinds of agreements are allowed.

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
            fun verify(tx: TransactionForContract)

            // Expresses the contract constraints as legal prose.
            val legalContractReference: SecureHash
        }

A few more Kotlinisms here:

* ``fun`` declares a function
* The syntax ``fun funName(arg1Name: arg1Type): returnType`` declares that ``funName`` takes an argument of type
  ``arg1Type`` and returns a value of type ``returnType``

We can see that ``Contract`` expresses its constraints in two ways:

* In legal prose, through a hash referencing a legal contract that expresses the contract's constraints in legal prose
* In code, through a ``verify`` function that takes a transaction as input, and:
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
  * Its sender and its recipient cannot be the same entity
  * All the participants (i.e. both the sender and the recipient) must sign the transaction

Let's write a contract that enforces these constraints. We'll do this by modifying either TemplateContract.java or
TemplateContract.kt and updating ``TemplateContract`` to define an ``IOUContract``.

Defining IOUContract
--------------------

The Create command
^^^^^^^^^^^^^^^^^^
The first thing our contract needs is a *command*. Commands serve two purposes:

* They indicate the transaction's intent, allowing us to perform different verification given the situation

  * For example, a transaction proposing the creation of an IOU could have to satisfy different constraints to one
    redeeming an IOU

* They allow us to define the required signers for the transaction

  * For example, IOU creation might require signatures from both the sender and the recipient, whereas the transfer
    of an IOU might only require a signature from the IOUs current holder

Let's update the definition of ``TemplateContract`` (in TemplateContract.java/TemplateContract.kt) to define an
``IOUContract`` with a ``Create`` command:

.. container:: codeset

    .. code-block:: kotlin

        package com.template

        import net.corda.core.contracts.*
        import net.corda.core.crypto.SecureHash
        import net.corda.core.crypto.SecureHash.Companion.sha256

        open class IOUContract : Contract {
            // Currently, verify() does no checking at all!
            override fun verify(tx: TransactionForContract) {}

            // Our Create command.
            class Create : CommandData

            // The legal contract reference - we'll leave this as a dummy hash for now.
            override val legalContractReference = SecureHash.sha256("Prose contract.")
        }

    .. code-block:: java

        package com.template;

        import net.corda.core.contracts.CommandData;
        import net.corda.core.contracts.Contract;
        import net.corda.core.crypto.SecureHash;

        public class IOUContract implements Contract {
            @Override
            // Currently, verify() does no checking at all!
            public void verify(TransactionForContract tx) {}

            // Our Create command.
            public static class Create implements CommandData {}

            // The legal contract reference - we'll leave this as a dummy hash for now.
            private final SecureHash legalContractReference = SecureHash.sha256("Prose contract.");
            @Override public final SecureHash getLegalContractReference() { return legalContractReference; }
        }

Aside from renaming ``TemplateContract`` to ``IOUContract``, we've also implemented the ``Create`` command. All
commands must implement the ``CommandData`` interface.

The ``CommandData`` interface is a simple marker interface for commands. In fact, its declaration is only two words
long (in Kotlin, interfaces do not require a body):

.. container:: codeset

    .. code-block:: kotlin

        interface CommandData

The verify logic
^^^^^^^^^^^^^^^^
We now need to define the actual contract constraints. For our IOU CorDapp, we won't concern ourselves with writing
valid legal prose to enforce the IOU agreement in court. Instead, we'll focus on implementing ``verify``.

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

Based on the constraints enumerated above, we'll write a ``verify`` function that rejects transactions on four grounds:

* The transaction doesn't include a ``Create`` command
* The transaction doesn't have no inputs and a single output
* The IOU itself is invalid
* The transaction doesn't require signatures from both the sender and the recipient

Let's work through these constraints one-by-one.

Command constraints
~~~~~~~~~~~~~~~~~~~
To test for the presence of the ``Create`` command, we can use Corda's ``requireSingleCommand`` function:

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()
        }

    .. code-block:: java

        // Additional imports.
        import net.corda.core.contracts.AuthenticatedObject;
        import net.corda.core.contracts.TransactionForContract;
        import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

        ...

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);
        }

Here, ``requireSingleCommand`` performing a dual purpose:

* It's asserting that there is exactly one ``Create`` command in the transaction
* It's extracting the command and returning it

If the ``Create`` command isn't present, or if the transaction has multiple ``Create`` commands, contract
verification will fail.

Transaction constraints
~~~~~~~~~~~~~~~~~~~~~~~
We also wanted our transaction to have no inputs and only a single output. One way to impose this constraint is as
follows:

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
            }
        }

    .. code-block:: java

        // Additional import.
        import static net.corda.core.contracts.ContractsDSL.requireThat;

        ...

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                return null;
            });
        }

Note the use of Corda's built-in ``requireThat`` function. ``requireThat`` provides a terse way to write the following:

* If the condition on the right-hand side doesn't evaluate to true...
* ...throw an ``IllegalArgumentException`` with the message on the left-hand side

As before, the act of throwing this exception would cause transaction verification to fail.

IOU constraints
~~~~~~~~~~~~~~~
We want to impose two constraints on the ``IOUState`` itself:

* Its value must be non-negative
* Its sender and its recipient cannot be the same entity

We can impose these constraints in the same ``requireThat`` block as before:

.. container:: codeset

    .. code-block:: kotlin

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                // IOU-specific constraints.
                final IOUState out = (IOUState) tx.getOutputs().get(0);
                check.using("The IOU's value must be non-negative.",out.getValue() > 0);
                check.using("The sender and the recipient cannot be the same entity.", out.getSender() != out.getRecipient());

                return null;
            });
        }

    .. code-block:: java

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)

                // IOU-specific constraints.
                val out = tx.outputs.single() as IOUState
                "The IOU's value must be non-negative." using (out.value > 0)
                "The sender and the recipient cannot be the same entity." using (out.sender != out.recipient)
            }
        }

You can see that we're not restricted to only writing constraints in the ``requireThat`` block. We can also write
other statements - in this case, we're extracting the transaction's single ``IOUState`` and assigning it to a variable.

Signer constraints
~~~~~~~~~~~~~~~~~~
Our final constraint is that the IOU's sender and recipient must both be required signers on the transaction. A
transaction's required signers is equal to the union of all the signers listed on the commands. We can therefore
extract the signers from the ``Create`` command we retrieved earlier.

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)

                // IOU-specific constraints.
                val out = tx.outputs.single() as IOUState
                "The IOU's value must be non-negative." using (out.value > 0)
                "The sender and the recipient cannot be the same entity." using (out.sender != out.recipient)

                // Constraints on the signers.
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))
            }
        }

    .. code-block:: java

        // Additional imports.
        import com.google.common.collect.ImmutableList;
        import java.security.PublicKey;
        import java.util.List;

        ...

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                // IOU-specific constraints.
                final IOUState out = (IOUState) tx.getOutputs().get(0);
                check.using("The IOU's value must be non-negative.",out.getValue() > 0);
                check.using("The sender and the recipient cannot be the same entity.", out.getSender() != out.getRecipient());

                // Constraints on the signers.
                final List<PublicKey> requiredSigners = ImmutableList.of(
                        out.getSender().getOwningKey(),
                        out.getRecipient().getOwningKey());
                check.using("All of the participants must be signers.", command.getSigners().containsAll(requiredSigners));

                return null;
            });
        }

Checkpoint
----------
We've now defined the full contract logic of our ``IOUContract``. This contract means that transactions involving
``IOUState`` states will have to fulfill strict constraints to become valid ledger updates.

Before we move on, let's go back and modify ``IOUState`` to point to the new ``IOUContract``:

.. container:: codeset

    .. code-block:: kotlin

        class IOUState(val value: Int,
                       val sender: Party,
                       val recipient: Party,
                       override val contract: IOUContract = IOUContract()) : ContractState {

            override val participants get() = listOf(sender, recipient)
        }

    .. code-block:: java

        public class IOUState implements ContractState {
            private final Integer value;
            private final Party sender;
            private final Party recipient;
            private final IOUContract contract;

            public IOUState(Integer value, Party sender, Party recipient, IOUContract contract) {
                this.value = value;
                this.sender = sender;
                this.recipient = recipient;
                this.contract = contract;
            }

            public Integer getValue() {
                return value;
            }

            public Party getSender() {
                return sender;
            }

            public Party getRecipient() {
                return recipient;
            }

            @Override
            public IOUContract getContract() {
                return contract;
            }

            @Override
            public List<AbstractParty> getParticipants() {
                return ImmutableList.of(sender, recipient);
            }
        }

Transaction tests
-----------------
How can we ensure that we've defined our contract constraints correctly?

One option would be to deploy the CorDapp onto a set of nodes, and test it manually. However, this is a relatively
slow process, and would take on the order of minutes to test each change.

Instead, we can test our contract logic using Corda's ``ledgerDSL`` transaction-testing framework. This will allow us
to test our contract without the overhead of spinning up a set of nodes.

Open either test/kotlin/com/template/contract/ContractTests.kt or test/java/com/template/contract/ContractTests.java,
and add the following as our first test:

.. container:: codeset

    .. code-block:: kotlin

        package com.template

        import net.corda.testing.*
        import org.junit.Test

        class IOUTransactionTests {
            @Test
            fun `transaction must include Create command`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        fails()
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        verifies()
                    }
                }
            }
        }

    .. code-block:: java

        package com.template;

        import net.corda.core.identity.Party;
        import org.junit.Test;
        import java.security.PublicKey;
        import static net.corda.testing.CoreTestUtils.*;

        public class IOUTransactionTests {
            static private final Party miniCorp = getMINI_CORP();
            static private final Party megaCorp = getMEGA_CORP();
            static private final PublicKey[] keys = new PublicKey[2];

            {
                keys[0] = getMEGA_CORP_PUBKEY();
                keys[1] = getMINI_CORP_PUBKEY();
            }

            @Test
            public void transactionMustIncludeCreateCommand() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.fails();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.verifies();
                        return null;
                    });
                    return null;
                });
            }
        }

This test uses Corda's built-in ``ledgerDSL`` to:
* Create a fake transaction.
* Add inputs, outputs, commands, etc. (using the DSL's ``output``, ``input`` and ``command`` methods)
* At any point, asserting that the transaction built so far is either contractually valid (by calling ``verifies``) or
  contractually invalid (by calling ``fails``).

In this instance:

* We initially create a transaction with an output but no command
* We assert that this transaction is invalid (since the ``Create`` command is missing)
* We then add the ``Create`` command
* We assert that transaction is now valid

Here is the full set of tests we'll be using to test the ``IOUContract``:

.. container:: codeset

    .. code-block:: kotlin

        class IOUTransactionTests {
            @Test
            fun `transaction must include Create command`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        fails()
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        verifies()
                    }
                }
            }

            @Test
            fun `transaction must have no inputs`() {
                ledger {
                    transaction {
                        input { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("No inputs should be consumed when issuing an IOU.")
                    }
                }
            }

            @Test
            fun `transaction must have one output`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("Only one output state should be created.")
                    }
                }
            }

            @Test
            fun `sender must sign transaction`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("All of the participants must be signers.")
                    }
                }
            }

            @Test
            fun `recipient must sign transaction`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("All of the participants must be signers.")
                    }
                }
            }

            @Test
            fun `sender is not recipient`() {
                ledger {
                    transaction {
                        output { IOUState(1, MEGA_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("The sender and the recipient cannot be the same entity.")
                    }
                }
            }

            @Test
            fun `cannot create negative-value IOUs`() {
                ledger {
                    transaction {
                        output { IOUState(-1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("The IOU's value must be non-negative.")
                    }
                }
            }
        }

    .. code-block:: java

        public class IOUTransactionTests {
            static private final Party miniCorp = getMINI_CORP();
            static private final Party megaCorp = getMEGA_CORP();
            static private final PublicKey[] keys = new PublicKey[2];

            {
                keys[0] = getMEGA_CORP_PUBKEY();
                keys[1] = getMINI_CORP_PUBKEY();
            }

            @Test
            public void transactionMustIncludeCreateCommand() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.fails();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.verifies();
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void transactionMustHaveNoInputs() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.input(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("No inputs should be consumed when issuing an IOU.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void transactionMustHaveOneOutput() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("Only one output state should be created.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void senderMustSignTransaction() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMINI_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("All of the participants must be signers.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void recipientMustSignTransaction() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMEGA_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("All of the participants must be signers.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void senderIsNotRecipient() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, megaCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMEGA_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("The sender and the recipient cannot be the same entity.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void cannotCreateNegativeValueIOUs() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(-1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("The IOU's value must be non-negative.");
                        return null;
                    });
                    return null;
                });
            }
        }

Copy these tests into the ContractTests file, and run them to ensure that the ``IOUState`` and ``IOUContract`` we've
developed up until this point are running ok. All the tests should pass.

Progress so far
---------------
We've now written an ``IOUContract`` constraining the evolution of the individual ``IOUState``s over time:
* An ``IOUState`` can only be created, not transferred or redeemed
* Creating an ``IOUState`` requires an issuance transaction with no inputs, a single ``IOUState`` output, and a
  ``Create`` command
* The ``IOUState`` created by the issuance transaction must have a non-negative value, and its sender and recipient
  must be different entities.

The final step in the creation of our CorDapp will be to write the ``IOUFlow`` that will allow nodes to orchestrate
the creation of new ``IOUState``s on the ledger, while only sharing information on a need-to-know basis.