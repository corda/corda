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

Scripts structure
-----------------

The ``MappedSchema`` class should have a matching Liquibase script defining a table creation.
Liquibase scripts use declarative set of XML tags and attributes to express DDL in a cross database vendor way.
The script can also be written in SQL, however this doesn't guarantee compatibility across different database vendors.
Liquibase script code is grouped in a units called ``changeSet``s,
a single ``changeSet`` should contain instructions to create/update/delete a single table.

Database table creation
~~~~~~~~~~~~~~~~~~~~~~~

For initial table creation use tags like ``createTable`` and ``addPrimaryKey``,
see `Liquibase documentation <https://www.liquibase.org/documentation/index.html>`_ for examples and the complete Liquibase instruction list.

To illustrate how to write an example Liquibase script, we will follow a simple ``MappedSchema`` ``MySchemaV1``.
The schema has a single JPA entity called ``PersistentIOU`` with the following fields:

.. code-block:: kotlin

    import net.corda.core.identity.AbstractParty
    import net.corda.core.schemas.MappedSchema
    import net.corda.core.schemas.PersistentState
    import java.util.*
    import javax.persistence.*
    import org.hibernate.annotations.Type

    object MySchema

    object MySchemaV1 : MappedSchema(schemaFamily = MySchema.javaClass,
        version = 1, mappedTypes = listOf(PersistentIOU::class.java)) {
        @Entity
        @Table(name = "iou_states")
        class PersistentIOU(
            @Column(name = "owner_name")
            var owner: AbstractParty?,
            @Column(name = "lender")
            var lenderName: String,
            @Column(name = "value", nullable = false)
            var value: Int,
            @Column(name = "linear_id", nullable = false)
            @Type(type = "uuid-char")
            var linearId: UUID
        ) : PersistentState() {
            // Default constructor required by hibernate.
            constructor(): this(null, "", 0, UUID.randomUUID())
        }
    }

The corresponding Liquibase ``changeSet`` for the JPA entity is:

.. code-block:: xml

    <changeSet author="My_Company" id="create-my_states">
    <createTable tableName="iou_states">
        <column name="output_index" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="transaction_id" type="NVARCHAR(64)">
            <constraints nullable="false"/>
        </column>
        <column name="owner_name" type="NVARCHAR(255)"/>
        <column name="lender" type="NVARCHAR(255)">
            <constraints nullable="false"/>
        </column>
        <column name="value" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="linear_id" type="VARCHAR(255)">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <addPrimaryKey columnNames="output_index, transaction_id"
          constraintName="PK_iou_states"
          tableName="iou_states"/>
    </changeSet>

Each ``changeSet`` tag is uniquely identified by the combination of the ``author`` tag, the ``id`` tag, and the file classpath name.
The first entry ``createTable`` defines a new table.
The table and the columns names match the relevant names defined in JPA annotations of ``PersistentIOU`` class.
The columns ``output_index`` and ``transaction_id`` are mapped from ``PersistentState`` superclass fields.
A compound primary key is added via ``addPrimaryKey`` tag.
In order to achieve compatibility with supported databases,
the mapping of ``linearId`` field is a custom ``uuid-char`` type, this type can be mapped to a ``VARCHAR(255)`` column.
Corda contains a built-in custom JPA converter for the ``AbstractParty`` type to a varchar column type defined as ``NVARCHAR(255)`` in the script.


Database table modification
~~~~~~~~~~~~~~~~~~~~~~~~~~~

For any subsequent changes to a table driven by changes in a CorDapp, a new ``changeSet`` needs to be created.
The existing ``changeSet`` cannot be modified, as Liquibase needs to track the what was exactly created.

Continuing our example from the previous paragraph, let's suppose that at some point (maybe for security reasons)
the ``owner_name`` column of the ``PersistentIOU`` entity needs to be stored as a hash instead of the X500 name of the owning party.

The ``PersistentIOU`` field ``owner``

.. code-block:: kotlin

    @Column(name = "owner_name")
    var owner: AbstractParty?,

is replaced with:

.. code-block:: kotlin

    @Column(name = "owner_name_hash", length = MAX_HASH_HEX_SIZE)

To change the database table following steps are needed: a new column addition,
population of the hash value of the old column to the new column for existing rows, and the old column removal.
These activities can be express in a new ``changeSet`` as:

