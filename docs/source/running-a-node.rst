Running a node
==============

Starting your node
------------------

After following the steps in :doc:`deploying-a-node`, you should have deployed your node(s) with any chosen CorDapps
installed. You run each node by navigating to ``<node_dir>`` in a terminal window and running:

.. code-block:: shell

   Windows:   java -jar corda.jar
   UNIX:      ./corda.jar

.. warning:: If your working directory is not ``<node_dir>`` your plugins and configuration will not be used.

The configuration file and workspace paths can be overridden on the command line. For example:

``./corda.jar --config-file=test.conf --base-directory=/opt/r3corda/nodes/test``.

Otherwise the workspace folder for the node is the current working path.

Debugging your node
-------------------

To enable remote debugging of the node, run the following from the terminal window:

``java -Dcapsule.jvm.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" -jar corda.jar``

This command line will start the debugger on port 5005 and pause the process awaiting debugger attachment.

Viewing the persisted state of your node
----------------------------------------

To make examining the persisted contract states of your node or the internal node database tables easier, and providing you are
using the default database configuration used for demos, you should be able to connect to the internal node database over
a JDBC connection at the URL that is output to the logs at node start up.  That URL will be of the form ``jdbc:h2:tcp://<host>:<port>/node``.

The user name and password for the login are as per the node data source configuration.

The name and column layout of the internal node tables is in a state of flux and should not be relied upon to remain static
at the present time, and should certainly be treated as read-only.

.. _CordaPluginRegistry: api/kotlin/corda/net.corda.core.node/-corda-plugin-registry/index.html
.. _PluginServiceHub: api/kotlin/corda/net.corda.core.node/-plugin-service-hub/index.html
.. _ServiceHub: api/kotlin/corda/net.corda.core.node/-service-hub/index.html