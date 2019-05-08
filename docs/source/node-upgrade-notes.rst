Upgrading your node to Corda 4
==============================

Corda releases strive to be backwards compatible, so upgrading a node is fairly straightforward and should not require changes to
applications. It consists of the following steps:

1. Drain the node.
2. Make a backup of your node directories and/or database.
3. Replace the ``corda.jar`` file with the new version.
4. Start up the node. This step may incur a delay whilst any needed database migrations are applied.
5. Undrain it to re-enable processing of new inbound flows.

The protocol is designed to tolerate node outages, so during the upgrade process peers on the network will wait for your node to come back.

Step 1. Drain the node
----------------------

Before a node or application on it can be upgraded, the node must be put in :ref:`draining-mode`. This brings the currently running
:doc:`key-concepts-flows` to a smooth halt such that existing work is finished and new work is queuing up rather than being processed.

Draining flows is a key task for node administrators to perform. It exists to simplify applications by ensuring apps don't have to be
able to migrate workflows from any arbitrary point to other arbitrary points, a task that would rapidly become infeasible as workflow
and protocol complexity increases.

To drain the node, run the ``gracefulShutdown`` command. This will wait for the node to drain and then shut down the node when the drain
is complete.

.. warning:: The length of time a node takes to drain depends on both how your applications are designed, and whether any apps are currently
   talking to network peers that are offline or slow to respond. It is thus hard to give guidance on how long a drain should take, but in
   an environment with well written apps and in which your counterparties are online, drains may need only a few seconds.

Step 2. Make a backup of your node directories and/or database
--------------------------------------------------------------

It's always a good idea to make a backup of your data before upgrading any server. This will make it easy to roll back if there's a problem.
You can simply make a copy of the node's data directory to enable this. If you use an external non-H2 database please consult your database
user guide to learn how to make backups.

We provide some :ref:`backup recommendations <backup-recommendations>` if you'd like more detail.

Step 3. Upgrade the node database to Corda 3.2 or later
-------------------------------------------------------

Ensure your node is running Corda 3.2 or later.
Corda 3.2 required a database table name change and column type change in PostgreSQL.
These changes need to be applied to the database before upgrading to Corda 4.0.
Refer to `Corda 3.2 release notes <https://docs.corda.net/releases/release-V3.4/upgrade-notes.html#v3-1-to-v3-2>`_
for further information.

Step 4. Replace ``corda.jar`` with the new version
--------------------------------------------------

Download the latest version of Corda from `our Artifactory site <https://ci-artifactory.corda.r3cev.com/artifactory/webapp/#/artifacts/browse/simple/General/corda/net/corda/corda-node>`_.
Make sure it's available on your path, and that you've read the :doc:`release-notes`, in particular to discover what version of Java this
node requires.

.. important:: Corda 4 requires Java |java_version| or any higher Java 8 patchlevel. Java 9+ is not currently supported.

Step 5. Start up the node
-------------------------

Start the node in the usual manner you have selected. The node will perform any automatic data migrations required, which may take some
time. If the migration process is interrupted it can be continued simply by starting the node again, without harm.

Step 6. Undrain the node
------------------------

You may now do any checks that you wish to perform, read the logs, and so on. When you are ready, use this command at the shell:

``run setFlowsDrainingModeEnabled enabled: false``

Your upgrade is complete.
