API: Contract Constraints
=========================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

Contract constraints
--------------------

Corda separates verification of states from their definition. Whilst you might have expected the ``ContractState``
interface to define a verify method, or perhaps to do verification logic in the constructor, instead it is primarily
done by a method on a ``Contract`` class. This is because what we're actually checking is the
validity of a *transaction*, which is more than just whether the individual states are internally consistent.
The transition between two valid states may be invalid, if the rules of the application are not being respected.
For instance, two cash states of $100 and $200 may both be internally valid, but replacing the first with the second
isn't allowed unless you're a cash issuer - otherwise you could print money for free.

For a transaction to be valid, the ``verify`` function associated with each state must run successfully. However,
for this to be secure, it is not sufficient to specify the ``verify`` function by name as there may exist multiple
different implementations with the same method signature and enclosing class. This normally will happen as applications
evolve, but could also happen maliciously as anyone can create a JAR with a class of that name.

Contract constraints solve this problem by allowing a state creator to constrain which ``verify`` functions out of
the universe of implementations can be used (i.e. the universe is everything that matches the class name and contract
constraints restrict this universe to a subset). Constraints are satisfied by attachments (JARs). You are not allowed to
attach two JARs that both define the same application due to the *no overlap rule*. This rule specifies that two
attachment JARs may not provide the same file path. If they do, the transaction is considered invalid. Because each
state specifies both a constraint over attachments *and* a Contract class name to use, the specified class must appear
in only one attachment.

.. note:: With the introduction of signature constraints in Corda 4, a new attachments classloader will verify that
   both signed and unsigned versions of an associated contract jar contain identical classes. This allows for automatic
   migration of hash-constrained states (created with pre-Corda 4 unsigned contract jars) to signature constrained states
   when used as outputs in new transactions using signed Corda 4 contract jars.

Recap: A corda transaction transitions input states to output states. Each state is composed of data, the name of the class that verifies the transition(contract), and
the contract constraint. The transaction also contains a list of attachments (normal JARs) from where these classes will be loaded. There must be only one JAR containing each contract.
The contract constraints are responsible to ensure the attachment JARs are following the rules set by the creators of the input states (in a continuous chain to the issue).
This way, we have both valid data and valid code that checks the transition packed into the transaction.

So who picks the attachment to use? It is chosen by the creator of the transaction but has to satisfy the constraints of the input states.
This is because any node doing transaction resolution will actually verify the selected attachment against all constraints,
so the transaction will only be valid if it passes those checks.
For example, when the input state is constrained by the ``HashAttachmentConstraint``, can only attach the JAR with that hash to the transaction.

The transaction creator also gets to pick the constraints used by any output states.
When building a transaction, the default constraint on output states is ``AutomaticPlaceholderConstraint``, which means that corda will select the appropriate constraint.
Unless specified otherwise, attachment constraints will propagate from input to output states. (The rules are described below)
Constraint propagation is also enforced during transaction verification, where for normal transactions (not explicit upgrades, or notary changes),
the constraints of the output states are required to "inherit" the constraint of the input states. ( See below for details)

There are two ways of handling upgrades to a smart contract in Corda:

1. *Implicit:* By allowing multiple implementations of the contract ahead of time, using constraints.
2. *Explicit:* By creating a special *contract upgrade transaction* and getting all participants of a state to sign it using the
   contract upgrade flows.

This article focuses on the first approach. To learn about the second please see :doc:`upgrading-cordapps`.

The advantage of pre-authorising upgrades using constraints is that you don't need the heavyweight process of creating
upgrade transactions for every state on the ledger. The disadvantage is that you place more faith in third parties,
who could potentially change the app in ways you did not expect or agree with. The advantage of using the explicit
upgrade approach is that you can upgrade states regardless of their constraint, including in cases where you didn't
anticipate a need to do so. But it requires everyone to sign, requires everyone to manually authorise the upgrade,
consumes notary and ledger resources, and is just in general more complex.

.. _implicit_constraint_types:

Contract/State Agreement
------------------------

Starting with Corda 4, a ``ContractState`` must explicitly indicate which ``Contract`` it belongs to. When a transaction is
verified, the contract bundled with each state in the transaction must be its "owning" contract, otherwise we cannot guarantee that
the transition of the ``ContractState`` will be verified against the business rules that should apply to it.

There are two mechanisms for indicating ownership. One is to annotate the ``ContractState`` with the ``BelongsToContract`` annotation,
indicating the ``Contract`` class to which it is tied:

