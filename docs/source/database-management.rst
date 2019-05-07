.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Database management scripts
===========================

Corda - the platform, and the installed CorDapps store their data in a relational database (see :doc:`api-persistence`).
When a new CorDapp is installed, associated tables, indexes, foreign-keys, etc. must be created.
Similarly, when a new version of a CorDapp is installed, its database schemas may have changed,
but the existing data needs to be preserved or changed accordingly.

In Corda Enteprise, CorDapps' custom tables are created or upgraded automatically using :ref:`Liquibase <liquibase_ref>`.

Migration scripts structure
---------------------------

Corda provides migration scripts in an XML format for its internal node and vault tables. CorDapps should provide
migration scripts for the tables they manage. In Corda, ``MappedSchemas`` (see :doc:`api-persistence`) manage JPA
Entities and thus the corresponding database tables. So ``MappedSchemas`` are the natural place to point to the
changelog file(s) that contain the change-sets for those tables. Nodes can configure which ``MappedSchemas`` are included
which means only the required tables are created. To follow standard best practices, our convention for structuring the
change-logs is to have a "master" changelog file per ``MappedSchema`` that will only include release change-logs (see example below).

Example:

As a hypothetical scenario, let's suppose that at some point (maybe for security reasons) the ``owner`` column of the
``PersistentCashState`` entity needs to be stored as a hash instead of the X500 name of the owning party.

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

The ``PersistentCashState`` entity is included in the ``CashSchemaV1`` schema; so, based on the convention mentioned above, 
we create a file ``cash.changelog-v2.xml`` with the above changeset and include it in `cash.changelog-master.xml`.

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


As we can see in this example, database migrations can "destroy" data, so it is therefore good practice to backup the
database before executing the migration scripts.

Creating SQL migration scripts for CorDapps
-------------------------------------------

The database management tool is distributed as a standalone JAR file named ``tools-database-manager-${corda_version}.jar``.
It is intended to be used by Corda Enterprise node administrators but it can help to develop a Liquibase script for a CorDapp.
The ``create-migration-sql-for-cordapp`` subcommand can be used to create migration scripts for each ``MappedSchema`` in
a CorDapp. Each ``MappedSchema`` in a CorDapp installed on a Corda Enterprise node requires the creation of new tables
in the node's database. It is generally considered bad practice to apply changes to a production database automatically.
Instead, migration scripts can be generated for each schema, which can then be inspected before being applied.

Usage:

.. code-block:: shell

    database-manager create-migration-sql-for-cordapp [-hvV] [--jar]
                                                      [--logging-level=<loggingLevel>]
                                                      -b=<baseDirectory>
                                                      [-f=<configFile>]
                                                      [<schemaClass>]

The ``schemaClass`` parameter can be optionally set to create migrations for a particular class, otherwise migration
schemas will be created for all classes found.

Additional options:

* ``--base-directory``, ``-b``: (Required) The node working directory where all the files are kept (default: ``.``).
* ``--config-file``, ``-f``: The path to the config file. Defaults to ``node.conf``.
* ``--jar``: Place generated migration scripts into a jar.
* ``--verbose``, ``--log-to-console``, ``-v``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
* ``--help``, ``-h``: Show this help message and exit.
* ``--version``, ``-V``: Print version information and exit.


Distributing Liqubase database management scripts with CorDapps
---------------------------------------------------------------

CorDapp developers who decide to store contract state in custom entities can create migration files for the ``MappedSchema`` they define.

There are 2 ways of associating a migration file with a schema:

1) By overriding ``val migrationResource: String`` and pointing to a file that needs to be in the classpath.
2) By putting a file on the classpath in a ``migration`` package whose name is the hyphenated name of the schema (all supported file extensions will be appended to the name).

CorDapp developers can use any of the supported formats (XML, SQL, JSON, YAML) for the migration files they create. In
case CorDapp developers distribute their CorDapps with migration files, these will be automatically applied when the
CorDapp is deployed on a Corda Enterprise node. If they are deployed on an open source Corda node, then the
migration will be ignored, and the database tables will be generated by Hibernate. In case CorDapp developers don't
distribute a CorDapp with migration files, then the organisation that decides to deploy this CordApp on a Corda
Enterprise node has the responsibility to manage the database.

During development or demo on the default H2 database, then the CorDapp will just work when deployed even if there are
no migration scripts, by relying on the primitive migration tool provided by Hibernate, which is not intended for
production.

.. warning:: A very important aspect to be remembered is that the CorDapp will have to work on all supported Corda databases.
   It is the responsibility of the developers to test the migration scripts and the CorDapp against all the databases.
   In the future we will provide additional tooling to assist with this aspect.

When developing a new version of an existing CorDapp, depending on the changes to the ``PersistentEntities``, a
changelog will have to be created as per the Liquibase documentation and the example above.

.. _database_management_add_Liquibase_retrospectively_ref:

Adding database migration scripts retrospectively to an existing CorDapp
------------------------------------------------------------------------

If a CorDapp does not include the required migration scripts for each ``MappedSchema``, these can be generated and inspected before
being applied as follows:

1. Deploy the CorDapp on your node (copy the JAR into the ``cordapps`` folder)
2. Find out the name of the ``MappedSchema`` object containing the new contract state entities
3. Call the database management tool: ``java -jar tools-database-manager-${corda_version}.jar --base-directory /path/to/node --create-migration-sql-for-cordapp com.example.MyMappedSchema``.
   This will generate a file called ``my-mapped-schema.changelog-master.sql`` in a folder called ``migration`` in the ``base-directory``.
   If no ``MappedSchema`` object is specified, the tool will generate one SQL file for each schema defined in the CorDapp
4. Inspect the file(s) to make sure it is correct. This is a standard SQL file with some Liquibase metadata as comments
5. Create a JAR with the ``migration`` folder (by convention it could be named: ``originalCorDappName-migration.jar``),
   and deploy this JAR in the node's ``cordapps`` folder together with the CorDapp (e.g. run the following command in the node's base directory
   ``jar cvf /path/to/node/cordapps/MyCordapp-migration.jar migration``)
6. To make sure that the new migration will be used, do a dry run with the database management tool and inspect the output file

Considerations for migrating Open Source CorDapps to Corda Enterprise
---------------------------------------------------------------------

If a Corda Node is upgraded from Open Source to Enterprise, then any CorDapps need to contain Liquibase scripts.
Any custom tables, which are required by CorDapps, were created manually or by Hibernate upon node startup.
Because of that the database doesn't contain an entry in the *DATABASECHANGELOG* table which is created by the Liquibase runner.
You would need to create such entries and provide them to a node operator, in order to run them manually.

See the Corda node upgrade procedure :ref:`details steps <upgrading_os_to_ent_1>` how to obtain SQL statements.
Also see  `Liquibase Sql Format <http://www.liquibase.org/documentation/sql_format.html>`_.


