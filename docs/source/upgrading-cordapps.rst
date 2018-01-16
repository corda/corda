Upgrading a CorDapp (outside of platform version upgrades)
==========================================================

.. note:: This document only concerns the upgrading of CorDapps and not the Corda platform itself (wire format, node
   database schemas, etc.).

.. contents::

CorDapp versioning
------------------
The Corda platform does not mandate a version number on a per-CorDapp basis. Different elements of a CorDapp are
allowed to evolve separately:

* States
* Contracts
* Services
* Flows
* Utilities and library functions
* All, or a subset, of the above

Sometimes, however, a change to one element will require changes to other elements. For example, changing a shared data
structure may require flow changes that are not backwards-compatible.

Areas of consideration
----------------------
This document will consider the following types of versioning:

* Flow versioning
* State and contract versioning
* State and state schema versioning
* Serialisation of custom types

Flow versioning
---------------
Any flow that initiates other flows must be annotated with the ``@InitiatingFlow`` annotation, which is defined as:

.. sourcecode:: kotlin

   annotation class InitiatingFlow(val version: Int = 1)

The ``version`` property, which defaults to 1, specifies the flow's version. This integer value should be incremented
whenever there is a release of a flow which has changes that are not backwards-compatible. A non-backwards compatible
change is one that changes the interface of the flow.

Currently, CorDapp developers have to explicitly write logic to handle these flow version numbers. In the future,
however, the platform will use prescribed rules for handling versions.

What defines the interface of a flow?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The flow interface is defined by the sequence of ``send`` and ``receive`` calls between an ``InitiatingFlow`` and an
``InitiatedBy`` flow, including the types of the data sent and received. We can picture a flow's interface as follows:

.. image:: resources/flow-interface.png
   :scale: 50%
   :align: center

In the diagram above, the ``InitiatingFlow``:

* Sends an ``Int``
* Receives a ``String``
* Sends a ``String``
* Receives a ``CustomType``

The ``InitiatedBy`` flow does the opposite:

* Receives an ``Int``
* Sends a ``String``
* Receives a ``String``
* Sends a ``CustomType``

As long as both the ``IntiatingFlow`` and the ``InitiatedBy`` flows conform to the sequence of actions, the flows can
be implemented in any way you see fit (including adding proprietary business logic that is not shared with other
parties).

What constitutes a non-backwards compatible flow change?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A flow can become backwards-incompatible in two main ways:

* The sequence of ``send`` and ``receive`` calls changes:

  * A ``send`` or ``receive`` is added or removed from either the ``InitatingFlow`` or ``InitiatedBy`` flow
  * The sequence of ``send`` and ``receive`` calls changes

* The types of the ``send`` and ``receive`` calls changes

What happens when running flows with incompatible versions?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Pairs of ``InitiatingFlow`` flows and ``InitiatedBy`` flows that have incompatible interfaces are likely to exhibit the
following behaviour:

* The flows hang indefinitely and never terminate, usually because a flow expects a response which is never sent from
  the other side
* One of the flow ends with an exception: "Expected Type X but Received Type Y", because the ``send`` or ``receive``
  types are incorrect
* One of the flows ends with an exception: "Counterparty flow terminated early on the other side", because one flow
  sends some data to another flow, but the latter flow has already ended

How do I upgrade my flows?
~~~~~~~~~~~~~~~~~~~~~~~~~~
For flag-day upgrades, the process is simple.

Assumptions
^^^^^^^^^^^

* All nodes in the business network can be shut down for a period of time
* All nodes retire the old flows and adopt the new flows at the same time

Process
^^^^^^^

1. Update the flow and test the changes. Increment the flow version number in the ``InitiatingFlow`` annotation
2. Ensure that all versions of the existing flow have finished running and there are no pending ``SchedulableFlows`` on
   any of the nodes on the business network
3. Shut down all the nodes
4. Replace the existing CorDapp JAR with the CorDapp JAR containing the new flow
5. Start the nodes

From this point onwards, all the nodes will be using the updated flows.

In situations where some nodes may still be using previous versions of a flow, the updated flows need to be
backwards-compatible.

