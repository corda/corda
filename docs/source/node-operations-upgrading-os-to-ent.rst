Upgrading a Corda (open source) Node to Corda Enterprise
========================================================

Upgrading the version of Corda on a node
----------------------------------------

CorDapps, contracts and states written for Corda 3.x and Corda 4.x are compatible with |release|, so upgrading
existing open source Corda nodes should be a simple case of updating the Corda JAR. For developer information on recompiling
CorDapps against Corda Enterprise, see :doc:`upgrade-notes`.

Upgrading the Node
~~~~~~~~~~~~~~~~~~
See :doc:`node-upgrade-notes` for general instructions on upgrading your node.

.. _node-operations-upgrading-cordapps:

Database upgrades
~~~~~~~~~~~~~~~~~

When upgrading an existing node from Corda 3.x or Corda 4.x to Corda Enterprise, the node operator has the option of using one of the enterprise
database engines that are supported (see :doc:`node-database`).
We highly recommend moving away from the default H2 database when setting up a production node.

Corda Enterprise uses :ref:`Liquibase <liquibase_ref>` for database schema management.
See :doc:`database-management` for more info.

.. _migrate-4-to-enterprise-database:

Migrating existing data from a Corda 4.0 H2 database to a |release| supported database
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: Switching from an H2 development database to a commercial production database requires migrating across both schemas and data.
   Specialist third party tools are available on the market to facilitate this activity. Please contact R3 for advice on specialised tooling
   we have validated to work well for this upgrade exercise.

The procedure for migrating from H2 to a commercial database is as follows:

  1. :ref:`Create a database user with schema permissions <db_setup_step_1_ref>`

  2. Configure a Corda node to connect to the new database following :ref:`Corda node configuration changes <db_setup_step_3_ref>`
     and specific options for :ref:`you database vendor <db_setup_vendors_ref>`

  3. Create database schema following one of 3 possible procedures,
     depending on the database user permissions and preferred database upgrade policy

      * :ref:`Database schema management upon node startup <db_setup_auto-upgrade_ref>`

      * :ref:`Database Management Tool applies schema changes directly <db-setup-database-management-direct-execution_ref>`

      * :ref:`Database Management Tool generates DDL script to be run manually <db-setup-database-management-ddl-execution_ref>`

        Execute the first 3 steps: *Essential preparation before the first installation*, *Extract DDL script using Database Management Tool*,
        *Apply DDL scripts on a database*.

  4. Migrate data from H2 database

     The migration from H2 database requires a third party specialized tool.
     Your organisation may need to purchase a licence to use it.
     Please contact R3 for further advice.

.. _migrate-3-to-enterprise-database:

Migrating existing data from a Corda 3.3 H2 database to a |release| supported database
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: The migration from H2 database to a |release| supported database requires a third party specialized tool.
          Please contact R3 for further advice.

Update Corda (open source) 3.3 node to Corda (open source) |release| node first.
Then follow the :ref:`procedure migration from H2 database <migrate-4-to-enterprise-database>`.

Migrating existing data from a Corda 3.0, 3,1 or 3.2 H2 database to a |release| supported database
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: The migration from H2 database to a |release| supported database requires a third party specialized tool.
          Please contact R3 for further advice.

Please ensure you follow the instructions in `Upgrade Notes <https://docs.corda.net/releases/release-V3.3/upgrade-notes.html>`_ to upgrade your database
to the latest minor release of Corda (3.3 as time of writing), and then proceed with upgrading following the instructions :ref:`above<migrate-3-to-enterprise-database>`
