.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Testing
============

.. contents::

Flow testing
------------

MockNetwork
^^^^^^^^^^^

* Create a mock network using MockNetwork
* The mock nodes run in-memory, and optionally in-thread, allowing you to quickly test your CorDapp without standing up a full network

Key params
~~~~~~~~~~

* A set of CorDapp packages to scan (HOW DOES THIS ACTUALLY WORK?)
* `threadPerNode`, which determines whether all the nodes run on a single thread. This is faster and allows all the nodes to be debugged, but may not correspond exactly to the behviour of real nodes

Creating nodes
^^^^^^^^^^^^^^

* Create individual mock nodes using MockNetwork.createPartyNodes

Running the network
^^^^^^^^^^^^^^^^^^^

* Use `MockNetwork.runNetwork` to simulate the sending of messages around the network
* The default value of -1 sends messages around the network until there are no more messages to send

Running flows
^^^^^^^^^^^^^

* Use the `StartedNodeServices.startFlow` extension method to call a flow and get a future representing the flow's output
* You can retrieve the result of the flow from the future for testing
* Ensure you run `MockNetwork.runNetwork` before resolving the future, so that the messages sent as part of the flow are processed

Examples
^^^^^^^^

Checking the nodes' tx storages
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(see https://github.com/corda/cordapp-example/blob/release-V2/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt#L86)

Checking the nodes' vaults
~~~~~~~~~~~~~~~~~~~~~~~~~~

(see https://github.com/corda/cordapp-example/blob/release-V2/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt#L107)