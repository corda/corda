.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>


Database Concerns for Corda Enterprise Nodes
============================================

Corda - the platform, and the installed third-party CorDapps store their data in a relational database (see :doc:`api-persistence`).

When Corda is first installed, or when a new CorDapp is installed, associated tables, indexes, foreign-keys, etc must be created.

Similarly, when Corda is upgraded, or when a new version of a CorDapp is installed, their database schemas may have changed,
but the existing data needs to be preserved or changed accordingly.

Corda supports multiple database management systems, so CorDapp developers need to keep this database portability requirement in mind when writing and testing the code.

To address these concerns, Corda Enterprise provides a mechanism to make it straightforward to migrate from the old schemas to the new ones whilst preserving data.
It does this by integrating a specialised database migration library.

Also Corda Enterprise makes it easy to "lift" a CorDapp that does not handle the database migration (e.g.: the CorDapp developers did not include db migration scripts)

This document is addressed to Node Administrators and CorDapp developers.

* Node Administrators need to understand how the manage the underlying database.
* CorDapp Developers need to understand how to write migration scripts.


Database Migration
==================

"Database migrations" (or schema migrations) in this document, refers to the evolution of the database schema or the actual data
that a Corda Node uses when new releases of Corda or CorDapps are installed.

On a high level, this means that the corda binaries will ship with scripts that cover everything from the creation of the schema
for the initial install to changes on subsequent versions.

A Corda Node runs on top of a database that contains internal node tables, vault tables and CorDapp tables.
The database migration framework will handle all of these in the same way, as evolutions of schema and data.

As a database migration framework, we use the open source library `Liquibase <http://www.liquibase.org/>`_.

.. note::
    This advanced feature is only provided in Corda Enterprise.
    Whenever an upgraded version of Corda or a new version of a CorDapp is shipped that requires a different database schema to its predecessor,
    it is the responsibility of the party shipping the code (R3 in the case of Corda; the app developer in the case of a CorDapp) to also provide the migration scripts.
    Once such a change has been applied to the actual database, this fact is recorded in the database by the database migration library (see below),
    hence providing a mechanism to determine the 'version' of any given schema.


About Liquibase
---------------

Liquibase is a generic framework to implement an automated, version based database migration framework that supports a large number of databases.

It works by maintaining a list of applied change sets.

A changeset can be something very simple like adding a new column to a table.

It stores each executed changeset with columns like id, author, timestamp, description, md5 hash, etc in a table called ``DATABASECHANGELOG``.

This changelog table will be read every time a migration command is run to determine what change sets need to be executed.

It represents the "version" of the database (The sum of the executed change sets at any point).

Change sets are scripts written in a supported format (xml, yml, sql), and should never be modified once they have been executed. Any necessary correction should be applied in a new changeset.

For documentation around liquibase see: `The Official website <http://www.liquibase.org>`_ and `Tutorial <https://www.thoughts-on-java.org/database-migration-with-liquibase-getting-started>`_.
(Understanding how liquibase works is highly recommended for understanding how database migrations work in Corda.)

Integration with the Corda node
===============================

Operational
-----------
By default, a node will *not* attempt to execute database migration scripts at startup (even when a new version has been deployed), but will check the database "version" (see above),
and halt if the database is not in sync with the node, to avoid data corruption.

To bring the database to the correct state we provide an advanced migration tool. (see below)

Running the migration at startup automatically can be configured by specifying true in the ``database.runMigration`` node configuration setting (default behaviour is false).

We recommend Node administrators to leave the default behaviour in production, and use the migration tool to have better control. (See below)

It is safe to run at startup if you have implemented the usual best practices for database management ( e.g. running a backup before installing a new version, etc).


Migration scripts structure
---------------------------
Corda provides migration scripts in an XML format for its internal node and vault tables.
CorDapps should provide migration scripts for the tables they manage.

In Corda, ``MappedSchemas`` (see :doc:`api-persistence`) manage JPA Entities and thus the corresponding database tables.
So ``MappedSchemas`` are the natural place to point to the changelog file(s) that contain the changesets for those tables.

Nodes can configure which ``MappedSchemas`` are included which means only the required tables are created.

To follow standard best practices, our convention for structuring the changelogs is to have a "master" changelog file per ``MappedSchema`` that will only include release changelogs. (see example below )

Example:

As a hypothetical scenario, let's suppose that at some point (maybe for security reasons) the ``owner`` column of the ``PersistentCashState`` entity needs to be stored as a hash instead of the X500 name of the owning party.

