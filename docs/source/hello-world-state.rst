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
            // The list of entities considered to have a stake in this state.
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

We can see that the ``ContractState`` interface has a single field, ``participants``. ``participants`` is a list of
the entities for which this state is relevant.

Beyond this, our state is free to define any fields, methods, helpers or inner classes it requires to accurately
represent a given class of shared facts on the ledger.

``ContractState`` also has several child interfaces that you may wish to implement depending on your state, such as
``LinearState`` and ``OwnableState``. See :doc:`api-states` for more information.

Modelling IOUs
--------------
How should we define the ``IOUState`` representing IOUs on the ledger? Beyond implementing the ``ContractState``
interface, our ``IOUState`` will also need properties to track the relevant features of the IOU:

* The lender of the IOU
* The borrower of the IOU
* The value of the IOU

There are many more fields you could include, such as the IOU's currency. We'll abstract them away for now. If
you wish to add them later, its as simple as adding an additional property to your class definition.

Defining IOUState
-----------------
Let's open ``TemplateState.java`` (for Java) or ``App.kt`` (for Kotlin) and update ``TemplateState`` to
define an ``IOUState``:

.. container:: codeset

    .. code-block:: kotlin

        class IOUState(val value: Int,
                       val lender: Party,
                       val borrower: Party) : ContractState {
            override val participants get() = listOf(lender, borrower)
        }

    .. code-block:: java

        package com.template.state;

        import com.google.common.collect.ImmutableList;
        import net.corda.core.contracts.ContractState;
        import net.corda.core.identity.AbstractParty;
        import net.corda.core.identity.Party;

        import java.util.List;

        public class IOUState implements ContractState {
            private final int value;
            private final Party lender;
            private final Party borrower;

            public IOUState(int value, Party lender, Party borrower) {
                this.value = value;
                this.lender = lender;
                this.borrower = borrower;
            }

            public int getValue() {
                return value;
            }

            public Party getLender() {
                return lender;
            }

            public Party getBorrower() {
                return borrower;
            }

            @Override
            public List<AbstractParty> getParticipants() {
                return ImmutableList.of(lender, borrower);
            }
        }

If you're following along in Java, you'll also need to rename ``TemplateState.java`` to ``IOUState.java``.

We've made the following changes:

* We've renamed ``TemplateState`` to ``IOUState``
* We've added properties for ``value``, ``lender`` and ``borrower`` (along with any getters and setters in Java):

  * ``value`` is just a standard int (in Java)/Int (in Kotlin)
  * ``lender`` and ``borrower`` are of type ``Party``. ``Party`` is a built-in Corda type that represents an entity on
    the network.

* We've overridden ``participants`` to return a list of the ``lender`` and ``borrower``

  * Actions such as changing a state's contract or notary will require approval from all the ``participants``

Progress so far
---------------
We've defined an ``IOUState`` that can be used to represent IOUs as shared facts on the ledger. As we've seen, states in
Corda are simply JVM classes that implement the ``ContractState`` interface. They can have any additional properties and
methods you like.

Next, we'll be writing our ``IOUContract`` to control the evolution of these shared facts over time.
