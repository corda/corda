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

If you're a developer, the next step is to write your own CorDapp. CorDapps are applications that are installed on one or
more Corda nodes, and that allow the node's operator to instruct their node to perform some new process - anything from
issuing a debt instrument to making a restaurant booking.

Our use-case
------------
We will write a CorDapp to model IOUs on the blockchain. Each IOU – short for “I O(we) (yo)U” – will record the fact that one node owes
another node a certain amount. This simple CorDapp will showcase several key benefits of Corda as a blockchain platform:

* **Privacy** - Since IOUs represent sensitive information, we will be taking advantage of Corda's ability to only share
  ledger updates with other nodes on a need-to-know basis, instead of using a gossip protocol to share this information with every node on
  the network as you would with a traditional blockchain platform

* **Well-known identities** - Each Corda node has a well-known identity on the network. This allows us to write code in terms of real
  identities, rather than anonymous public keys

* **Re-use of existing, proven technologies** - We will be writing our CorDapp using standard Java. It will run on a Corda node, which is
  simply a Java process and runs on a regular Java machine (e.g. on your local machine or in the cloud). The nodes will store their data in
  a standard SQL database

CorDapps usually define at least three things:

* **States** - the (possibly shared) facts that are written to the ledger
* **Flows** - the procedures for carrying out specific ledger updates
* **Contracts** - the constraints governing how states of a given type can evolve over time

Our IOU CorDapp is no exception. It will define the following components:

The IOUState
^^^^^^^^^^^^
Our state will be the ``IOUState``, representing an IOU. It will contain the IOU's value, its lender and its borrower. We can visualize
``IOUState`` as follows:

  .. image:: resources/tutorial-state.png
     :scale: 25%
     :align: center

The IOUFlow
^^^^^^^^^^^
Our flow will be the ``IOUFlow``. This flow will completely automate the process of issuing a new IOU onto a ledger. It has the following
steps:

  .. image:: resources/simple-tutorial-flow.png
     :scale: 25%
     :align: center

The IOUContract
^^^^^^^^^^^^^^^
For this tutorial, we will use the default ``TemplateContract``. We will update it to create a fully-fledged ``IOUContract`` in the next
tutorial.

Progress so far
---------------
We've designed a simple CorDapp that will allow nodes to agree new IOUs on the blockchain.

Next, we'll take a look at the template project we'll be using as the basis for our CorDapp.
