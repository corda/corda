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

.. note:: Presently the node includes an instance of the H2 database but any database that supports JDBC is a
          candidate and the node will in the future support a range of database implementations via their JDBC drivers. Much
          of the node internal state is also persisted there. You can access the internal H2 database via JDBC, please see the
          info in ":doc:`node-administration`" for details.

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

JDBC session's can be used in Flows and Service Plugins (see ":doc:`flow-state-machines`")

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

Database Migration
==================

As a database migration tool, we use the open source library liquibase  <http://www.liquibase.org/>.

If migration is enabled, the database state is checked (and updated) during node startup. (After deploying a new version of the code that contains database migrations, they are executed at that point).
Possible database changes range from schema changes to data changes.
If migration is disabled (the default), then on node startup, the database "version" is checked if it is up-to-date with the deployed code.

Liquibase will create a table called ``DATABASECHANGELOG``, that will store useful information about each change ( like timestamp, description, user, md5 hash so it can't be changed, etc)
We can also "tag" the database at each release to make rollback easier.

Database changes are maintained in several xml files per ``MappedSchema``, so that only migrations corresponding to the node’s configured schemas are run.
For example, on the node-info schema, if there are any database changes for release 12, the changes will be added to a new file called:  ``node-info.changelog-v12.xml`` which has to be included in ``node-info.changelog-master.xml``.


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

    <changeSet author="developer_name" id="add content_size column">
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
	- database.runMigration = true   // false by default,

Command line arguments:

- To export the migration to a file use `—just-generate-database-migration outputSqlFile`. This will generate the delta from the last release, and will output the resulting sql into the outputSqlFile. It will not write to the db. It will not start the node! ( default value for `outputSqlFile` is a `.sql` file with the current date )

- To run the migration without starting the node: `--just-run-db-migration`
