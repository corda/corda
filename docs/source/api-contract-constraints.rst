.. highlight:: kotlin
.. role:: kotlin(code)
    :language: kotlin
.. raw:: html


   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Contract Constraints
=========================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-contracts`.

.. contents::

Reasons for Contract Constraints
--------------------------------

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

.. _implicit_vs_explicit_upgrades:

Implicit vs Explicit Contract upgrades
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Constraints are not the only way to manage upgrades to transactions. There are two ways of handling
upgrades to a smart contract in Corda:

* **Implicit**: By pre-authorising multiple implementations of the contract ahead of time, using constraints.
* **Explicit**: By creating a special *contract upgrade transaction* and getting all participants of a state to sign it using the
  contract upgrade flows.

The advantage of pre-authorising upgrades using constraints is that you don't need the heavyweight process of creating
upgrade transactions for every state on the ledger. The disadvantage is that you place more faith in third parties,
who could potentially change the app in ways you did not expect or agree with. The advantage of using the explicit
upgrade approach is that you can upgrade states regardless of their constraint, including in cases where you didn't
anticipate a need to do so. But it requires everyone to sign, manually authorise the upgrade,
consumes notary and ledger resources, and is just in general more complex.

This article focuses on the first approach. To learn about the second please see :doc:`upgrading-cordapps`.

.. _implicit_constraint_types:

Types of Contract Constraints
-----------------------------

Corda supports several types of constraints to cover a wide set of client requirements:

* **Hash constraint**: Exactly one version of the app can be used with this state. This prevents the app from being upgraded in the future while still
  making use of the state created with the original version.
* **Compatibility zone whitelisted (or CZ whitelisted) constraint**: The compatibility zone operator lists the hashes of the versions that can be used with a contract class name.
* **Signature constraint**: Any version of the app signed by the given ``CompositeKey`` can be used. This allows app issuers to express the
  complex social and business relationships that arise around code ownership. For example, a Signature Constraint allows a new version of an
  app to be produced and applied to an existing state as long as it has been signed by the same key(s) as the original version.
* **Always accept constraint**: Any version of the app can be used. This is insecure but convenient for testing.

.. _signature_constraints:

Signature Constraints
---------------------

The best kind of constraint to use is the **Signature Constraint**. If you sign your application it will be used automatically.
We recommend signature constraints because they let you express complex social and business relationships while allowing
smooth migration of existing data to new versions of your application.

Signature constraints can specify flexible threshold policies, but if you use the automatic support then a state will
require the attached app to be signed by every key that the first attachment was signed by. Thus if the app that was used
to issue the states was signed by Alice and Bob, every transaction must use an attachment signed by Alice and Bob. Doing so allows the
app to be upgraded and changed while still remaining valid for use with the previously issued states.

More complex policies can be expressed through Signature Constraints if required. Allowing policies where only a number of the possible
signers must sign the new version of an app that is interacting with previously issued states. Accepting different versions of apps in this
way makes it possible for multiple versions to be valid across the network as long as the majority (or possibly a minority) agree with the
logic provided by the apps.

Hash and zone whitelist constraints are left over from earlier Corda versions before Signature Constraints were
implemented. They make it harder to upgrade applications than when using signature constraints, so they're best avoided.

Further information into the design of Signature Constraints can be found in its :doc:`design document <design/data-model-upgrades/signature-constraints>`.

Signing CorDapps for use with Signature Constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Expanding on the previous section, for an app to use Signature Constraints, it must be signed by a ``CompositeKey`` or a simpler ``PublicKey``.
The signers of the app can consist of a single organisation or multiple organisations. Once the app has been signed, it can be distributed
across the nodes that intend to use it.

Each transaction received by a node will then verify that the apps attached to it have the correct signers as specified by its
Signature Constraints. This ensures that the version of each app is acceptable to the transaction's input states.

If a node receives a transaction that uses a contract attachment that it doesn't trust, but there is an attachment present on the node with
the same contract classes and same signatures, then the node will execute that contract's code as if it were trusted. This means that nodes
are no longer required to have every version of a CorDapp uploaded to them in order to verify transactions running older version of a CorDapp.
Instead, it is sufficient to have any version of the CorDapp contract installed.

For third party dependencies attached to the transaction, the rule is slightly different. In this case, the attachment will be trusted by the
node provided there is another trusted attachment in the node's attachment store that has been signed with the same keys.

More information on how to sign an app directly from Gradle can be found in the
:ref:`CorDapp Jar signing <cordapp_build_system_signing_cordapp_jar_ref>` section of the documentation.

Using Signature Constraints in transactions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If the app is signed, Signature Constraints will be used by default (in most situations) by the ``TransactionBuilder`` when adding output states.
This is expanded upon in :ref:`contract_constraints_in_transactions`.

.. note:: Signature Constraints are used by default except when a new transaction contains an input state with a Hash Constraint. In this
          situation the Hash Constraint is used.

App versioning with Signature Constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Signed apps require a version number to be provided, see :doc:`versioning`.

Hash Constraints
----------------

Issues when using the HashAttachmentConstraint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When setting up a new network, it is possible to encounter errors when states are issued with the ``HashAttachmentConstraint``,
but not all nodes have that same version of the CorDapp installed locally.

In this case, flows will fail with a ``ContractConstraintRejection``, and are sent to the flow hospital.
From there, they are suspended, waiting to be retried on node restart.
This gives the node operator the opportunity to recover from those errors, which in the case of constraint violations means
adding the right cordapp jar to the ``cordapps`` folder.

.. _relax_hash_constraints_checking_ref:

Hash constrained states in private networks
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Where private networks started life using CorDapps with hash constrained states, we have introduced a mechanism to relax the checking of
these hash constrained states when upgrading to signed CorDapps using signature constraints.

The Java system property ``-Dnet.corda.node.disableHashConstraints="true"`` may be set to relax the hash constraint checking behaviour. For
this to work, every participant of the network must set the property to the same value. Therefore, this mode should only be used upon
"out of band" agreement by all participants in a network.

.. warning:: This flag should remain enabled until every hash constrained state is exited from the ledger.

.. _contract_state_agreement:

Contract/State Agreement
------------------------

Starting with Corda 4, a ``ContractState`` must explicitly indicate which ``Contract`` it belongs to. When a transaction is
verified, the contract bundled with each state in the transaction must be its "owning" contract, otherwise we cannot guarantee that
the transition of the ``ContractState`` will be verified against the business rules that should apply to it.

There are two mechanisms for indicating ownership. One is to annotate the ``ContractState`` with the ``BelongsToContract`` annotation,
indicating the ``Contract`` class to which it is tied:

.. container:: codeset

    .. sourcecode:: java

        @BelongsToContract(MyContract.class)
        public class MyState implements ContractState {
            // implementation goes here
        }


    .. sourcecode:: kotlin

        @BelongsToContract(MyContract::class)
        data class MyState(val value: Int) : ContractState {
            // implementation goes here
        }

The other is to define the ``ContractState`` class as an inner class of the ``Contract`` class:


.. container:: codeset

    .. sourcecode:: java

        public class MyContract implements Contract {

            public static class MyState implements ContractState {
                // state implementation goes here
            }

            // contract implementation goes here
        }


    .. sourcecode:: kotlin

        class MyContract : Contract {

            data class MyState(val value: Int) : ContractState {
                // state implementation goes here
            }

            // contract implementation goes here
        }

If a ``ContractState``'s owning ``Contract`` cannot be identified by either of these mechanisms, and the ``targetVersion`` of the
CorDapp is 4 or greater, then transaction verification will fail with a ``TransactionRequiredContractUnspecifiedException``. If
the owning ``Contract`` *can* be identified, but the ``ContractState`` has been bundled with a different contract, then
transaction verification will fail with a ``TransactionContractConflictException``.

.. _contract_constraints_in_transactions:

Using Contract Constraints in Transactions
------------------------------------------

The app version used by a transaction is defined by its attachments. The JAR containing the state and contract classes, and optionally its
dependencies, are all attached to the transaction. Nodes will download this JAR from other nodes if they haven't seen it before,
so it can be used for verification.

The ``TransactionBuilder`` will manage the details of constraints for you, by selecting both constraints
and attachments to ensure they line up correctly. Therefore you only need to have a basic understanding of this topic unless you are
doing something sophisticated.

By default the ``TransactionBuilder`` will use :ref:`signature_constraints` for any issuance transactions if the app attached to it is
signed.

To manually define the Contract Constraint of an output state, see the example below:

.. container:: codeset

    .. sourcecode:: java

        TransactionBuilder transaction() {
            TransactionBuilder transaction = new TransactionBuilder(notary());
            // Signature Constraint used if app is signed
            transaction.addOutputState(state);
            // Explicitly using a Signature Constraint
            transaction.addOutputState(state, CONTRACT_ID, new SignatureAttachmentConstraint(getOurIdentity().getOwningKey()));
            // Explicitly using a Hash Constraint
            transaction.addOutputState(state, CONTRACT_ID, new HashAttachmentConstraint(getServiceHub().getCordappProvider().getContractAttachmentID(CONTRACT_ID)));
            // Explicitly using a Whitelisted by Zone Constraint
            transaction.addOutputState(state, CONTRACT_ID, WhitelistedByZoneAttachmentConstraint.INSTANCE);
            // Explicitly using an Always Accept Constraint
            transaction.addOutputState(state, CONTRACT_ID, AlwaysAcceptAttachmentConstraint.INSTANCE);

            // other transaction stuff
            return transaction;
        }


    .. sourcecode:: kotlin

        private fun transaction(): TransactionBuilder {
            val transaction = TransactionBuilder(notary())
            // Signature Constraint used if app is signed
            transaction.addOutputState(state)
            // Explicitly using a Signature Constraint
            transaction.addOutputState(state, constraint = SignatureAttachmentConstraint(ourIdentity.owningKey))
            // Explicitly using a Hash Constraint
            transaction.addOutputState(state, constraint = HashAttachmentConstraint(serviceHub.cordappProvider.getContractAttachmentID(CONTRACT_ID)!!))
            // Explicitly using a Whitelisted by Zone Constraint
            transaction.addOutputState(state, constraint = WhitelistedByZoneAttachmentConstraint)
            // Explicitly using an Always Accept Constraint
            transaction.addOutputState(state, constraint = AlwaysAcceptAttachmentConstraint)

            // other transaction stuff
            return transaction
        }

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

.. _constraints_propagation:

Constraints propagation
-----------------------

As was mentioned above, the ``TransactionBuilder`` API gives the CorDapp developer or even malicious node owner the possibility
to construct output states with a constraint of their choosing.

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

Example of creation of two Cordapps with Finance App Flows and Finance App Contracts:

.. container:: codeset

   .. sourcecode:: kotlin

        Driver.driver(DriverParameters(
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.finance.schemas"),
                TestCordapp.findCordapp("net.corda.finance.flows")
            )
        ) {
            // Your test code goes here
        })

   .. sourcecode:: java

        Driver.driver(
            new DriverParameters()
                .withCordappsForAllNodes(
                    Arrays.asList(
                        TestCordapp.findCordapp("net.corda.finance.schemas"),
                        TestCordapp.findCordapp("net.corda.finance.flows")
                    )
                ),
            dsl -> {
              // Your test code goes here
            }
        );

Starting a node missing CorDapp(s)
**********************************

When running the Corda node ensure all CordDapp JARs are placed in ``cordapps`` directory of each node.
By default Gradle Cordform task ``deployNodes`` copies all JARs if CorDapps to deploy are specified.
See :doc:`generating-a-node` for detailed instructions.

Wrong fully-qualified contract name
***********************************

You are specifying the fully-qualified name of the contract incorrectly. For example, you've defined ``MyContract`` in
the package ``com.mycompany.myapp.contracts``, but the fully-qualified contract name you pass to the
``TransactionBuilder`` is ``com.mycompany.myapp.MyContract`` (instead of ``com.mycompany.myapp.contracts.MyContract``).
