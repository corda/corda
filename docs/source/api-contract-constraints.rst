.. highlight:: kotlin

API: Contract Constraints
=========================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

*Contract constraints* solve two problems faced by any decentralised ledger that supports evolution of data and code:

1. Controlling and agreeing upon upgrades
2. Preventing attacks

Upgrades and security are intimately related because if an attacker can "upgrade" your data to a version of an app that gives them
a back door, they would be able to do things like print money or edit states in any way they want. That's why it's important for
participants of a state to agree on what kind of upgrades will be allowed.

Every state on the ledger contains the fully qualified class name of a ``Contract`` implementation, and also a *constraint*.
This constraint specifies which versions of an application can be used to provide the named class, when the transaction is built.
New versions released after a transaction is signed and finalised won't affect prior transactions because the old code is attached
to it.

There are several types of constraint:

1. Hash constraint: exactly one version of the app can be used with this state.
2. Compatibility zone whitelisted (or CZ whitelisted) constraint: the compatibility zone operator lists the hashes of the versions that can be used with this contract class name.
3. Signature constraint: any version of the app signed by the given ``CompositeKey`` can be used.
4. Always accept constraint: any app can be used at all. This is insecure but convenient for testing.

The actual app version used is defined by the attachments on a transaction that consumes a state: the JAR containing the state and contract classes, and optionally
its dependencies, are all attached to the transaction. Other nodes will download these JARs from a node if they haven't seen them before,
so they can be used for verification. The ``TransactionBuilder`` will manage the details of constraints for you, by selecting both constraints
and attachments to ensure they line up correctly. Therefore you only need to have a basic understanding of this topic unless you are
doing something sophisticated.

The best kind of constraint to use is the **signature constraint**. If you sign your application it will be used automatically.
We recommend signature constraints because they let you smoothly migrate existing data to new versions of your application.
Hash and zone whitelist constraints are left over from earlier Corda versions before signature constraints were
implemented. They make it harder to upgrade applications than when using signature constraints, so they're best avoided.
Signature constraints can specify flexible threshold policies, but if you use the automatic support then a state will
require the attached app to be signed by every key that the first attachment was signed by. Thus if the app that was used
to issue the states was signed by Alice and Bob, every transaction must use an attachment signed by Alice and Bob.

**Constraint propagation.** Constraints are picked when a state is created for the first time in an issuance transaction. Once created,
the constraint used by equivalent output states (i.e. output states that use the same contract class name) must match the
input state, so it can't be changed and you can't combine states with incompatible constraints together in the same transaction.

.. _implicit_vs_explicit_upgrades:

**Implicit vs explicit.** Constraints are not the only way to manage upgrades to transactions. There are two ways of handling
upgrades to a smart contract in Corda:

1. *Implicit:* By pre-authorising multiple implementations of the contract ahead of time, using constraints.
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

.. _contract_downgrade_rule_ref:

App versioning with signature constraints
-----------------------------------------

Signed apps require a version number to be provided, see :doc:`versioning`. You can't import two different
JARs that claim to be the same version, provide the same contract classes and which are both signed. At runtime
the node will throw a ``DuplicateContractClassException`` exception if this condition is violated.

Issues when using the HashAttachmentConstraint
----------------------------------------------

When setting up a new network, it is possible to encounter errors when states are issued with the ``HashAttachmentConstraint``,
but not all nodes have that same version of the CorDapp installed locally.

In this case, flows will fail with a ``ContractConstraintRejection``, and the failed flow will be sent to the :doc:`node-flow-hospital`.
From there it's suspended waiting to be retried on node restart.
This gives the node operator the opportunity to recover from those errors, which in the case of constraint violations means
adding the right cordapp jar to the ``cordapps`` folder.

.. _relax_hash_constraints_checking_ref:

Hash constrained states in private networks
-------------------------------------------

Where private networks started life using CorDapps with hash constrained states, we have introduced a mechanism to relax the checking of
these hash constrained states when upgrading to signed CorDapps using signature constraints.

The Java system property ``-Dnet.corda.node.disableHashConstraints="true"`` may be set to relax the hash constraint checking behaviour.

This mode should only be used upon "out of band" agreement by all participants in a network.

Please also beware that this flag should remain enabled until every hash constrained state is exited from the ledger.

CorDapps as attachments
-----------------------

CorDapp JARs (see :doc:`cordapp-overview`) that contain classes implementing the ``Contract`` interface are automatically
loaded into the ``AttachmentStorage`` of a node, and made available as ``ContractAttachments``.

