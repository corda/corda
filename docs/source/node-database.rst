Node database
=============

Default in-memory database
--------------------------
By default, nodes store their data in an H2 database. You can connect directly to a running node's database to see its
stored states, transactions and attachments as follows:

* Enable the H2 database access in the node configuration using the following syntax:

  .. sourcecode:: groovy
    h2Settings {
        address: "localhost:0"
    }

* Download the **last stable** `h2 platform-independent zip <http://www.h2database.com/html/download.html>`_, unzip the zip, and
  navigate in a terminal window to the unzipped folder
* Change directories to the bin folder: ``cd h2/bin``

* Run the following command to open the h2 web console in a web browser tab:

  * Unix: ``sh h2.sh``
  * Windows: ``h2.bat``

* Find the node's JDBC connection string. Each node outputs its connection string in the terminal
  window as it starts up. In a terminal window where a node is running, look for the following string:

  ``Database connection URL is              : jdbc:h2:tcp://10.18.0.150:56736/node``

* Paste this string into the JDBC URL field and click ``Connect``, using the default username (``sa``) and no password.

You will be presented with a web interface that shows the contents of your node's storage and vault, and provides an
interface for you to query them using SQL.

The default behaviour is to expose the H2 database on localhost. This can be overridden in the
node configuration using ``h2Settings.address`` and specifying the address of the network interface to listen on,
or simply using ``0.0.0.0:0`` to listen on all interfaces.

PostgreSQL
----------
Nodes can also be configured to use PostgreSQL 9.6, using PostgreSQL JDBC Driver 42.1.4.

.. warning:: This is an experimental community contribution. The Corda continuous integration pipeline does not run unit 
   tests or integration tests of this feature.

Configuration
~~~~~~~~~~~~~
Here is an example node configuration for PostgreSQL:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSource.url = "jdbc:postgresql://[HOST]:[PORT]/postgres"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }

Note that:

* The ``database.schema`` property is optional
* The value of ``database.schema`` is not wrapped in double quotes and Postgres always treats it as a lower-case value
  (e.g. ``AliceCorp`` becomes ``alicecorp``)
* If you provide a custom ``database.schema``, its value must either match the ``dataSource.user`` value to end up
  on the standard schema search path according to the
  `PostgreSQL documentation <https://www.postgresql.org/docs/9.3/static/ddl-schemas.html#DDL-SCHEMAS-PATH>`_, or
  the schema search path must be set explicitly for the user.



Node Database Schema
----------
The node has a standard set of tables in its database schema which is used to store information into the database.
Tables below:

contract_cash_states	

cp_states

node_attachments

node_attachments_contracts	

node_checkpoints	

node_contract_upgrades	

node_identities

node_info_hosts	

node_info_party_cert

node_infos

node_link_nodeinfo_party	

node_message_ids
> *stores in-flight information related to all communication and interactions the the node during a transaction*

node_message_retry	

node_mutual_exclusion	

node_named_identities	

node_our_key_pairs	

node_properties	

node_scheduled_states

node_transaction_mappings	

node_transactions	

vault_fungible_states

vault_fungible_states_parts	

vault_linear_states	

vault_linear_states_parts	

vault_states	

vault_transaction_notes	

DATABASECHANGELOG

DATABASECHANGELOGLOCK	

