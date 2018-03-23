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

PostgreSQL
----------
Nodes also have untested support for PostgreSQL 9.6, using PostgreSQL JDBC Driver 42.1.4.

.. warning:: This is an experimental community contribution, and is currently untested. We welcome pull requests to add
   tests and additional support for this feature.

Configuration
~~~~~~~~~~~~~
Here is an example node configuration for PostgreSQL:

.. sourcecode:: groovy

    dataSourceProperties = {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        "dataSource.url" = "jdbc:postgresql://[HOST]:[PORT]/postgres"
        "dataSource.user" = [USER]
        "dataSource.password" = [PASSWORD]
    }
    database = {
        transactionIsolationLevel = READ_COMMITTED
        schema = [SCHEMA]
    }

Note that:

* The ``database.schema`` property is optional
* The value of ``database.schema`` is not wrapped in double quotes and Postgres always treats it as a lower-case value
  (e.g. ``AliceCorp`` becomes ``alicecorp``)