This means, as a CorDapp developer we have to do these generic steps:

1. In the ``PersistentCashState`` entity we need to replace

.. code-block:: kotlin

    @Column(name = "owner_name")
    var owner: AbstractParty,

with:

.. code-block:: kotlin

    @Column(name = "owner_name_hash", length = MAX_HASH_HEX_SIZE)
    var ownerHash: String,

2. Add a ``owner_key_hash`` column to the ``contract_cash_states`` table. (Each JPA Entity usually defines a table name as a @Table annotation.)

3. Run an update to set the ``owner_key_hash`` to the hash of the ``owner_name``. This is needed to convert the existing data to the new (hashed) format.

4. Delete the ``owner_name`` column

Steps 2. 3. and 4. can be expressed very easily like this:

.. code-block:: xml

    <changeSet author="R3.Corda" id="replace owner_name with owner_hash">
        <addColumn tableName="contract_cash_states">
            <column name="owner_name_hash" type="nvarchar(130)"/>
        </addColumn>
        <update tableName="contract_cash_states">
            <column name="owner_name_hash" valueComputed="hash(owner_name)"/>
        </update>
        <dropColumn tableName="contract_cash_states" columnName="owner_name"/>
    </changeSet>

The ``PersistentCashState`` entity is included in the ``CashSchemaV1`` schema, so based on the above mentioned convention we create a file ``cash.changelog-v2.xml`` with the above changeset and include in `cash.changelog-master.xml`.