.. code-block:: xml

    <changeSet author="My_Company" id="replace owner_name with owner_name_hash">
        <addColumn tableName="iou_states">
            <column name="owner_name_hash" type="nvarchar(130)"/>
        </addColumn>
        <update tableName="iou_states">
            <column name="owner_name_hash" valueComputed="hash(owner_name)"/>
        </update>
        <dropColumn tableName="iou_states" columnName="owner_name"/>
    </changeSet>

The column name change allowed us to have a simplified migration steps, avoiding in-place column modification.


Distributing scripts with CorDapps
----------------------------------

By default Corda expects a Liquibase script file name to be a hyphenated version of the ``MappedSchema`` name
(upper case letters changed to lowercase and be prefixed with hyphen, except the beginning of file).
E.g. for a ``MappedSchema`` named *MySchema*, Corda searches for a *my_schema.changelog.master.xml* file
(*json* and *sql* extensions are also allowed) under *migration* package in CorDapp JARs.

You can also set the name and the location in the ``MappedSchema`` code by overriding a field ``val migrationResource: String``.
The value should be a namespace and a file name without an extension.

The files need to be on classpath which means they should be located in the resources folder of your CorDapp source code.

To follow Corda convention for structuring the change-logs is to have a *“master”* changelog file per ``MappedSchem``
that will only include release change-logs.

Continuing the *MySchema* example, the initial CorDapp release should contain two files, the “master” file *my-schema-v1.changelog-master.xml*:

.. code-block:: xml

    <?xml version="1.1" encoding="UTF-8" standalone="no"?>
    <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.5.xsd">
        <include file="migration/my-schema.changelog-init.xml"/>
    </databaseChangeLog>

The “master” file contains one entry pointing to other file.
The *my-schema.changelog-init.xml* file contains instruction to create table and primary key
(for brevity a file encoding and XML schemas in the top level entry are omitted):

.. code-block:: xml

    <databaseChangeLog>
    <changeSet author="My_Company" id="create_my_states">
    <createTable tableName="iou_states">
        <column name="output_index" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="transaction_id" type="NVARCHAR(64)">
            <constraints nullable="false"/>
        </column>
        <column name="owner_name" type="NVARCHAR(255)"/>
        <column name="lender" type="NVARCHAR(255)">
            <constraints nullable="false"/>
        </column>
        <column name="value" type="INT"/>
        <column name="linear_id" type="VARCHAR(255)"/>
    </createTable>
    <addPrimaryKey columnNames="output_index, transaction_id"
          constraintName="PK_iou_states"
          tableName="iou_states"/>
    </changeSet>
    </databaseChangeLog>

The content of the file is described in the former paragraph.
For a subsequent CorDapp releases, if there is any database schema change, a new file is created and added to a “master” changelog file.
In our example, the next release changes a name and type of the *owner_name* column.

The “master” changelog file *my-schema-v1.changelog-master.xml* will have an additional entry:

.. code-block:: xml

    <databaseChangeLog>
        <include file="migration/my-schema.changelog-init.xml"/>
        <include file="migration/my-schema.changelog-v2.xml"/>
    </databaseChangeLog>

The actual column change is defined in a new *my-schema.changelog-v2.xml* file:

.. code-block:: xml

 <databaseChangeLog>
    <changeSet author="My_Company" id="replace owner_name with owner_hash">
        <addColumn tableName="iou_states">
            <column name="owner_name_hash" type="nvarchar(130)"/>
        </addColumn>
        <update tableName="iou_states">
            <column name="owner_name_hash" valueComputed="hash(owner_name)"/>
        </update>
        <dropColumn tableName="iou_states" columnName="owner_name"/>
    </changeSet>
 </databaseChangeLog>

Also the CorDapp should contain the initial script *my-schema.changelog-init.xml* with unchanged content.


Creating script for initial table creation using Corda Database Management Tool
-------------------------------------------------------------------------------

The database management tool is distributed as a standalone JAR file named ``tools-database-manager-${corda_release_version}.jar``.
It is intended to be used by Corda Enterprise node administrators but it can help to develop an Liquibase script for a CorDapp.
A generated script has instruction in SQL format (DDL statements), which may be not portable across different databases.
Because of that, the script in SQL format should be used for development purposes only, or when a CorDapp doesn't need to be portable across
different databases (e.g. the CorDapp will be deployed on Corda nodes running against PostgreSQL),
or as a help to create the portable Liquibase script in XML format.
The tool allows to create a Liquibase script for the initial database object creation only, and cannot generate a table alteration or deletion.

The ``create-migration-sql-for-cordapp`` sub-command can be used to create initial database management scripts for each ``MappedSchema`` in a CorDapp.
Usage:

