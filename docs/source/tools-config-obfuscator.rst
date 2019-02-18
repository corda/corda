Configuration Obfuscator
========================

Corda ships with a command-line tool for obfuscating configuration files. An obfuscated file is usable
by a Corda node running on a machine with a hardware address matching the one used as input in the obfuscation.

.. note::

    The purpose of this tool is to obfuscate sensitive information in configuration files and thus make a
    node installation less vulnerable to someone trawling plain text files searching for passwords and
    credentials of resources that they should not have access to in the first place.


.. warning::

    This feature will not make password protection completely secure. However, it will protect the node
    against trawling attacks.


Using the command-line tool
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The command-line tool is included as a JAR file, named ``corda-tools-config-obfuscator-<version>.jar``.
This tool takes as input the configuration file that we want to obfuscate, denoted ``CONFIG_FILE`` in
the usage screen. If we want to override the hardware address that is used for processing, we can do so
by passing in the one to use in a hexadecimal format, where each byte is delimited by a colon, *e.g.*,
``01:02:03:04:05:06``. This is helpful if we want to prepare a configuration file that will be used on
a production server and to which we know the hardware address.

Note also that, by default, the tool only prints out the transformed configuration to the terminal for
verification. To persist the changes, we need to use the ``-w`` flag, which ensures that the obfuscated
content gets written back into the provided configuration file.


Configuration directives
~~~~~~~~~~~~~~~~~~~~~~~~

To indicate parts of the configuration that should be obfuscated, we can place text markers on the form
``<encrypt{...}>``, like so:

.. code:: json

    {
      // (...)
      "p2pAddress": "somehost.com:10001",
      "keyStorePassword": "<encrypt{testpassword}>",
      // (...)
    }

Which, after having been run through the obfuscation tool, would yield something like:

.. code:: json

    {
      // (...)
      "p2pAddress": "somehost.com:10001",
      "keyStorePassword": "<{8I1E8FKrBxVkRpZGZKAxKg==:oQqmyYO+SZJhRkPB7laNyQ==}>",
      // (...)
    }

When run by a Corda node on a machine with the matching hardware address, the configuration would be
deobfuscated on the fly and interpreted like:

.. code:: json

    {
      // (...)
      "p2pAddress": "somehost.com:10001",
      "keyStorePassword": "testpassword",
      // (...)
    }

These directives can be placed arbitrarily within string properties in the configuration file, with a maximum of one per line.
For instance:

.. code:: json

    {
      // (...)
      "dataSourceProperties" : {
        "dataSource" : {
          "url" : "jdbc:h2:file:persistence;<encrypt{sensitive-options-go-here}>",
          "user" : "<encrypt{your-database-username}>",
          "password" : "<encrypt{your-secret-database-password}>"
        },
        "dataSourceClassName" : "org.h2.jdbcx.JdbcDataSource"
      },
      // (...)
    }

.. note::

    The ``<encrypt{}>`` blocks can only appear inside string properties. Otherwise, the node will not be able
    to decipher the obfuscated content. More explicitly, this means that the blocks can only appear on the right
    hand-side of the colon, and for string properties only.