.. code-block:: kotlin

    @CordaSerializable
    object CashSchemaV1 : MappedSchema(
            schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCashState::class.java)) {

        override val migrationResource = "cash.changelog-master"


.. code-block:: xml

    <databaseChangeLog>
        <!--the original schema-->
        <include file="migration/cash.changelog-init.xml"/>

        <!--added now-->
        <include file="migration/cash.changelog-v2.xml"/>
    </databaseChangeLog>


As we can see in this example, database migrations can "destroy" data, so it is therefore good practice to backup the database before executing the migration scripts.

Migration tool:
===============

The Advanced Database migration tool is distributed as a standalone jar file named db-migration-tool-${corda_version}.jar

It is intended to be used by Corda Enterprise node administrators.

Currently it has these features:
    1. It allows running the migration on the database (`--execute-migration` )
    2. Offers the option to inspect the actual sql statements that will be run as part of the current migration (`--dry-run` )
    3. Sometimes, when a node or the migration tool crashes while running migrations, Liquibase will not release the lock.
       This can happen during some long database operation, or when an admin kills the process.
       ( This cannot happen during normal operation of a node. Only during the migration process.)
       See: <http://www.liquibase.org/documentation/databasechangeloglock_table.html>.
       The tool provides a "release-lock" command that would forcibly unlock the db migration.
    4. When a CorDapp that does not is ready to be deployed on a Corda Enterprise production node,
       using this tool, the CorDapp can be "lifted" (`--create-migration-sql-for-cordapp`).
       The reason this is needed is because those CorDapps don't handle this enterprise level concern.
       See below for details.
    5. The tool is intended to be used to run database migrations on "Doorman" instances. There is no difference from running over a normal node.


It has the following command line options:

.. table::

   ====================================  =======================================================================
     Option                               Description
   ====================================  =======================================================================
    --help                               Print help message.
    --mode                               Either 'NODE' or 'DOORMAN'. By default 'NODE'.
    --base-directory(*)                  The node or doorman directory.
    --config-file                        The name of the config file. By default 'node.conf' for a simple node and 'network-management.conf' for a doorman.
    --doorman-jar-path                   The path to the doorman fat jar.
    --create-migration-sql-for-cordapp   Create migration files for a CorDapp. You can specify the fully qualified name of the `MappedSchema` class. If not specified it will generate the migration for all schemas that don't have migrations. The output directory is the base-directory, where a `migration` folder is created.
    --dry-run                            Output the database migration to the specified output file. The output directory is the base-directory. You can specify a file name or 'CONSOLE' if you want to send the output to the console.
    --execute-migration                  This option will run the db migration on the configured database. This is the only command that will actually write to the database.
    --release-lock                       Releases whatever locks are on the database change log table, in case shutdown failed.
   ====================================  =======================================================================

For example:

``java -jar db-migration-tool-R3.CORDA-3.0-DP3-RC01.jar --base-directory /path/to/node --execute-migration``

.. note:: When running the migration tool, prefer using absolute paths when specifying the "base-directory".


How-To:
=======

Node Administrator installing Corda for the first time
------------------------------------------------------
- run normal installations steps
- Using the db migration tool attempt a dry-run to inspect the output sql
    ``--base-directory /path/to/node --dry-run``
- The output sql from the above command can be executed directly on the database or this command can be run:
    ``--base-directory /path/to/node --execute-migration``
- At this point the corda node can be started successfully


Node Administrator installing new version of Corda
--------------------------------------------------
- deploy new version of Corda
- attempt to start node. If there are db migrations in the new release, then the node will exit and will show how many changes are needed
- The same steps as above can be executed: dry-run and/or execute-migration


Node Administrator installing new CorDapp
-----------------------------------------
- deploy new CorDapp to the node
- same steps as above


Node Administrator installing new version of CorDapp
----------------------------------------------------
- replace old CorDapp with new version of CorDapp
- same steps as above


Node Administrator installing a CorDapp developed by the OS community
---------------------------------------------------------------------
The Corda (OS) project does not have support for database migrations as this is an Enterprise feature.
So CorDapps contributed by the OS community will not have this concern addressed by their original developers
To help Corda Enterprise users, we offer support in the migration tool for "Lifting" a Cordapp

These are the steps:
    - deploy the CorDapp on your node (copy the jar in the `cordapps` folder)
    - find out the name of the MappedSchema containing the new contract state entities.
    - call the migration tool: ``--base-directory /path/to/node --create-migration-sql-for-cordapp com.example.MyMappedSchema``
    - this will generate a file called ``my-mapped-schema.changelog-master.sql`` in a folder called ``migration`` in the `base-directory`
    - in case you don't specify the actual MappedSchema name, the tool will generate one sql file for each schema defined in the CorDapp
    - inspect the file(s) to make sure it is correct. This is a standard sql file with some liquibase metadata as comments.
    - create a jar with the `migration` folder (by convention it could be named: originalCorDappName-migration.jar), and deploy this jar together with the CorDapp
    - To make sure that the new migration will be used, the migration tool can be run in a `dry-run` mode and inspect the output file


Node Administrator deploying a new version of a CorDapp developed by the OS community
--------------------------------------------------------------------------------
This is a slightly more complicated scenario.

The Node Administrator will have to understand the changes (if any) that happened in the latest version.

If there are changes that require schema changes, the Node Administrator will have to write and test those.

The way to do that is to create a new changeset in the existing changelog for that CorDapp ( generated as above)

See  `Liquibase Sql Format <http://www.liquibase.org/documentation/sql_format.html>`_


CorDapp developer developing a new CorDapp
------------------------------------------

CorDapp developers who decide to store contract state in custom entities can create migration files for the ``MappedSchema`` they define.

There are 2 ways of associating a migration file with a schema:
 1) By overriding ``val migrationResource: String`` and pointing to a file that needs to be in the classpath
 2) By putting a file on the classpath in a `migration` package whose name is the hyphenated name of the schema. (All supported file extensions will be appended to the name)

CorDapp developers can use any of the supported formats (xml, sql, json, yaml) for the migration files they create.

In case CorDapp developers distribute their CorDapps with migration files, these will be automatically applied when the CorDapp is deployed on a Corda Enterprise node.
If they are deployed on a standard ("Open source") Corda node, then the migration will be ignored, and the database tables will be generated by Hibernate.

In case CorDapp developers don't distribute a CorDapp with migration files, then the organisation that decides to deploy this CordApp on a Corda Enterprise node has the responsibility to manage the database.

During development or demo on the default H2 database, then the CorDapp will just work when deployed even if there are no migration scripts, by relying on the primitive migration tool provided by hibernate, which is not intended for production.

A very important aspect to be remembered is that the CorDapp will have to work on all supported Corda databases.
It is the responsibility of the developers to test the migration scripts and the CorDapp against all the databases.
In the future we will provide aditional tooling to assist with this aspect.

CorDapp developer developing a new version of an exiting CorDapp
----------------------------------------------------------------
Depending on the changes to the ``PersistentEntities`` a changelog will have to be created as per the liquibase documentation and the example above.

Troubleshooting
---------------
When seeing behavour similar to `this <https://stackoverflow.com/questions/15528795/liquibase-lock-reasons>`_

You can run ``--base-directory /path/to/node --release-lock``


