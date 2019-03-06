Upgrading your node to Corda 4
==============================

Corda releases strive to be backwards compatible, so upgrading a node is fairly straightforward and should not require changes to
applications. It consists of the following steps:

1. Drain the node.
2. Make a backup of your node directories and/or database.
3. Update database
4. Replace the ``corda.jar`` file with the new version.
5. Start up the node. This step may incur a delay whilst any needed database migrations are applied.
6. Undrain it to re-enable processing of new inbound flows.

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

Step 3. Update database
-----------------------

The database schema of Corda 3.X require an update to Corda 4.
Follow the steps described in :doc:`node-operations-database-schema-setup` page.
That page describes the procedure for both the initial database setup
and an existing database update as the steps are essentially the same.

Depending on you deployment process the database update can be performed as:

* Automatic update by a Corda node upon startup

  Follow the steps described :ref:`here <db_setup_auto-upgrade_ref>`.

* Automatic update by a database administrator using Corda Database Migration Tool

  The ``corda-database-migration-tool-4.0.jar`` tool can be accesses from
  `Corda Artifactory site <https://ci-artifactory.corda.r3cev.com/artifactory/webapp/#/artifacts/browse/simple/General/corda/net/corda/corda-node>`_.
  Ensure to use the same version of the tool as the Corda node version to be deployed.
  Follow the steps described :ref:`here <db-setup-database-management-direct-execution_ref>`.
  This release does require data rows migration, therefore ``myLegalName`` needs to be set and the tool needs to have access to node's CorDapps:

  - Ensure ``myLegalName`` is set correctly in a configuration file provided for Database Migration Tool.

  - If you are not reusing a node base directory, copy any CorDapps from the node being upgraded to *cordapps* subdirectory accessed by the tool.

* DDL script execution and automatic data update by a database administrator using Corda Database Migration Tool

  Follow the steps described :ref:`here <db-setup-database-management-ddl-execution_ref>`.
  This upgrade procedure is a mix of running the DDL script for schema update
  and running Database Migration Tool for non-schema alteration changes.
  All steps of this procedure except the first one needed to be run:
  *Extract DDL script using Database Migration Tool*,
  *Apply DDL scripts on a database*, *Apply remaining data upgrades on a database.*
  Especially the last step is required because Corda 4 contains new columns/tables
  which needed to be populated based on your existing data,
  and these migration can't be expressed in DDL script.
  Data rows migration requires the ``myLegalName`` option to be set and the tool needs to have access to node's CorDapps:

   - Ensure ``myLegalName`` is set correctly in a configuration file provided for Database Migration Tool.

   - If you are not reusing a node base directory, copy any CorDapps from a node being upgraded to *cordapps* subdirectory accessed by the tool.

  The ``corda-database-migration-tool-4.0.jar`` tool can be accesses from
  `Corda Artifactory site <https://ci-artifactory.corda.r3cev.com/artifactory/webapp/#/artifacts/browse/simple/General/corda/net/corda/corda-node>`_.
  Ensure to use the same version of the tool as the Corda node version to be deployed.

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