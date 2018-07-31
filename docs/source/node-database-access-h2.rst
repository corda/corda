Database access when running H2
===============================

.. contents::

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

If remote access is required, the address can be changed to ``0.0.0.0``. However it is recommended to change the default username and password before doing so.

.. sourcecode:: groovy

  h2Settings {
      address: "0.0.0.0:12345"
  }

.. note:: The previous ``h2Port`` syntax is now deprecated. ``h2Port`` will continue to work but the database will only
   be accessible on localhost.

Configuring the JDBC URL, username and password
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The JDBC URL defaults to a random value. The username defaults to ``sa`` and the password defaults to a blank password.

These defaults can be modified in the :doc:`corda-configuration-file` using the ``dataSourceProperties`` configuration
block.

Access
^^^^^^
The JDBC URL is printed during node startup to the log and will typically look like this:

     ``jdbc:h2:tcp://localhost:31339/node``

Any database browsing tool that supports JDBC can be used. Below are two examples.

IntelliJ Ultimate edition
~~~~~~~~~~~~~~~~~~~~~~~~~

IntelliJ Ultimate edition has an integrated tool. Just open the database window and add an H2 data source with the
JDBC URL above. You will now be able to browse the tables and row data within them.

H2 Console
~~~~~~~~~~

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

You can also use the H2 Console to connect directly to the node's ``persistence.mv.db`` file:

    ``jdbc:h2:~/path/to/file/persistence``