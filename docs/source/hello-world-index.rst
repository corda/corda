Hello, World!
=============

.. toctree::
    :maxdepth: 1

    hello-world-template
    hello-world-state
    hello-world-flow
    hello-world-running

By this point, :doc:`your dev environment should be set up <getting-set-up>`, you've run
:doc:`your first CorDapp <tutorial-cordapp>`, and you're familiar with Corda's :doc:`key concepts <key-concepts>`. What
comes next?

If you're a developer, the next step is to write your own CorDapp. Each CorDapp takes the form of a plugin that is
installed on one or more Corda nodes, and gives them the ability to conduct some new process - anything from
issuing a debt instrument to making a restaurant booking.

Our use-case
------------
Our CorDapp will model IOUs on ledger. An IOU – short for “_I_ _O_we yo_U_” – records the fact that one person owes
another a given amount of money. This is potentially sensitive information that we'd only want to communicate on a
need-to-know basis - ideally just between the lender and the borrower. Fortunately, this is one of the areas where
Corda excels. One of Corda's most important features is that it allows a small set of parties to agree on a shared fact
without needing to share this fact with everyone else on the network, as is the norm with blockchain platforms.

To serve any useful function, a CorDapp will usually need at least two core elements:

* **States** – the shared facts that nodes reach consensus over and are then stored on the ledger
* **Flows** – the step-by-step process for carrying out a specific ledger update

Our IOU CorDapp is no exception. It will define both a state and a flow:

State
^^^^^
The state type we will define will be the ``IOUState``. It will capture the value of the IOU, as well as who the lender
and the borrower are. We can visualize an ``IOUState`` as follows:

  .. image:: resources/tutorial-state.png
     :scale: 25%
     :align: center

Flow
^^^^
Our flow will be the ``IOUFlow``. This flow will completely automate the process of creating a new IOU on the ledger.
It has the following steps:

  .. image:: resources/simple-tutorial-flow.png
     :scale: 25%
     :align: center

In traditional distributed ledger systems, where all data is broadcast to every network participant, you don’t need to
think about this step – you simply package up your ledger update and send it to everyone else on the network. But in
Corda, which places a premium on privacy, flows allow us to carefully control who sees what during the process of
agreeing a ledger update.

Progress so far
---------------
We've sketched out a simple CorDapp that will allow nodes to confidentially agree the creation of new IOUs.

Next, we'll be taking a look at the template project we'll be using as a basis for our CorDapp.