How do I ensure flow backwards-compatibility?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The ``InitiatingFlow`` version number is included in the flow session handshake and exposed to both parties via the
``FlowLogic.getFlowContext`` method. This method takes a ``Party`` and returns a ``FlowContext`` object which describes
the flow running on the other side. In particular, it has a ``flowVersion`` property which can be used to
programmatically evolve flows across versions. For example:

.. sourcecode:: kotlin

    @Suspendable
    override fun call() {
        val otherFlowVersion = otherSession.getCounterpartyFlowInfo().flowVersion
        val receivedString = if (otherFlowVersion == 1) {
            receive<Int>(otherParty).unwrap { it.toString() }
        } else {
            receive<String>(otherParty).unwrap { it }
        }
    }

This code shows a flow that in its first version expected to receive an Int, but in subsequent versions was modified to
expect a String. This flow is still able to communicate with parties that are running the older CorDapp containing
the older flow.

How do I deal with interface changes to inlined subflows?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Here is an example of an in-lined subflow:

.. sourcecode:: kotlin

    @StartableByRPC
    @InitiatingFlow
    class FlowA(val recipient: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(FlowB(recipient))
        }
    }

    @InitiatedBy(FlowA::class)
    class FlowC(val otherSession: FlowSession) : FlowLogic() {
        // Omitted.
    }

    // Note: No annotations. This is used as an inlined subflow.
    class FlowB(val recipient: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val message = "I'm an inlined subflow, so I inherit the @InitiatingFlow's session ID and type."
            initiateFlow(recipient).send(message)
        }
    }

Inlined subflows are treated as being the flow that invoked them when initiating a new flow session with a counterparty.
Suppose flow ``A`` calls inlined subflow B, which, in turn, initiates a session with a counterparty. The ``FlowLogic``
type used by the counterparty to determine which counter-flow to invoke is determined by ``A``, and not by ``B``. This
means that the response logic for the inlined flow must be implemented explicitly in the ``InitiatedBy`` flow. This can
be done either by calling a matching inlined counter-flow, or by implementing the other side explicitly in the
initiated parent flow. Inlined subflows also inherit the session IDs of their parent flow.

As such, an interface change to an inlined subflow must be considered a change to the parent flow interfaces.

An example of an inlined subflow is ``CollectSignaturesFlow``. It has a response flow called ``SignTransactionFlow``
that isn’t annotated with ``InitiatedBy``. This is because both of these flows are inlined. How these flows speak to
one another is defined by the parent flows that call ``CollectSignaturesFlow`` and ``SignTransactionFlow``.

In code, inlined subflows appear as regular ``FlowLogic`` instances without either an ``InitiatingFlow`` or an
``InitiatedBy`` annotation.

Inlined flows are not versioned, as they inherit the version of their parent ``InitiatingFlow`` or ``InitiatedBy``
flow.

Are there any other considerations?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Suspended flows
^^^^^^^^^^^^^^^
Currently, serialised flow state machines persisted in the node's database cannot be updated. All flows must finish
before the updated flow classes are added to the node's plugins folder.

Flows that don't create sessions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Flows which are not an ``InitiatingFlow`` or ``InitiatedBy`` flow, or inlined subflows that are not called from an
``InitiatingFlow`` or ``InitiatedBy`` flow, can be updated without consideration of backwards-compatibility. Flows of
this type include utility flows for querying the vault and flows for reaching out to external systems.

Contract and state versioning
-----------------------------
Contracts and states can be upgraded if and only if all of the state's participants agree to the proposed upgrade. The
following combinations of upgrades are possible:

* A contract is upgraded while the state definition remains the same
* A state is upgraded while the contract stays the same
* The state and the contract are updated simultaneously

The procedure for updating a state or a contract using a flag-day approach is quite simple:

* Update and test the state or contract
* Stop all the nodes on the business network
* Produce a new CorDapp JAR file and distribute it to all the relevant parties
* Start all nodes on the network
* Run the contract upgrade authorisation flow for each state that requires updating on every node
* For each state, one node should run the contract upgrade initiation flow

Update Process
~~~~~~~~~~~~~~

Writing the new state and contract definitions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Start by updating the contract and/or state definitions. There are no restrictions on how states are updated. However,
upgraded contracts must implement the ``UpgradedContract`` interface. This interface is defined as:

