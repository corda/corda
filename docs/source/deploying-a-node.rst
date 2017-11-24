Deploying a node
================

.. contents::

.. note:: You should already have generated your node(s) with their CorDapps installed by following the instructions in
   :doc:`generating-a-node`.

Installing and running Corda as a system service (on Linux)
-----------------------------------------------------------
We recommend creating system processes to run a node and its webserver, as follows:

1. Move the node folder to ``/opt/corda`` and change its ownership to the user you want to use to run Corda:

   * ``mkdir /opt/corda; chown user:user /opt/corda``

2. Create and save the ``corda.service`` and ``corda-webserver.service`` files below

3. Change the ``User=`` line in both service files to the username which will run the Corda service (this should not be
   root)

4. Copy both service files to the ``/etc/systemd/system/`` directory

You can now start a node and its webserver by running the following ``systemctl`` commands:

   * ``systemctl daemon-reload``
   * ``systemctl corda start``
   * ``systemctl corda-webserver start``

The ``corda.service`` file:

.. code-block:: shell

   [Unit]
   Description=Corda Node
   Requires=network.target

   [Service]
   Type=simple
   User=username
   WorkingDirectory=/opt/corda
   ExecStart=/usr/bin/java -Xmx2048m -jar /opt/corda/corda.jar
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target

The ``corda-webserver.service`` file:

.. code-block:: shell

   [Unit]
   Description=Simple webserver for Corda
   Requires=network.target

   [Service]
   Type=simple
   User=username
   WorkingDirectory=/opt/corda
   ExecStart=/usr/bin/java -jar /opt/corda/corda-webserver.jar
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target