.. code-block:: shell

    java -jar tools-database-manager-|version|.jar \
                create-migration-sql-for-cordapp [-hvV]
                                                 [--jar]
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

.. warning:: A very important aspect to be remembered is that the CorDapp will have to work on all supported Corda databases.
   It is the responsibility of the developers to test the migration scripts and the CorDapp against all the databases.
   In the future we will provide additional tooling to assist with this aspect.

Continuing our ``MySchemaV1`` class example, assume that you have a running MS SQL database,
the *nodeA* directory contains Corda node configuration to connect to the database,
the *drivers* sub-directory contains a CorDapp with ``MySchemaV1``.
To obtain Liquibase script in SQL format run:

``java -jar tools-database-manager-${corda_release_version}.jar create-migration-sql-for-cordapp -b=my_cordapp/build/nodes/nodeA``

This will generate *migration/my-schema-v1.changelog-master.sql* script with the content:

.. code-block:: sql

    --liquibase formatted sql

    --changeset R3.Corda.Generated:initial_schema_for_MySchemaV1

    create table iou_states (
       output_index int not null,
        transaction_id nvarchar(64) not null,
        lender nvarchar(255),
        linear_id varchar(255) not null,
        owner_name nvarchar(255),
        value int not null,
        primary key (output_index, transaction_id)
    );

The second comment has the format ``--changeset author:change_set_id`` with default values *R3.Corda.Generated* for the script author
and *initial_schema_for_<schema_class_name>* for the ``changeSet`` id.
For development purposes the default values are sufficient however for distributing your CorDapp you should replace the generic
*R3.Corda.Generated* author name.

.. waring:: The generated script contains both a DDL and instruction for Liquibase (``--liquibase`` and ``--changeset``).
            The script is not meant to be run manually directly onto database as it would not populate Liquibase management tables
            and therefore Liquibase would run it again the next time the CorDapp is run.

As stated before, in most cases the generated script in SQL format contains DDL compatible with the database which was used for creating it only.
In the above example, the script would fail on an Oracle database due to the invalid *nvarchar* type, the correct Oracle database type is *nvarchar2*.

.. _database_management_add_Liquibase_retrospectively_ref:

Adding scripts retrospectively to an existing CorDapp
-----------------------------------------------------

If a CorDapp does not include the required migration scripts for each ``MappedSchema``, these can be generated and inspected before
being applied as follows:

1. Deploy the CorDapp on your node (copy the JAR into the ``cordapps`` folder)
2. Find out the name of the ``MappedSchema`` object containing the new contract state entities
3. Call the database management tool:
   ``java -jar corda-tools-database-manager-${corda_version}.jar --base-directory /path/to/node --create-migration-sql-for-cordapp com.example.MyMappedSchema``.
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
Also see `Liquibase Sql Format <http://www.liquibase.org/documentation/sql_format.html>`_.

Notes on Liquibase specifics
----------------------------

When writing data migrations, certain databases may have particular limitations which mean that database specific migration code is required. For example, in Oracle:

* 30 byte names - Prior to version 12c the maximum length of table/column names was around 30 bytes and post 12c the limit is 128 bytes. There is no way to reconfigure the limit or make a Liquibase workaround without also specialising the CorDapp code.

* VARCHAR longer than 2000 bytes - Liquibase does not automatically resolve the issue and will create a broken SQL statement. The solution is to migrate to LOB types (CLOB, BLOB, NCLOB) or extend the length limit. Versions after 12c can use `extended data types <https://oracle-base.com/articles/12c/extended-data-types-12cR1>`_ to do the latter.

Example Liquibase with specialised logic
----------------------------------------

When using Liquibase to work around the issue of VARCHAR length, you could create a changeset
specific to Oracle using the <changeset ... dbms="oracle"> with the supported Oracle value type, as Liquibase
itself does not do the conversion automatically.

.. code-block:: xml

    <!--This is only executed for Oracle-->
    <changeSet author="author" dbms = "oracle">
        <createTable tableName="table">
            <column name="field" type="CLOB"/>
        </createTable>
    </changeSet>

    <!--This is only executed for H2, Postgres and SQL Server-->
    <changeSet author="author" dbms="h2,postgresql,sqlserver">
        <createTable tableName="table">
            <column name="field" type="VARCHAR(4000)"/>
        </createTable>
    </changeSet>

As we can see, we have one changeset for Oracle and one for the other database types. The dbms check will ensure the proper changeset is executed.
Each database has it's own specifics, so when creating scripts for a CorDapp, it is recommended that you test your scripts against each supported
database.