.. sourcecode:: java

    @BelongToContract(MyContract.class)
    public class MyState implements ContractState {
        // implementation goes here
    }


.. sourcecode:: kotlin

    @BelongsToContract(MyContract::class)
    data class MyState(val value: Int) : ContractState {
        // implementation goes here
    }


The other is to define the ``ContractState`` class as an inner class of the ``Contract`` class

.. sourcecode:: java

    public class MyContract implements Contract {
    
        public static class MyState implements ContractState {
            // state implementation goes here
        }

        // contract implementation goes here
    }


.. sourcecode:: kotlin

    class MyContract : Contract {
        data class MyState(val value: Int) : ContractState
    }
    

If a ``ContractState``'s owning ``Contract`` cannot be identified by either of these mechanisms, and the ``targetVersion`` of the
CorDapp is 4 or greater, then transaction verification will fail with a ``TransactionRequiredContractUnspecifiedException``. If
the owning ``Contract`` *can* be identified, but the ``ContractState`` has been bundled with a different contract, then
transaction verification will fail with a ``TransactionContractConflictException``.

How constraints work
--------------------

In Corda 4 there are three types of constraint that can be used in production environments: hash, zone whitelist and signature.
For development purposes the ``AlwaysAcceptAttachmentConstraint`` allows any attachment to be selected.

Hash and zone whitelist constraints were available in Corda 3, with hash constraints being used as default.
In Corda 4 the default constraint is the signature constraint if the jar is signed. Otherwise,
the default constraint type is either a zone constraint, if the network parameters in effect when the
transaction is built contain an entry for that contract class, or a hash constraint if not.

**Hash constraints.** The behaviour provided by public blockchain systems like Bitcoin and Ethereum is that once data is placed on the ledger,
the program that controls it is fixed and cannot be changed. There is no support for upgrades at all. This implements a
form of "code is law", assuming you trust the community of that blockchain to not release a new version of the platform
that invalidates or changes the meaning of your program.

This is supported by Corda using a hash constraint. This specifies exactly one hash of a CorDapp JAR that contains the
contract and states any consuming transaction is allowed to use. Once such a state is created, other nodes will only
accept a transaction if it uses that exact JAR file as an attachment. By implication, any bugs in the contract code
or state definitions cannot be fixed except by using an explicit upgrade process via ``ContractUpgradeFlow``.

.. note:: Corda does not support any way to create states that can never be upgraded at all, but the same effect can be
   obtained by using a hash constraint and then simply refusing to agree to any explicit upgrades. Hash
   constraints put you in control by requiring an explicit agreement to any upgrade.

**Zone constraints.** Often a hash constraint will be too restrictive. You do want the ability to upgrade an app,
and you don't mind the upgrade taking effect "just in time" when a transaction happens to be required for other business
reasons. In this case you can use a zone constraint. This specifies that the network parameters of a compatibility zone
(see :doc:`network-map`) is expected to contain a map of class name to hashes of JARs that are allowed to provide that
class. The process for upgrading an app then involves asking the zone operator to add the hash of your new JAR to the
parameters file, and trigger the network parameters upgrade process. This involves each node operator running a shell
command to accept the new parameters file and then restarting the node. Node owners who do not restart their node in
time effectively stop being a part of the network.

**Signature constraints.** These enforce an association between a state and its associated contract JAR which must be
signed by a specified identity, via the regular Java ``jarsigner`` tool. This is the most flexible type
and the smoothest to deploy: no restarts or contract upgrade transactions are needed.
When a CorDapp is build using :ref:`corda-gradle-plugin <cordapp_build_system_signing_cordapp_jar_ref>` the JAR is signed
by Corda development key by default, an external keystore can be configured or signing can be disabled.

.. warning:: CorDapps can only use signature constraints when participating in a Corda network using a minimum platform version of 4.
    An auto downgrade rule applies to signed CorDapps built and tested with Corda 4 but running on a Corda network of a lower version:
    if the associated contract class is whitelisted in the network parameters then zone constraints are applied, otherwise hash constraints are used.

A ``TransactionState`` has a ``constraint`` field that represents that state's attachment constraint. When a party
constructs a ``TransactionState``, or adds a state using ``TransactionBuilder.addOutput(ContractState)`` without
specifying the constraint parameter, a default value (``AutomaticPlaceholderConstraint``) is used. This default will be
automatically resolved to a specific ``HashAttachmentConstraint`` or a ``WhitelistedByZoneAttachmentConstraint``.
This automatic resolution occurs when a ``TransactionBuilder`` is converted to a ``WireTransaction``. This reduces
the boilerplate that would otherwise be involved.

