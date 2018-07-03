Upgrade a Corda Node to Corda Enterprise
========================================

Upgrading the version of Corda on a node
----------------------------------------

CorDapps, contracts and states written for Corda 3.x are compatible with Corda Enterprise 3.0, so upgrading
existing open source Corda nodes should be a simple case of updating the Corda JAR. For developer information on recompiling
CorDapps against Corda Enterprise, see :doc:`upgrade-notes`.


Upgrading the Node
~~~~~~~~~~~~~~~~~~

To upgrade a node running on Corda 3.x to Corda Enterprise 3.0:

* Stop the node
* Make a backup of the node for rollback purposes
* Replace the Corda JAR with the Corda Enterprise JAR (see :ref:`resolve-corda-enterprise-binaries` for more information)
* Make any necessary changes to the config file:

  * The network map is no longer provided by a node and thus the ``networkMapService`` config is ignored. Instead the
    network map is either provided by the compatibility zone (CZ) operator (who operates the doorman) and available
    using the ``compatibilityZoneURL`` config, or is provided using signed node info files which are copied locally.
    See :doc:`network-map` for more details, and :doc:`setting-up-a-corda-network` on how to use the network
    bootstrapper for deploying a local network.

  * Remove any ``webAddress`` keys from the config file. The Corda webserver has been deprecated but is still available
    as a standalone JAR for development purposes. See :doc:`running-a-node` for more information.

  * All nodes now require an ``rpcSettings`` section in their config file
  * For more information on the available fields in the ``node.conf`` file see :ref:`corda-configuration-file-fields`

* Make any necessary changes to the database as described :ref:`below<node-operations-upgrading-cordapps>`
* Restart the node
* When the node starts up it should display the "Corda Enterprise" banner with a helpful tip, instead of the open source
  starting banner

.. _node-operations-upgrading-cordapps:

Database upgrades
~~~~~~~~~~~~~~~~~

When upgrading an existing node from Corda 3.x to Corda Enterprise, the node operator has the option of using one of the enterprise
database engines that are supported (see :doc:`node-database`).
We highly recommend moving away from the default H2 database when setting up a production node.

Also, on Corda Enterprise we have integrated Liquibase to track database changes. See :doc:`database-management`, for more info.


Migrating existing data from a Corda 3.0 or 3.1 H2 database to the Corda Enterprise database
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

I) Backup the database and run the following statements in order to align the database with Corda Enterprise:

    For CorDapps persisting custom entities with ``PersistentStateRef`` used as a non Primary Key column, the backing table needs to be updated.
    In SQL replace ``your_transaction_id``/``your_output_index`` column names with your custom names, if the entity doesn't use the JPA
    ``@AttributeOverrides`` then the default names are ``transaction_id`` and ``output_index``.

    First, run the following SQL statement to determine whether any existing rows have ``NULL`` values:

    .. sourcecode:: sql

         SELECT count(*) FROM [YOUR_PersistentState_TABLE_NAME] WHERE your_transaction_id IS NULL OR your_output_index IS NULL;

    * If the table already contains rows with ``NULL`` columns, and ``NULL`` values and empty strings are handled in the same way,
      all ``NULL`` column occurrences can be changed to an empty string using the following SQL:

      .. sourcecode:: sql

         UPDATE [YOUR_PersistentState_TABLE_NAME] SET your_transaction_id="" WHERE your_transaction_id IS NULL;
         UPDATE [YOUR_PersistentState_TABLE_NAME] SET your_output_index="" WHERE your_output_index IS NULL;

      Once no rows have any ``NULL`` values for ``transaction_ids`` or ``output_idx``, then it's safe to update the table using
      the following SQL:

      .. sourcecode:: sql

         ALTER TABLE [YOUR_PersistentState_TABLE_NAME] ALTER COLUMN your_transaction_id SET NOT NULL;
         ALTER TABLE [YOUR_PersistentState_TABLE_NAME] ALTER COLUMN your_output_index SET NOT NULL;

    * If the table already contains rows with ``NULL`` values, and the logic is different between ``NULL`` values and empty strings
      and needs to be preserved, you would need to create a copy of the ``PersistentStateRef`` class with a different name and
      use the new class in your entity.

    No action is needed for default node tables, or custom CorDapp entities using ``PersistentStateRef`` as a primary key, as
    in this case the backing columns are automatically not nullable.

    * A table name with a typo has been corrected

        .. sourcecode:: sql

           ALTER TABLE [schema.]NODE_ATTCHMENTS_CONTRACTS RENAME TO NODE_ATTACHMENTS_CONTRACTS;

        The ``schema`` parameter is optional.

    .. note:: Don't forget to backup the H2 database as after the changes the database will no longer be compatible with a Corda 3.0 or 3.1 node.

II) Export the data from the H2 database using a specialized tool.


III) Prepare a new database and import the data:

    - Use the database management tool (see :doc:`database-management`) to setup the database schema.
    - Import the data into the new database.


