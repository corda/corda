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

If you're a developer, the next step is to write your own CorDapp. CorDapps are plugins that are installed on one or
more Corda nodes, and give the nodes' owners the ability to make their node conduct some new process - anything from
issuing a debt instrument to making a restaurant booking.

Our use-case
------------
Our CorDapp will model IOUs on-ledger. An IOU – short for “I O(we) (yo)U” – records the fact that one person owes
another person a given amount of money. Clearly this is sensitive information that we'd only want to communicate on
a need-to-know basis between the lender and the borrower. Fortunately, this is one of the areas where Corda excels.
Corda makes it easy to allow a small set of parties to agree on a shared fact without needing to share this fact with
everyone else on the network, as is the norm in blockchain platforms.

To serve any useful function, our CorDapp will need at least two things:

* **States**, the shared facts that Corda nodes reach consensus over and are then stored on the ledger
* **Flows**, which encapsulate the procedure for carrying out a specific ledger update

Our IOU CorDapp is no exception. It will define both a state and a flow:

The IOUState
^^^^^^^^^^^^
Our state will be the ``IOUState``. It will store the value of the IOU, as well as the identities of the lender and the
borrower. We can visualize ``IOUState`` as follows:

  .. image:: resources/tutorial-state.png
     :scale: 25%
     :align: center

The IOUFlow
^^^^^^^^^^^
Our flow will be the ``IOUFlow``. This flow will completely automate the process of issuing a new IOU onto a ledger. It
is composed of the following steps:

  .. image:: resources/simple-tutorial-flow.png
     :scale: 25%
     :align: center

In traditional distributed ledger systems, where all data is broadcast to every network participant, you don’t need to
think about data flows – you simply package up your ledger update and send it to everyone else on the network. But in
Corda, where privacy is a core focus, flows allow us to carefully control who sees what during the process of
agreeing a ledger update.

Progress so far
---------------
We've sketched out a simple CorDapp that will allow nodes to confidentially issue new IOUs onto a ledger.

Next, we'll be taking a look at the template project we'll be using as the basis for our CorDapp.