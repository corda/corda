Running nodes locally
=====================

.. contents::

.. note:: You should already have generated your node(s) with their CorDapps installed by following the instructions in
   :doc:`generating-a-node`.

There are several ways to run a Corda node locally for testing purposes.

Starting a Corda node using DemoBench
-------------------------------------
See the instructions in :doc:`demobench`.

.. _starting-an-individual-corda-node:

Starting a Corda node from the command line
-------------------------------------------
Run a node by opening a terminal window in the node's folder and running:

.. code-block:: shell

   java -jar corda.jar

By default, the node will look for a configuration file called ``node.conf`` and a CorDapps folder called ``cordapps``
in the current working directory. You can override the configuration file and workspace paths on the command line (e.g.
``./corda.jar --config-file=test.conf --base-directory=/opt/corda/nodes/test``).

You can increase the amount of Java heap memory available to the node using the ``-Xmx`` command line argument. For
example, the following would run the node with a heap size of 2048MB:

.. code-block:: shell

   java -Xmx2048m -jar corda.jar

You should do this if you receive an ``OutOfMemoryError`` exception when interacting with the node.

Optionally run the node's webserver as well by opening a terminal window in the node's folder and running:

.. code-block:: shell

   java -jar corda-webserver.jar

.. warning:: The node webserver is for testing purposes only and will be removed soon.

Enabling remote debugging
~~~~~~~~~~~~~~~~~~~~~~~~~
To enable remote debugging of the node, run the following from the terminal window:

``java -Dcapsule.jvm.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" -jar corda.jar``

This command line will start the debugger on port 5005 and pause the process awaiting debugger attachment.

Starting a node with JMX monitoring enabled
-------------------------------------------
To enable export of JMX metrics over HTTP via `Jolokia <https://jolokia.org/>`_, run the following from the terminal window:

``java -Dcapsule.jvm.args="-javaagent:drivers/jolokia-jvm-1.3.7-agent.jar=port=7005" -jar corda.jar``

This command line will start the node with JMX metrics accessible via HTTP on port 7005.

See :ref:`Monitoring your node <jolokia_ref>` for further details.

Starting all nodes at once from the command line (native)
---------------------------------------------------------
If you created your nodes using ``deployNodes``, a ``runnodes`` shell script (or batch file on Windows) will have been
generated to allow you to quickly start up all nodes and their webservers. ``runnodes`` should only be used for testing
purposes.

Start the nodes with ``runnodes`` by running the following command from the root of the project:

* Linux/macOS: ``build/nodes/runnodes``
* Windows: ``call build\nodes\runnodes.bat``

.. warning:: On macOS, do not click/change focus until all the node terminal windows have opened, or some processes may
   fail to start.

If you receive an ``OutOfMemoryError`` exception when interacting with the nodes, you need to increase the amount of
Java heap memory available to them, which you can do when running them individually. See
:ref:`starting-an-individual-corda-node`.

Starting all nodes at once from the command line (docker-compose)
-----------------------------------------------------------------
If you created your nodes using ``Dockerform``, the ``docker-compose.yml`` file and corresponding ``Dockerfile`` for
nodes has been created and configured appropriately. Navigate to ``build/nodes`` directory and run ``docker-compose up``
command. This will startup nodes inside new, internal network.
After the nodes are started up, you can use ``docker ps`` command to see how the ports are mapped.

.. warning:: You need both ``Docker`` and ``docker-compose`` installed and enabled to use this method. Docker CE
   (Community Edition) is enough. Please refer to `Docker CE documentation <https://www.docker.com/community-edition>`_
   and `Docker Compose documentation <https://docs.docker.com/compose/install/>`_ for installation instructions for all
   major operating systems.
