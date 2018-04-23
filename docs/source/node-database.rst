Node database
=============

Default in-memory database
--------------------------
By default, nodes store their data in an H2 database. You can connect directly to a running node's database to see its
stored states, transactions and attachments as follows:

* Download the `h2 platform-independent zip <http://www.h2database.com/html/download.html>`_, unzip the zip, and
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

Standalone database
-------------------

To run a node against a remote database modify node JDBC connection properties in `dataSourceProperties` entry
and Hibernate properties in `database` entry - see :ref:`Node configuration <database_properties_ref>`.

The Corda distribution does not include any JDBC drivers with the exception of the H2 driver used by samples.
It is the responsibility of the node administrator to download the appropriate JDBC drivers and configure the database settings.
Corda will search for valid JDBC drivers either under the ``./drivers`` subdirectory of the node base directory or in one
of the paths specified by the ``jarDirs`` field of the node configuration. Please make sure a ``jar`` file containing drivers
supporting the database in use is present in one of these locations.

SQL Azure and SQL Server
````````````````````````
Corda has been tested with SQL Server 2017 (14.0.3006.16) and Azure SQL (12.0.2000.8), using Microsoft JDBC Driver 6.2.

Example node configuration for SQL Azure:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
        dataSource.url = "jdbc:sqlserver://[DATABASE_SERVER].database.windows.net:1433;databaseName=[DATABASE];
            encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }
    jarDirs = [PATH_TO_JDBC_DRIVER_DIR]

Note that:

* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`
* The JDBC driver can be downloaded from `Microsoft Download Center <https://www.microsoft.com/en-us/download/details.aspx?id=55539>`_,
  extract the archive and ensure the directory ``jarDirs`` contains only one driver `mssql-jdbc-6.2.2.jre8.jar` as the archive comes with two jar versions

Oracle
````````````````````````
Corda supports Oracle 11g RC2 (with ojdbc6.jar) and Oracle 12c (ojdbc8.jar).

Example node configuration for Oracle:

.. sourcecode:: none

    dataSourceProperties = {
        dataSourceClassName = "oracle.jdbc.pool.OracleDataSource"
        dataSource.url = "jdbc:oracle:thin:@[IP]:[PORT]:xe"
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }
    jarDirs = [PATH_TO_JDBC_DRIVER_DIR]

Note that:

* The ``database.schema`` property is optional
* The minimum transaction isolation level ``database.transactionIsolationLevel`` is `READ_COMMITTED`

.. _postgres_ref:

PostgreSQL
````````````````````````
Corda has been tested on PostgreSQL 9.6 database, using PostgreSQL JDBC Driver 42.1.4.

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
    jarDirs = [PATH_TO_JDBC_DRIVER_DIR]

Note that:

* The ``database.schema`` property is optional
* The value of ``database.schema`` is automatically wrapped in double quotes to preserve case-sensitivity
  (e.g. `AliceCorp` becomes `"AliceCorp"`, without quotes PostgresSQL would treat the value as `alicecorp`),
  this behaviour differs from Corda Open Source where the value is not wrapped in double quotes