.. sourcecode:: kotlin

    interface UpgradedContract<in OldState : ContractState, out NewState : ContractState> : Contract {
        val legacyContract: ContractClassName
        fun upgrade(state: OldState): NewState
    }

The ``upgrade`` method describes how the old state type is upgraded to the new state type. When the state isn't being
upgraded, the same state type can be used for both the old and new state type parameters.

Authorising the upgrade
^^^^^^^^^^^^^^^^^^^^^^^
Once the new states and contracts are on the classpath for all the relevant nodes, the next step is for all nodes to
run the ``ContractUpgradeFlow.Authorise`` flow. This flow takes a ``StateAndRef`` of the state to update as well as a
reference to the new contract, which must implement the ``UpgradedContract`` interface.

At any point, a node administrator may de-authorise a contract upgrade by running the
``ContractUpgradeFlow.Deauthorise`` flow.

Performing the upgrade
^^^^^^^^^^^^^^^^^^^^^^
Once all nodes have performed the authorisation process, a participant must be chosen to initiate the upgrade via the
``ContractUpgradeFlow.Initiate`` flow for each state object. This flow has the following signature:

.. sourcecode:: kotlin

    class Initiate<OldState : ContractState, out NewState : ContractState>(
        originalState: StateAndRef<OldState>,
        newContractClass: Class<out UpgradedContract<OldState, NewState>>
    ) : AbstractStateReplacementFlow.Instigator<OldState, NewState, Class<out UpgradedContract<OldState, NewState>>>(originalState, newContractClass)

This flow sub-classes ``AbstractStateReplacementFlow``, which can be used to upgrade state objects that do not need a
contract upgrade.

One the flow ends successfully, all the participants of the old state object should have the upgraded state object
which references the new contract code.

Points to note
~~~~~~~~~~~~~~

Capabilities of the contract upgrade flows
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Despite its name, the ``ContractUpgradeFlow`` also handles the update of state object definitions
* The state can completely change as part of an upgrade! For example, it is possible to transmute a ``Cat`` state into
  a ``Dog`` state, provided that all participants in the ``Cat`` state agree to the change
* Equally, the state doesn't have to change at all
* If a node has not yet run the contract upgrade authorisation flow, they will not be able to upgrade the contract
  and/or state objects
* Upgrade authorisations can subsequently be deauthorised
* Upgrades do not have to happen immediately. For a period, the two parties can use the old states and contracts
  side-by-side
* State schema changes are handled separately

Writing new states and contracts
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* If a property is removed from a state, any references to it must be removed from the contract code. Otherwise, you
  will not be able to compile your contract code. It is generally not advisable to remove properties from states. Mark
  them as deprecated instead
* When adding properties to a state, consider how the new properties will affect transaction validation involving this
  state. If the contract is not updated to add constraints over the new properties, they will be able to take on any
  value
* Updated state objects can use the old contract code as long as there is no requirement to update it

Dealing with old contract code JAR files
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
* Currently, all parties **must** keep the old state and contract definitions on their node's classpath as they will
  always be required to verify transactions involving previous versions of the state using previous versions of the
  contract

  * This will change when the contract code as an attachment feature has been fully implemented.

Permissioning
^^^^^^^^^^^^^
* Only node administrators are able to run the contract upgrade authorisation and deauthorisation flows

Logistics
^^^^^^^^^
* All nodes need to run the contract upgrade authorisation flow
* Only one node should run the contract upgrade initiation flow. If multiple nodes run it for the same ``StateRef``, a
  double-spend will occur for all but the first completed upgrade
* The supplied upgrade flows upgrade one state object at a time

Serialisation
-------------

Currently, the serialisation format for everything except flow checkpoints (which uses a Kryo-based format) is based
upon AMQP 1.0, a self-describing and controllable serialisation format. AMQP is desirable because it allows us to have
a schema describing what has been serialized alongside the data itself. This assists with versioning and deserialising
long-ago archived data, among other things.

Writing classes
~~~~~~~~~~~~~~~
Although not strictly related to versioning, AMQP serialisation dictates that we must write our classes in a particular way:

* Your class must have a constructor that takes all the properties that you wish to record in the serialized form. This
  is required in order for the serialization framework to reconstruct an instance of your class
