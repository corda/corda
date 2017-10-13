Introduction
============

By this point, :doc:`your dev environment should be set up <getting-set-up>`, you've run
:doc:`your first CorDapp <tutorial-cordapp>`, and you're familiar with Corda's :doc:`key concepts <key-concepts>`. What
comes next?

If you're a developer, the next step is to write your own CorDapp. Each CorDapp takes the form of a JAR that is
installed on one or more Corda nodes, and gives them the ability to conduct some new process - anything from
issuing a debt instrument to making a restaurant booking.

Our use-case
------------
Our CorDapp will seek to model IOUs on ledger. An IOU – short for “I Owe yoU” – records the fact that one person owes
another a given amount of money. We can imagine that this is potentially sensitive information that we'd only want to
communicate on a need-to-know basis. This is one of the areas where Corda excels - allowing a small set of parties to
agree on a fact without needing to share this fact with everyone else on the network, as you do with most other
blockchain platforms.

To serve any useful function, a CorDapp needs three core elements:

* **One or more states** – the shared facts that will be agreed upon and stored on the ledger
* **One or more contracts** – the rules governing how these states can evolve over time
* **One or more flows** – the step-by-step process for carrying out a ledger update

Our IOU CorDapp is no exception. It will have the following elements:

State
^^^^^
The states will be IOUStates, with each instance representing a single IOU. We can visualize an IOUState as follows:

  .. image:: resources/tutorial-state.png
     :scale: 25%
     :align: center

Contract
^^^^^^^^
Our contract will be the IOUContract, imposing rules on the evolution of IOUs over time:

    * Only the creation of new IOUs will be allowed
    * Transferring existing IOUs or paying off an IOU with cash will not be allowed

However, we can easily extend our CorDapp to handle additional use-cases later on.

Flow
^^^^
Our flow will be the IOUFlow. It will allow a node to orchestrate the creation of a new IOU on the ledger, via the
following steps:

  .. image:: resources/simple-tutorial-flow.png
     :scale: 25%
     :align: center

In traditional distributed ledger systems, where all data is broadcast to every network participant, you don’t even
think about this step – you simply package up your ledger update and send it out into the world. But in Corda, where
privacy is a core focus, flows are used to carefully control who sees what during the process of agreeing a
ledger update.

Progress so far
---------------
We've sketched out a simple CorDapp that will allow nodes to confidentially agree the creation of new IOUs.

Next, we'll be taking a look at the template project we'll be using as a base for our work.