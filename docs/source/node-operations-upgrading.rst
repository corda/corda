Node upgrades
=============

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

For running existing CorDapps built against Corda open source against Corda Enterprise 3.0, the following database changes
are required:

* The ``net.corda.core.schemas.PersistentStateRef`` fields (``index`` and ``txId``) were incorrectly marked as nullable
  in previous versions and are now non-nullable ( see :doc:`changelog` for more information).

  * To upgrade using a H2 database:

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

    No action is needed for default node tables, or custom CorDapp entities using ``PersistentStateRef`` as a Primary Key, as
    in this case the backing columns are automatically not nullable.

* A table name with a typo has been corrected

  * To upgrade using a H2 database:

    While the node is stopped, run the following SQL statement for each database instance and schema:

    .. sourcecode:: sql

       ALTER TABLE [schema.]NODE_ATTCHMENTS_CONTRACTS RENAME TO NODE_ATTACHMENTS_CONTRACTS;

    The ``schema`` parameter is optional.

Upgrading the version of a CorDapp on a node

Upgrading CorDapps on a node
----------------------------

In order to upgrade a CorDapps on a node to a new version, it needs to be determined whether any backwards compatible
changes have been made.

For developer information on upgrading CorDapps, see :doc:`upgrading-cordapps`.

Flow upgrades
~~~~~~~~~~~~~

If any backwards-incompatible changes have been made (see :ref:`upgrading-cordapps-backwards-incompatible-flow-changes`
for more information), the upgrade method detailed below will need to be followed. Otherwise the CorDapp JAR can just
be replaced with the new version.

Contract and State upgrades
~~~~~~~~~~~~~~~~~~~~~~~~~~~

There are two types of contract/state upgrade:

1. *Implicit:* By allowing multiple implementations of the contract ahead of time, using constraints. See
   :doc:`api-contract-constraints` to learn more.
2. *Explicit:* By creating a special *contract upgrade transaction* and getting all participants of a state to sign it using the
   contract upgrade flows.

This documentation only considers the *explicit* type of upgrade, as implicit contract upgrades are handled by the application.

In an explicit upgrade contracts and states can be changed in arbitrary ways, if and only if all of the state's participants
agree to the proposed upgrade. The following combinations of upgrades are possible:

* A contract is upgraded while the state definition remains the same.
* A state is upgraded while the contract stays the same.
* The state and the contract are updated simultaneously.

Running the upgrade
~~~~~~~~~~~~~~~~~~~

If a contract or state requires an explicit upgrade then all states will need updating to the new contract at a time agreed
by all participants. The updated CorDapp JAR needs to be distributed to all relevant parties in advance of the changeover
time.

In order to perform the upgrade, follow the following steps:

* If required, do a flow drain to avoid the definition of states or contracts changing whilst a flow is in progress (see :ref:`upgrading-cordapps-flow-drains` for more information)

  * By RPC using the ``setFlowsDrainingModeEnabled`` method with the parameter ``true``
  * Via the shell by issuing the following command ``run setFlowsDrainingModeEnabled enabled: true``

* Check that all the flows have completed

  * By RPC using the ``stateMachinesSnapshot`` method and checking that there are no results
  * Via the shell by issuing the following command ``run stateMachinesSnapshot``

* Once all flows have completed, stop the node
* Replace the existing JAR with the new one
* Make any database changes required to any custom vault tables for the upgraded CorDapp
* Restart the node
* If you drained the node prior to upgrading, switch off flow draining mode to allow the node to continue to receive requests

  * By RPC using the ``setFlowsDrainingModeEnabled`` method with the parameter ``false``
  * Via the shell by issuing the following command ``run setFlowsDrainingModeEnabled enabled: false``

* Run the contract upgrade authorisation flow (``ContractUpgradeFlow$Initiate``) for each state that requires updating on every node.

  * You can do this manually via RPC but for anything more than a couple of states it is assumed that a script will be
    provided by the CorDapp developer to query the vault and run this for all states
  * The contract upgrade initiate flow only needs to be run on one of the participants for each state. The flow will
    automatically upgrade the state on all participants.
