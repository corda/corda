Debugging a CorDapp
===================

.. contents::

There are several ways to debug your CorDapp.

Using a ``MockNetwork``
-----------------------

You can attach the `IntelliJ IDEA debugger <https://www.jetbrains.com/help/idea/debugging-code.html>`_ to a
``MockNetwork`` to debug your CorDapp:

* Define your flow tests as per :doc:`api-testing`

    * In your ``MockNetwork``, ensure that ``threadPerNode`` is set to ``false``

* Set your breakpoints
* Run the flow tests using the debugger. When the tests hit a breakpoint, execution will pause

Using the node driver
---------------------

You can also attach the `IntelliJ IDEA debugger <https://www.jetbrains.com/help/idea/debugging-code.html>`_ to nodes
running via the node driver to debug your CorDapp.

With the nodes in-process
^^^^^^^^^^^^^^^^^^^^^^^^^

1. Define a network using the node driver as per :doc:`tutorial-integration-testing`

    * In your ``DriverParameters``, ensure that ``startNodesInProcess`` is set to ``true``

2. Run the driver using the debugger

3. Set your breakpoints

4. Interact with your nodes. When execution hits a breakpoint, execution will pause

    * The nodes' webservers always run in a separate process, and cannot be attached to by the debugger

With remote debugging
^^^^^^^^^^^^^^^^^^^^^

1. Define a network using the node driver as per :doc:`tutorial-integration-testing`

    * In your ``DriverParameters``, ensure that ``startNodesInProcess`` is set to ``false`` and ``isDebug`` is set to
      ``true``

2. Run the driver. The remote debug ports for each node will be automatically generated and printed to the terminal.
   For example:

.. sourcecode:: none

    [INFO ] 11:39:55,471 [driver-pool-thread-0] (DriverDSLImpl.kt:814) internal.DriverDSLImpl.startOutOfProcessNode -
        Starting out-of-process Node PartyA, debug port is 5008, jolokia monitoring port is not enabled {}

3. Attach the debugger to the node of interest on its debug port

4. Set your breakpoints

5. Interact with your node. When execution hits a breakpoint, execution will pause

    * The nodes' webservers always run in a separate process, and cannot be attached to by the debugger

By enabling remote debugging on a node
--------------------------------------

See :ref:`enabling-remote-debugging`.