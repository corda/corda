Upgrading CorDapps
==================

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
Any flow that initiaties other flows must be annotated with the ``@InitiatingFlow`` annotation, which is defined as:

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

1. Update the flow and test the changes. Uncrement the flow version number in the ``InitiatingFlow`` annotation
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

How do I deal with interface changes to inlined sub flows?
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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

    // Note: No annotations. This is used as an inlined sub-flow.
    class FlowB(val recipient: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val message = "I'm an inlined sub-flow, so I inherit the @InitiatingFlow's session ID and type."
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
``InitiatingFlow`` or ``InitiatedBy`` flow , can be updated without consideration of backwards-compatibility. Flows of
this type include utility flows for querying the vault and flows for reaching out to external systems.