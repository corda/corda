Running a node
==============

Starting your node
------------------
After following the steps in :doc:`deploying-a-node`, you should have deployed your node(s) with any chosen CorDapps
already installed. You run each node by navigating to ``<node_dir>`` in a terminal window and running:

.. code-block:: shell

   java -jar corda.jar

.. warning:: If your working directory is not ``<node_dir>`` your cordapps and configuration will not be used.

The configuration file and workspace paths can be overridden on the command line. For example:

``./corda.jar --config-file=test.conf --base-directory=/opt/r3corda/nodes/test``.

Otherwise the workspace folder for the node is the current working path.

Debugging your node
-------------------
To enable remote debugging of the node, run the following from the terminal window:

``java -Dcapsule.jvm.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" -jar corda.jar``

This command line will start the debugger on port 5005 and pause the process awaiting debugger attachment.