They are retrievable by hash using ``AttachmentStorage.openAttachment``. These JARs can either be installed on the
node or will be automatically fetched over the network when receiving a transaction.

.. warning:: The obvious way to write a CorDapp is to put all you states, contracts, flows and support code into a single
   Java module. This will work but it will effectively publish your entire app onto the ledger. That has two problems:
   (1) it is inefficient, and (2) it means changes to your flows or other parts of the app will be seen by the ledger
   as a "new app", which may end up requiring essentially unnecessary upgrade procedures. It's better to split your
   app into multiple modules: one which contains just states, contracts and core data types. And another which contains
   the rest of the app. See :ref:`cordapp-structure`.


Constraints propagation
-----------------------

As was mentioned above, the ``TransactionBuilder`` API gives the CorDapp developer or even malicious node owner the possibility
to construct output states with a constraint of his choosing.

For the ledger to remain in a consistent state, the expected behavior is for output state to inherit the constraints of input states.
This guarantees that for example, a transaction can't output a state with the ``AlwaysAcceptAttachmentConstraint`` when the
corresponding input state was the ``SignatureAttachmentConstraint``. Translated, this means that if this rule is enforced, it ensures
that the output state will be spent under similar conditions as it was created.

Before version 4, the constraint propagation logic was expected to be enforced in the contract verify code, as it has access to the entire Transaction.

Starting with version 4 of Corda the constraint propagation logic has been implemented and enforced directly by the platform,
unless disabled by putting ``@NoConstraintPropagation`` on the ``Contract`` class which reverts to the previous behavior of expecting
apps to do this.

For contracts that are not annotated with ``@NoConstraintPropagation``, the platform implements a fairly simple constraint transition policy
to ensure security and also allow the possibility to transition to the new ``SignatureAttachmentConstraint``.

During transaction building the ``AutomaticPlaceholderConstraint`` for output states will be resolved and the best contract attachment versions
will be selected based on a variety of factors so that the above holds true. If it can't find attachments in storage or there are no
possible constraints, the ``TransactionBuilder`` will throw an exception.

Constraints migration to Corda 4
--------------------------------

Please read :doc:`cordapp-constraint-migration` to understand how to consume and evolve pre-Corda 4 issued hash or CZ whitelisted constrained states
using a Corda 4 signed CorDapp (using signature constraints).

Debugging
---------
If an attachment constraint cannot be resolved, a ``MissingContractAttachments`` exception is thrown. There are three common sources of
``MissingContractAttachments`` exceptions:

Not setting CorDapp packages in tests
*************************************

You are running a test and have not specified the CorDapp packages to scan.
When using ``MockNetwork`` ensure you have provided a package containing the contract class in ``MockNetworkParameters``. See :doc:`api-testing`.

Similarly package names need to be provided when testing using ``DriverDSl``. ``DriverParameters`` has a property ``cordappsForAllNodes`` (Kotlin)
or method ``withCordappsForAllNodes`` in Java. Pass the collection of ``TestCordapp`` created by utility method ``TestCordapp.findCordapp(String)``.

Example of creation of two Cordapps with Finance App Flows and Finance App Contracts in Kotlin:

   .. sourcecode:: kotlin

        Driver.driver(DriverParameters(cordappsForAllNodes = listOf(TestCordapp.findCordapp("net.corda.finance.schemas"),
                TestCordapp.findCordapp("net.corda.finance.flows"))) {
            // Your test code goes here
        })

The same example in Java:

   .. sourcecode:: java

        Driver.driver(new DriverParameters()
                .withCordappsForAllNodes(Arrays.asList(TestCordapp.findCordapp("net.corda.finance.schemas"),
                TestCordapp.findCordapp("net.corda.finance.flows"))), dsl -> {
            // Your test code goes here
        });


Starting a node missing CorDapp(s)
*********************************

When running the Corda node ensure all CordDapp JARs are placed in ``cordapps`` directory of each node.
By default Gradle Cordform task ``deployNodes`` copies all JARs if CorDapps to deploy are specified.
See :doc:`generating-a-node` for detailed instructions.

Wrong fully-qualified contract name
***********************************

You are specifying the fully-qualified name of the contract incorrectly. For example, you've defined ``MyContract`` in
the package ``com.mycompany.myapp.contracts``, but the fully-qualified contract name you pass to the
``TransactionBuilder`` is ``com.mycompany.myapp.MyContract`` (instead of ``com.mycompany.myapp.contracts.MyContract``).