.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Persistence
================

.. contents::

Corda offers developers the option to expose all or some part of a contract state to an *Object Relational Mapping*
(ORM) tool to be persisted in a RDBMS.  The purpose of this is to assist *vault* development by effectively indexing
persisted contract states held in the vault for the purpose of running queries over them and to allow relational joins
between Corda data and private data local to the organisation owning a node.

The ORM mapping is specified using the `Java Persistence API <https://en.wikipedia.org/wiki/Java_Persistence_API>`_
(JPA) as annotations and is converted to database table rows by the node automatically every time a state is recorded
in the node's local vault as part of a transaction.

.. note:: Presently the node includes an instance of the H2 database. H2 database is supported for development purposes,
          and we have certified R3 Corda to work against SQL Server 2017 and Azure SQL.
          PostgreSQL 9.6 is supported preliminarily. Other databases will be officially supported very soon.
          Much of the node internal state is also persisted there. You can access
          the internal H2 database via JDBC, please see the info in ":doc:`node-administration`" for details.

Schemas
-------
Every ``ContractState`` can implement the ``QueryableState`` interface if it wishes to be inserted into the node's local
database and accessible using SQL.

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/schemas/PersistentTypes.kt
    :language: kotlin
    :start-after: DOCSTART QueryableState
    :end-before: DOCEND QueryableState

The ``QueryableState`` interface requires the state to enumerate the different relational schemas it supports, for
instance in cases where the schema has evolved, with each one being represented by a ``MappedSchema`` object return
by the ``supportedSchemas()`` method.  Once a schema is selected it must generate that representation when requested
via the ``generateMappedObject()`` method which is then passed to the ORM.

Nodes have an internal ``SchemaService`` which decides what to persist and what not by selecting the ``MappedSchema``
to use.

.. literalinclude:: ../../node/src/main/kotlin/net/corda/node/services/api/SchemaService.kt
    :language: kotlin
    :start-after: DOCSTART SchemaService
    :end-before: DOCEND SchemaService

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/schemas/PersistentTypes.kt
    :language: kotlin
    :start-after: DOCSTART MappedSchema
    :end-before: DOCEND MappedSchema

The ``SchemaService`` can be configured by a node administrator to select the schemas used by each app. In this way the
relational view of ledger states can evolve in a controlled fashion in lock-step with internal systems or other
integration points and not necessarily with every upgrade to the contract code. It can select from the
``MappedSchema`` offered by a ``QueryableState``, automatically upgrade to a later version of a schema or even
provide a ``MappedSchema`` not originally offered by the ``QueryableState``.

It is expected that multiple different contract state implementations might provide mappings to some common schema.
For example an Interest Rate Swap contract and an Equity OTC Option contract might both provide a mapping to a common
Derivative schema. The schemas should typically not be part of the contract itself and should exist independently of it
to encourage re-use of a common set within a particular business area or Cordapp.

``MappedSchema`` offer a family name that is disambiguated using Java package style name-spacing derived from the
class name of a *schema family* class that is constant across versions, allowing the ``SchemaService`` to select a
preferred version of a schema.

The ``SchemaService`` is also responsible for the ``SchemaOptions`` that can be configured for a particular
``MappedSchema`` which allow the configuration of a database schema or table name prefixes to avoid any clash with
other ``MappedSchema``.

.. note:: It is intended that there should be plugin support for the ``SchemaService`` to offer the version upgrading
   and additional schemas as part of Cordapps, and that the active schemas be configurable.  However the present
   implementation offers none of this and simply results in all versions of all schemas supported by a
   ``QueryableState`` being persisted. This will change in due course. Similarly, it does not currently support
   configuring ``SchemaOptions`` but will do so in the future.

Custom schema registration
--------------------------
Custom contract schemas are automatically registered at startup time for CorDapps. The node bootstrap process will scan
for schemas (any class that extends the ``MappedSchema`` interface) in the `plugins` configuration directory in your CorDapp jar.

For testing purposes it is necessary to manually register the packages containing custom schemas as follows:

- Tests using ``MockNetwork`` and ``MockNode`` must explicitly register packages using the `cordappPackages` parameter of ``MockNetwork``
- Tests using ``MockServices`` must explicitly register packages using the `cordappPackages` parameter of the ``MockServices`` `makeTestDatabaseAndMockServices()` helper method.

.. note:: Tests using the `DriverDSL` will automatically register your custom schemas if they are in the same project structure as the driver call.

Object relational mapping
-------------------------
The persisted representation of a ``QueryableState`` should be an instance of a ``PersistentState`` subclass,
constructed either by the state itself or a plugin to the ``SchemaService``.  This allows the ORM layer to always
associate a ``StateRef`` with a persisted representation of a ``ContractState`` and allows joining with the set of
unconsumed states in the vault.

