.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the state
=================

In Corda, shared facts on the ledger are represented as states. Our first task will be to define a new state type to
represent an IOU.

The ContractState interface
---------------------------
In Corda, any JVM class that implements the ``ContractState`` interface is a valid state. ``ContractState`` is
defined as follows:

.. container:: codeset

    .. code-block:: kotlin

        interface ContractState {
            // The contract controlling transactions involving this state
            val contract: Contract

            // The list of entities considered to have a stake in this state
            val participants: List<AbstractParty>
        }

The first thing you'll probably notice about this interface declaration is that its not written in Java or another
common language. The core Corda platform, including the interface declaration above, is entirely written in Kotlin.

Learning some Kotlin will be very useful for understanding how Corda works internally, and usually only takes an
experienced Java developer a day or so to pick up. However, learning Kotlin isn't essential. Because Kotlin code
compiles down to JVM bytecode, CorDapps written in other JVM languages can interoperate with Corda.

If you do want to dive into Kotlin, there's an official
`getting started guide <https://kotlinlang.org/docs/tutorials/>`_, and a series of
`Kotlin Koans <https://kotlinlang.org/docs/tutorials/koans.html>`_.

If not, here's a quick primer on the Kotlinisms in the declaration of ``ContractState``:

* ``val`` declares a read-only property, similar to Java's ``final`` keyword
* The syntax ``varName: varType`` declares ``varName`` as being of type ``varType``

We can see that the ``ContractState`` interface declares two properties:

* ``contract``: the contract controlling transactions involving this state
* ``participants``: the list of entities that have to approve state changes such as changing the state's notary or
  upgrading the state's contract

Beyond this, our state is free to define any properties, methods, helpers or inner classes it requires to accurately
represent a given class of shared facts on the ledger.

Modelling IOUs
--------------
How should we define the ``IOUState`` representing IOUs on the ledger? Beyond implementing the ``ContractState``
interface, our ``IOUState`` will also need properties to track the relevant features of the IOU:

* The sender of the IOU
* The IOU's recipient
* The value of the IOU

There are many more fields you could include, such as the IOU's currency. We'll abstract them away for now. If
you wish to add them later, its as simple as adding an additional property to your class definition.

Defining IOUState
-----------------
Let's open TemplateState.java (for Java) or TemplateState.kt (for Kotlin) and update ``TemplateState`` to define an
``IOUState``:

.. container:: codeset

    .. code-block:: kotlin

        package com.iou

        import net.corda.core.contracts.ContractState
        import net.corda.core.identity.Party

        class IOUState(val value: Int,
                       val sender: Party,
                       val recipient: Party,
                       // TODO: Once we've defined IOUContract, come back and update this.
                       override val contract: TemplateContract = TemplateContract()) : ContractState {

            override val participants get() = listOf(sender, recipient)
        }

    .. code-block:: java

        package com.iou;

        import com.google.common.collect.ImmutableList;
        import net.corda.core.contracts.ContractState;
        import net.corda.core.identity.AbstractParty;
        import net.corda.core.identity.Party;

        import java.util.List;

        public class IOUState implements ContractState {
            private final Integer value;
            private final Party sender;
            private final Party recipient;
            // TODO: Once we've defined IOUContract, come back and update this.
            private final TemplateContract contract;

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
            // TODO: Once we've defined IOUContract, come back and update this.
            public TemplateContract getContract() {
                return contract;
            }

            @Override
            public List<AbstractParty> getParticipants() {
                return ImmutableList.of(sender, recipient);
            }
        }

We've made the following changes:

* We've renamed ``TemplateState`` to ``IOUState``
* We've added properties for ``value``, ``sender`` and ``recipient`` (along with any getters and setters in Java)

  * ``value`` is just a standard Integer (in Java)/Int (in Kotlin), but ``sender`` and ``recipient`` are of type
    ``Party``. ``Party`` is a built-in Corda type that represents an entity on the network.

* We've overridden ``participants`` to return a list of the ``sender`` and ``recipient``
  * This means that actions such as changing the state's contract or its notary will require approval from both the
    ``sender`` and the ``recipient``

We've left ``IOUState``'s contract as ``TemplateContract`` for now. We'll update this once we've defined the
``IOUContract``.

Progress so far
---------------
We've defined an ``IOUState`` that can be used to represent IOUs as shared facts on the ledger. As we've seen, states in
Corda are simply JVM classes that implement the ``ContractState`` interface. They can have any additional properties and
methods you like.

Next, we'll be writing our ``IOUContract`` to control the evolution of these shared facts over time.