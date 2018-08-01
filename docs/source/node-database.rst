Node database
=============

Default in-memory database
--------------------------
By default, nodes store their data in an H2 database.
The database (a file persistence.mv.db) is created at the first node startup with the administrator user 'sa' and a blank password.
The user name and password can be changed in node configuration:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }

Note, changing user/password for the existing node in node.conf will not update them in the H2 database,
you need to login to the database first to create new user or change the user password.
The database password is required only when the H2 database is exposed on non-localhost address (which is disabled by default).
The node requires the user with administrator permissions in order to creates tables upon the first startup
or after deplying new CorDapps with own tables.

You can connect directly to a running node's database to see its
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
or simply using ``0.0.0.0:0`` to listen on all interfaces. The node requires a database password to be set when
the database is exposed on the network interface to listen on.

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

SQLServer
----------
Nodes also have untested support for Microsoft SQL Server 2017, using Microsoft JDBC Driver 6.2 for SQL Server.

.. warning:: This is an experimental community contribution, and is currently untested. We welcome pull requests to add
   tests and additional support for this feature.

Configuration
~~~~~~~~~~~~~
Here is an example node configuration for SQLServer:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://[HOST]:[PORT];databaseName=[DATABASE_NAME]"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }
    jarDirs = ["[FULL_PATH]/sqljdbc_6.2/enu/"]

Note that:

* The ``database.schema`` property is optional and is ignored as of release 3.1.
* Ensure the directory referenced by jarDirs contains only one JDBC driver JAR file; by the default,
  sqljdbc_6.2/enu/contains two JDBC JAR file for different Java versions.
=======

