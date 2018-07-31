Database access when running H2
===============================

Via a socket on a running node
------------------------------
When running a node using the H2 database, the node can be configured to expose its internal database over socket which
can be browsed using any tool that can use JDBC drivers. The JDBC URL is printed during node startup to the log and
will typically look like this:

     ``jdbc:h2:tcp://localhost:31339/node``

The username and password can be altered in the :doc:`corda-configuration-file` but default to username "sa" and a blank
password.

Any database browsing tool that supports JDBC can be used, but if you have IntelliJ Ultimate edition then there is
a tool integrated with your IDE. Just open the database window and add an H2 data source with the above details.
You will now be able to browse the tables and row data within them.

By default, the node's H2 database is not exposed. This behaviour can be overridden by specifying the full network 
address (interface and port), using the new ``h2Settings`` syntax in the node configuration.

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

The previous ``h2Port`` syntax is now deprecated. ``h2Port`` will continue to work but the database
will only be accessible on localhost.

.. _h2_relative_path:

Connecting directly to the node's ``persistence.mv.db`` file
------------------------------------------------------------

TODO

.. _h2_upgrade_script:

Upgrade scripts
---------------

TODO