Finally, an ``AlwaysAcceptAttachmentConstraint`` can be used which accepts anything, though this is intended for
testing only, and a warning will be shown if used by a contract.

Please note that the ``AttachmentConstraint`` interface is marked as ``@DoNotImplement``. You are not allowed to write
new constraint types. Only the platform may implement this interface. If you tried, other nodes would not understand
your constraint type and your transaction would not verify.

.. warning:: An AlwaysAccept constraint is effectively the same as disabling security for those states entirely.
   Nothing stops you using this constraint in production, but that degrades Corda to being effectively a form
   of distributed messaging with optional contract logic being useful only to catch mistakes, rather than potentially
   malicious action. If you are deploying an app for which malicious actors aren't in your threat model, using an
   AlwaysAccept constraint might simplify things operationally.

An example below shows how to construct a ``TransactionState`` with an explicitly specified hash constraint from within
a flow:

.. sourcecode:: java

   // Constructing a transaction with a custom hash constraint on a state
   TransactionBuilder tx = new TransactionBuilder();

   Party notaryParty = ... // a notary party

   tx.addInputState(...)
   tx.addInputState(...)

   DummyState contractState = new DummyState();

   TransactionState transactionState = new TransactionState(contractState, DummyContract.Companion.getPROGRAMID(), notaryParty, null, HashAttachmentConstraint(myhash));
   tx.addOutputState(transactionState);
   WireTransaction wtx = tx.toWireTransaction(serviceHub);  // This is where an automatic constraint would be resolved.
   LedgerTransaction ltx = wtx.toLedgerTransaction(serviceHub);
   ltx.verify(); // Verifies both the attachment constraints and contracts

.. _contract_non-downgrade_rule_ref:

Contract attachment non-downgrade rule
--------------------------------------
Contract code is versioned and deployed as an independent JAR that gets imported into a node's database as a contract attachment (either explicitly
uploaded via RPC or automatically loaded from disk). When constructing new transaction it is paramount to ensure
that the contract version of code associated with new output states is the same or newer than the highest version of any existing input states.
This is to prevent the possibility of nodes selecting older, potentially malicious or buggy contract code when creating new states from
existing consumed states.

Transactions contain an attachment for each contract. The version of the output states is the version of this contract attachment.
See :doc:`versioning` for more details on how these versions are set. These can be seen as the version of the code that instantiated and
serialised those classes.

The non-downgrade rule specifies that the version of the code used in the transaction that spends a state needs to be greater than or equal to
the highest version of the input states (i.e. spending_version >= creation_version)

The contract attachment non-downgrade rule is enforced in two locations:

1. Transaction building, upon creation of new output states. During this step, the node also selects the latest available attachment
   (i.e. the contract code with the latest contract class version).
2. Transaction verification, upon resolution of existing transaction chains.

A version number is stored in the manifest information of the enclosing JAR file. This version identifier should be a whole number starting
from 1. This information should be set using the Gradle cordapp plugin, or manually, as described in :doc:`versioning`.

Issues when using the HashAttachmentConstraint
----------------------------------------------

When setting up a new network, it is possible to encounter errors when states are issued with the ``HashAttachmentConstraint``,
but not all nodes have that same version of the CorDapp installed locally.

In this case, flows will fail with a ``ContractConstraintRejection``, and the failed flow will be sent to the flow hospital.
From there it's suspended waiting to be retried on node restart.
This gives the node operator the opportunity to recover from those errors, which in the case of constraint violations means
adding the right cordapp jar to the ``cordapps`` folder.


CorDapps as attachments
-----------------------

CorDapp JARs (see :doc:`cordapp-overview`) that contain classes implementing the ``Contract`` interface are automatically
loaded into the ``AttachmentStorage`` of a node, and made available as ``ContractAttachments``.
They are retrievable by hash using ``AttachmentStorage.openAttachment``.
These JARs can either be installed on the node or fetched from the network using the ``FetchAttachmentsFlow``.

.. note:: The obvious way to write a CorDapp is to put all you states, contracts, flows and support code into a single
   Java module. This will work but it will effectively publish your entire app onto the ledger. That has two problems:
   (1) it is inefficient, and (2) it means changes to your flows or other parts of the app will be seen by the ledger
   as a "new app", which may end up requiring essentially unnecessary upgrade procedures. It's better to split your
   app into multiple modules: one which contains just states, contracts and core data types. And another which contains
   the rest of the app. See :ref:`cordapp-structure`.