The ``PersistentState`` subclass should be marked up as a JPA 2.1 *Entity* with a defined table name and having
properties (in Kotlin, getters/setters in Java) annotated to map to the appropriate columns and SQL types.  Additional
entities can be included to model these properties where they are more complex, for example collections, so the mapping
does not have to be *flat*. The ``MappedSchema`` must provide a list of all of the JPA entity classes for that schema
in order to initialise the ORM layer.

Several examples of entities and mappings are provided in the codebase, including ``Cash.State`` and
``CommercialPaper.State``. For example, here's the first version of the cash schema.

.. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/schemas/CashSchemaV1.kt
    :language: kotlin

Identity mapping
----------------
Schema entity attributes defined by identity types (``AbstractParty``, ``Party``, ``AnonymousParty``) are automatically
processed to ensure only the ``X500Name`` of the identity is persisted where an identity is well known, otherwise a null
value is stored in the associated column. To preserve privacy, identity keys are never persisted. Developers should use
the ``IdentityService`` to resolve keys from well know X500 identity names.

.. _jdbc_session_ref:

JDBC session
------------
Apps may also interact directly with the underlying Node's database by using a standard
JDBC connection (session) as described by the `Java SQL Connection API <https://docs.oracle.com/javase/8/docs/api/java/sql/Connection.html>`_

Use the ``ServiceHub`` ``jdbcSession`` function to obtain a JDBC connection as illustrated in the following example:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/database/HibernateConfigurationTest.kt
  :language: kotlin
  :start-after: DOCSTART JdbcSession
  :end-before: DOCEND JdbcSession

JDBC sessions can be used in Flows and Service Plugins (see ":doc:`flow-state-machines`")

The following example illustrates the creation of a custom corda service using a jdbcSession:

.. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/CustomVaultQuery.kt
  :language: kotlin
  :start-after: DOCSTART CustomVaultQuery
  :end-before: DOCEND CustomVaultQuery

which is then referenced within a custom flow:

.. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/CustomVaultQuery.kt
  :language: kotlin
  :start-after: DOCSTART TopupIssuer
  :end-before: DOCEND TopupIssuer

For examples on testing ``@CordaService`` implementations, see the oracle example :doc:`here <oracles>`

.. _database_migration_ref:

Database Migration
==================

As a database migration tool, we use the open source library liquibase <http://www.liquibase.org/>.

Migration is enabled by specifying true in the ``database.runMigration`` node configuration setting (default behaviour is false).
When enabled, the database state is checked, and updated during node startup.

The default behaviour (``database.runMigration=false``) is to just check the database state, and fail if it is not up to date. To bring the database to the correct state we provide an advanced migration tool. See below for details.

For example, if migration is enabled, after deploying a new version of the code that contains database migrations (see example below for a possible scenario), they are executed at that point (during startup).

Possible database changes range from schema changes to data changes. (The database changes are grouped together in `changesets`. See the example below.).

About Liquibase
---------------

