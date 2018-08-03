Database access when running H2
===============================

.. contents::

Configuring the username and password
-------------------------------------

The database (a file called ``persistence.mv.db``) is created when the node first starts up. By default, it has an
administrator user ``sa`` and a blank password. The node requires the user with administrator permissions in order to
creates tables upon the first startup or after deploying new CorDapps with their own tables. The database password is
required only when the H2 database is exposed on non-localhost address (which is disabled by default).

This username and password can be changed in node configuration:

 .. sourcecode:: groovy

     dataSourceProperties = {
        dataSource.user = [USER]
        dataSource.password = [PASSWORD]
    }

Note that changing the user/password for the existing node in ``node.conf`` will not update them in the H2 database.
You need to log into the database first to create a new user or change a user's password.

Connecting via a socket on a running node
-----------------------------------------

Configuring the port
^^^^^^^^^^^^^^^^^^^^

Nodes backed by an H2 database will not expose this database by default. To configure the node to expose its internal
database over a socket which can be browsed using any tool that can use JDBC drivers, you must specify the full network
address (interface and port) using the ``h2Settings`` syntax in the node configuration.

The configuration below will restrict the H2 service to run on ``localhost``:

.. sourcecode:: groovy

  h2Settings {
      address: "localhost:12345"
  }

If you want H2 to auto-select a port (mimicking the old ``h2Port`` behaviour), you can use:

.. sourcecode:: groovy

  h2Settings {
      address: "localhost:0"
  }

If remote access is required, the address can be changed to ``0.0.0.0`` to listen on all interfaces. A password must be
set for the database user before doing so.

.. sourcecode:: groovy

  h2Settings {
      address: "0.0.0.0:12345"
  }
  dataSourceProperties {
      dataSource.password : "strongpassword"
  }

.. note:: The previous ``h2Port`` syntax is now deprecated. ``h2Port`` will continue to work but the database will only
   be accessible on localhost.

Connecting to the database
^^^^^^^^^^^^^^^^^^^^^^^^^^
The JDBC URL is printed during node startup to the log and will typically look like this:

     ``jdbc:h2:tcp://localhost:31339/node``

Any database browsing tool that supports JDBC can be used.

Connecting using the H2 Console
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* Download the **last stable** `h2 platform-independent zip <http://www.h2database.com/html/download.html>`_, unzip the
  zip, and navigate in a terminal window to the unzipped folder

* Change directories to the bin folder: ``cd h2/bin``

* Run the following command to open the h2 web console in a web browser tab:

  * Unix: ``sh h2.sh``
  * Windows: ``h2.bat``

* Paste the node's JDBC URL into the JDBC URL field and click ``Connect``, using the default username (``sa``) and no
  password (unless configured otherwise)

You will be presented with a web interface that shows the contents of your node's storage and vault, and provides an
interface for you to query them using SQL.

.. _h2_relative_path:

Connecting directly to the node's ``persistence.mv.db`` file
------------------------------------------------------------

You can also use the H2 Console to connect directly to the node's ``persistence.mv.db`` file. Ensure the node is off
before doing so, as access to the database file requires exclusive access. If the node is still running, the H2 Console
will return the following error:
``Database may be already in use: null. Possible solutions: close all other connection(s); use the server mode [90020-196]``.

    ``jdbc:h2:~/path/to/file/persistence``