Constraints propagation
-----------------------

As was mentioned above, the TransactionBuilder API gives the CorDapp developer or even malicious node owner the possibility
to construct output states with a constraint of his choosing.
Also, as listed above, some constraints are more restrictive then others.
For example, the ``HashAttachmentConstraint`` is the most restrictive, basically reducing the universe of possible attachments
to 1 (see migrating from hash constraints in note below), while the ``AlwaysAcceptAttachmentConstraint`` allows any attachment to be selected.

For the ledger to remain in a consistent state, the expected behavior is for output state to inherit the constraints of input states.
This guarantees that for example, a transaction can't output a state with the ``AlwaysAcceptAttachmentConstraint`` when the
corresponding input state was the ``HashAttachmentConstraint``. Translated, this means that if this rule is enforced, it ensures
that the output state will be spent under similar conditions as it was created.

Before version 4, the constraint propagation logic was expected to be enforced in the contract verify code, as it has access to the entire Transaction.

Starting with version 4 of Corda, the constraint propagation logic has been implemented and enforced directly by the platform,
unless disabled using ``@NoConstraintPropagation`` - which reverts to the previous behavior.

For Contracts that are not annotated with ``@NoConstraintPropagation``, the platform implements a fairly simple constraint transition policy
to ensure security and also allow the possibility to transition to the new ``SignatureAttachmentConstraint``.

.. note:: Migration from hash to signature constraints is automatic if the transaction building node has a signed version of the
   original contract jar (used in previous transactions generating hash constrained states). Additionally, it is a requirement that
   the owner of this signed jar register the java package namespace of the encompassing contract classes with the network parameters.
   See :ref:`package_namespace_ownership` introduced in Corda 4.

During transaction building the ``AutomaticPlaceholderConstraint`` for output states will be resolved and the best contract attachment versions
will be selected based on a variety of factors so that the above holds true.
If it can't find attachments in storage or there are no possible constraints, the Transaction Builder will fail early.

For example:

- In the simple case, if a ``MyContract`` input state is constrained by the ``HashAttachmentConstraint``, then the constraints of all output states of that type will be resolved
  to the ``HashAttachmentConstraint`` with the same hash, and the attachment with that hash will be selected.

- For upgradeable constraints like the ``WhitelistedByZoneAttachmentConstraint``, the output states will inherit the same,
  and the selected attachment will be the latest version installed on the node.

- A more complex case is when for ``MyContract``, one input state is constrained by the ``HashAttachmentConstraint``, while another
  state by the ``WhitelistedByZoneAttachmentConstraint``. To respect the rule from above, if the hash of the ``HashAttachmentConstraint``
  is whitelisted by the network, then the output states will inherit the ``HashAttachmentConstraint``, as it is more restrictive.
  If the hash was not whitelisted, then the builder will fail as it is unable to select a correct constraint.

- The ``SignatureAttachmentConstraint`` is an upgradeable constraint, same as the ``WhitelistedByZoneAttachmentConstraint``.
  By convention we allow states to transition to the ``SignatureAttachmentConstraint`` from the ``WhitelistedByZoneAttachmentConstraint`` as long as the Signatures
  from new constraints are all the jarsigners from the whitelisted attachment. We also allow transitioning of states from ``HashAttachmentConstraint`` to
  ``SignatureAttachmentConstraint`` where both the unsigned and signed versions of the associated contract attachment are loaded in a node, and the java
  package namespace of encompassing contract classes is registered with the network parameters using the same signing key as the signed contract jar.

For Contracts that are annotated with ``@NoConstraintPropagation``, the platform requires that the Transaction Builder specifies
an actual constraint for the output states (the ``AutomaticPlaceholderConstraint`` can't be used) .

Debugging
---------
If an attachment constraint cannot be resolved, a ``MissingContractAttachments`` exception is thrown. There are two
common sources of ``MissingContractAttachments`` exceptions:

Not setting CorDapp packages in tests
*************************************
You are running a test and have not specified the CorDapp packages to scan. See the instructions above.

Wrong fully-qualified contract name
***********************************
You are specifying the fully-qualified name of the contract incorrectly. For example, you've defined ``MyContract`` in
the package ``com.mycompany.myapp.contracts``, but the fully-qualified contract name you pass to the
``TransactionBuilder`` is ``com.mycompany.myapp.MyContract`` (instead of ``com.mycompany.myapp.contracts.MyContract``).