Liquibase will create a table called ``DATABASECHANGELOG``, that will store information about each executed change (like timestamp, description, user, md5 hash so it can't be changed, etc).
This table will be used every time a migration command is run to determine what changesets need to be applied.
Changesets should never be modified once they were executed. Any correction should be applied in a new changeset.
We can also "tag" the database at each release to make rollback easier.

Database changes are maintained in several xml files per ``MappedSchema``, so that only migrations corresponding to the node’s configured schemas are run.
The migration file(s) for all ``MappedSchemas`` are dynamically included in the global changelog, as long as they are present on the classpath and are either explicitly declared in the ``MappedSchema`` implementation, or follow a naming convention based on the ``MappedSchema`` name.
(The migration tool that we provide can generate liquibase files with the correct name for a schema)

Our convention is to maintain a "master" changelog file per ``MappedSchema`` which will include "version" changelogs.
By following our versioning convention, and using the node-info schema as an example, if there are any database changes for release 12, the changes will be added to a new file called: ``node-info.changelog-v12.xml`` which has to be included in ``node-info.changelog-master.xml``.


Example:
--------

Let's suppose that at some point, at version 12, there is a need to add a new column: ``contentSize`` to the ``DBAttachment`` entity.

This means we have to:
    - In the source code, add the ``contentSize`` property and map it to a new column.
    - create the column in the ``node_attachments`` table.
    - Run an update to set the size of all existing attachments, to not break the code that uses the new property

.. code-block:: kotlin

    class DBAttachment(
        ...
        @Column(name = "content")
        @Lob
        var content: ByteArray,

        //newly added column
        @Column(name = "content_size")
        var contentSize: Int,
        ...
    )

The ``DBAttachment`` entity is included in the ``NodeServicesV1`` schema, so we create a file ``node-services.changelog-v12.xml`` with this changeset:

.. code-block:: xml

    <changeSet author="R3.Corda" id="add content_size column">
        <addColumn tableName="node_attachments">
            <column name="content_size" type="INT"/>
        </addColumn>
        <update tableName="node_attachments">
            <column name="content_size" valueComputed="length(content)"/>
        </update>
        <rollback>
            <dropColumn tableName="node_attachments" columnName="content_size"/>
        </rollback>
    </changeSet>

And include it in `node-services.changelog-master.xml`:

.. code-block:: xml

    <databaseChangeLog>
        <!--the original schema-->
        <include file="migration/node-services.changelog-init.xml"/>

        <!--migrations from previous releases-->
        <include file="migration/node-services.changelog-v4.xml"/>
        <include file="migration/node-services.changelog-v7.xml"/>

        <!--added now-->
        <include file="migration/node-services.changelog-v12.xml"/>
    </databaseChangeLog>


By adding the rollback script, we give users the option to revert to an older version of the software.

An easy way to manage the db version is to tag it on every release (or on every release that has migrations)
<http://www.liquibase.org/documentation/changes/tag_database.html>


Usage:
------

Configurations:

- To enable migration at startup, set:
	- ``database.runMigration = true``   // false by default.

Migration tool:
---------------

The Migration tool will be distributed as a standalone jar file, with the following options:

.. table::

   ====================================  =======================================================================
     Option                               Description
   ====================================  =======================================================================
    --help                               Print help message
    --mode                               Either 'NODE' or 'DOORMAN'. By default 'NODE'
    --base-directory                     The node or doorman directory
    --config-file                        The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman.
    --doorman-jar-path                   The path to the doorman fat jar
    --create-migration-sql-for-cordapp   Create migration files for a CorDapp. You can specify the fully qualified of the `MappedSchema` class. If not specified it will generate foll all schemas that don't have migrations. The output directory is the base-directory, where a `migration` folder is created.
    --dry-run                            Output the database migration to the specified output file. The output directory is the base-directory. You can specify a file name or 'CONSOLE' if you want to send the output to the console.
    --execute-migration                  This option will run the db migration on the configured database
    --release-lock                       Releases whatever locks are on the database change log table, in case shutdown failed.
   ====================================  =======================================================================

It is intended to be used by R3 Corda node administrators.
Currently it has these features :
    - it allows running the migration on the database (`--execute-migration` )
    - offers the option to inspect the actual sql statements that will be run as part of the current migration (`--dry-run` )
    - can be used to release the migration lock (`--release-lock`)
    - when a CorDapp released by the open source community is ready to be deployed on a production node, using this tool it can be "upgraded" (`--create-migration-sql-for-cordapp`). See below for details.

CorDapps:
---------

CorDapp developers who decide to store contract state in custom entities can create migration files for the ``MappedSchema`` they define.

There are 2 ways of associating a migration file with a schema:
 1) By overriding ``val migrationResource: String`` and pointing to a file that needs to be in the classpath
 2) By putting a file on the classpath in a `migration` package whose name is the hyphenated name of the schema. (All supported file extensions will be appended to the name)

CorDapp developers can use any of the supported formats (xml, sql, json, yaml) for the migration files they create.

In case CorDapp developers distribute their CorDapps with migration files, these will be automatically applied when the CorDapp is deployed on an R3 Corda node.
If they are deployed on a standard ("Open source") Corda node, then the migration will be ignored, and the database tables will be generated by Hibernate.

In case CorDapp developers don't distribute a CorDapp with migration files, then the organisation that decides to deploy this CordApp on an R3 Corda ("Enterprise Blockchain") node has the responsibility to manage the database.

The following options are available:
 1) In case the organisation is running a demo or trial node on the default H2 database, then the CorDapp will just work when deployed by relying on the migration tool provided by hibernate, which is not intended for production.
 2) In case the organisation is running a production node (with live data) on an enterprise database, then they will have to manage the database migration for the CorDapp.

    These are the steps to do this:
        - deploy the CorDapp on your node (copy the jar in the `cordapps` folder)
        - find out the name of the MappedSchema containing the new contract state entities and hyphenate it. For example:``net.corda.finance.schemasCommercialPaperSchemaV1``
        - call the migration tool ``java -jar migration-tool.jar --base-directory path_to_node --create-migration-sql-for-cordapp net.corda.finance.schemasCommercialPaperSchemaV1``
        - this will generate a file called ``commercial-paper-schema-v1.changelog-master.sql`` in a folder called ``migration`` in the `base-directory`
        - in case you don't specify the actual MappedSchema name, the tool will generate one sql file for each schema defined in the CorDapp
        - inspect the file(s) to make sure it is correct
        - create a jar with the `migration` folder (by convention it could be named: originalCorDappName-migration.jar), and deploy this jar together with the CorDapp
        - To make sure that the new migration will be used, the migration tool can be run in a `dry-run` mode and inspect the output file