* If more than one constructor is provided, the serialization framework needs to know which one to use. The
  ``@ConstructorForDeserialization`` annotation can be used to indicate the chosen constructor. For a Kotlin class
  without the ``@ConstructorForDeserialization`` annotation, the primary constructor is selected
* The class must be compiled with parameter names in the .class file. This is the default in Kotlin but must be turned
  on in Java (using the ``-parameters`` command line option to ``javac``)
* Your class must provide a Java Bean getter for each of the properties in the constructor, with a matching name. For
  example, if a class has the constructor parameter ``foo``, there must be a getter called ``getFoo()``. If ``foo`` is
  a boolean, the getter may optionally be called ``isFoo()``. This is why the class must be compiled with parameter
  names turned on
* The class must be annotated with ``@CordaSerializable``
* The declared types of constructor arguments/getters must be supported, and where generics are used the generic
  parameter must be a supported type, an open wildcard (*), or a bounded wildcard which is currently widened to an open
  wildcard
* Any superclass must adhere to the same rules, but can be abstract
* Object graph cycles are not supported, so an object cannot refer to itself, directly or indirectly

Writing enums
~~~~~~~~~~~~~
Elements cannot be added to enums in a new version of the code. Hence, enums are only a good fit for genuinely static
data that will never change (e.g. days of the week). A ``Buy`` or ``Sell`` flag is another. However, something like
``Trade Type`` or ``Currency Code`` will likely change. For those, it is preferable to choose another representation,
such as a string.

State schemas
-------------
By default, all state objects are serialised to the database as a string of bytes and referenced by their ``StateRef``.
However, it is also possible to define custom schemas for serialising particular properties or combinations of
properties, so that they can be queried from a source other than the Corda Vault. This is done by implementing the
``QueryableState`` interface and creating a custom object relational mapper for the state. See :doc:`api-persistence`
for details.

For backwards compatible changes such as adding columns, the procedure for upgrading a state schema is to extend the
existing object relational mapper. For example, we can update:

.. sourcecode:: kotlin

    object ObligationSchemaV1 : MappedSchema(Obligation::class.java, 1, listOf(ObligationEntity::class.java)) {
        @Entity @Table(name = "obligations")
        class ObligationEntity(obligation: Obligation) : PersistentState() {
            @Column var currency: String = obligation.amount.token.toString()
            @Column var amount: Long = obligation.amount.quantity
            @Column @Lob var lender: ByteArray = obligation.lender.owningKey.encoded
            @Column @Lob var borrower: ByteArray = obligation.borrower.owningKey.encoded
            @Column var linear_id: String = obligation.linearId.id.toString()
        }
    }

To:

.. sourcecode:: kotlin

    object ObligationSchemaV1 : MappedSchema(Obligation::class.java, 1, listOf(ObligationEntity::class.java)) {
        @Entity @Table(name = "obligations")
        class ObligationEntity(obligation: Obligation) : PersistentState() {
            @Column var currency: String = obligation.amount.token.toString()
            @Column var amount: Long = obligation.amount.quantity
            @Column @Lob var lender: ByteArray = obligation.lender.owningKey.encoded
            @Column @Lob var borrower: ByteArray = obligation.borrower.owningKey.encoded
            @Column var linear_id: String = obligation.linearId.id.toString()
            @Column var defaulted: Bool = obligation.amount.inDefault               // NEW COLUNM!
        }
    }

Thus adding a new column with a default value.

To make a non-backwards compatible change, the ``ContractUpgradeFlow`` or ``AbstractStateReplacementFlow`` must be
used, as changes to the state are required. To make a backwards-incompatible change such as deleting a column (e.g.
because a property was removed from a state object), the procedure is to define another object relational mapper, then
add it to the ``supportedSchemas`` property of your ``QueryableState``, like so:

.. sourcecode:: kotlin

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ExampleSchemaV1, ExampleSchemaV2)

Then, in ``generateMappedObject``, add support for the new schema:

.. sourcecode:: kotlin

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is DummyLinearStateSchemaV1 -> // Omitted.
            is DummyLinearStateSchemaV2 -> // Omitted.
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

With this approach, whenever the state object is stored in the vault, a representation of it will be stored in two
separate database tables where possible - one for each supported schema.