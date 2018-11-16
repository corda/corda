HA Utilities
============

Setting up multiple nodes behind shared Corda Firewall require preparation of various keystores and config files, which can be time consuming and error prone.
The HA Utilities aims to provide tools to streamline the node provision and deployment process.

The tool is distributed as part of |release| in the form of runnable JAR "|jar_name|".

.. |jar_name| replace:: corda-tools-ha-utilities-|version|.jar

To run simply pass in the file or URL as the first parameter:

.. parsed-literal::

    > java -jar |jar_name| <file or URL>

..

Use the ``--help`` flag for a full list of command line options.

Sub-commands
^^^^^^^^^^^^

``node-registration``: Corda registration tool for registering 1 or more node with the corda network, using provided node configuration.
``import-ssl-key``: Key copying tool for creating bridge SSL keystore or add new node SSL identity to existing bridge SSL keystore.
``generate-internal-ssl-keystores``: Generate self-signed root and SSL certificates for bridge, external artemis broker and float, for internal communication between the services.
``install-shell-extensions``: Install alias and autocompletion for bash and zsh. See :doc:`cli-application-shell-extensions` for more info.


Node Registration Tool
----------------------

The registration tool can be used to register multiple Corda nodes with the network operator, it is useful when managing multiple identities and setting up multiple Corda nodes sharing Corda firewall infrastructures.

Command-line options
~~~~~~~~~~~~~~~~~~~~
.. code-block:: shell

      ha-utilities node-registration [-hvV] [--logging-level=<loggingLevel>] [-b=FOLDER] -p=PASSWORD -t=FILE -f=FILE... [-f=FILE...]...

* ``-v``, ``--verbose``, ``--log-to-console``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO
* ``-b``, ``--base-directory=FOLDER``: The node working directory where all the files are kept.
* ``-f``, ``--config-files=FILE...``: The path to the config file
* ``-t``, ``--network-root-truststore=FILE``: Network root trust store obtained from network operator.
* ``-p``, ``--network-root-truststore-password=PASSWORD``: Network root trust store password obtained from network operator.
* ``-h``, ``--help``: Show this help message and exit.
* ``-V``, ``--version``: Print version information and exit.

SSL key copier
--------------

When using shared external bridge, the bridge will need to have access to nodes' SSL key in order to establish connections to counterparties on behalf of the nodes.
The SSL key copier sub command can be used to provision the SSL keystore and add additional keys when adding more nodes to the shared infrastructure.

Command-line options
~~~~~~~~~~~~~~~~~~~~
.. code-block:: shell

      ha-utilities import-ssl-key [-hvV] [--logging-level=<loggingLevel>] [-b=FOLDER] [-k=FILES] -p=PASSWORDS --node-keystore-passwords=PASSWORDS... [--node-keystore-passwords=PASSWORDS...]... --node-keystores=FILES... [--node-keystores=FILES...]...

* ``-v``, ``--verbose``, ``--log-to-console``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO
* ``--node-keystores=FILES...``: The path to the node SSL keystore(s)
* ``--node-keystore-passwords=PASSWORDS...``: The password(s) of the node SSL keystore(s)
* ``-b``, ``--base-directory=FOLDER``: The working directory where all the files are kept.
* ``-k``, ``--bridge-keystore=FILES``: The path to the bridge SSL keystore.
* ``-p``, ``--bridge-keystore-password=PASSWORDS``: The password of the bridge SSL keystore.
* ``-h``, ``--help``: Show this help message and exit.
* ``-V``, ``--version`` :Print version information and exit.


Self signed internal SSL keystore
---------------------------------

TLS is used to ensure communications between firewall components are secured. This tool can be used to generate the required keystores if TLS cert signing infrastructure is not available within your organisation.

Command-line options
~~~~~~~~~~~~~~~~~~~~
.. code-block:: shell

      ha-utilities generate-internal-ssl-keystores [-hvV] [--logging-level=<loggingLevel>] [-b=<baseDirectory>] [-c=<country>] [-l=<locality>] [-o=<organization>] [-p=<password>]

* ``-v``, ``--verbose``, ``--log-to-console``: If set, prints logging to the console as well as to a file.
* ``--logging-level=<loggingLevel>``: Enable logging at this level and higher. Possible values:ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO
* ``-p``, ``--password=<password>``: Default password for all generated keystore and private keys. Default: changeit
* ``-o``, ``--organization=<organization>``: X500Name's organization attribute. Default: Corda
* ``-l``, ``--locality=<locality>``: X500Name's locality attribute. Default: London
* ``-c``, ``--county=<country>``: X500Name's country attribute. Default: GB
* ``-b``, ``--base-directory=<baseDirectory>``: The node working directory where all the files are kept.
* ``-h``, ``--help``: Show this help message and exit.
* ``-V``, ``--version``: Print version information and exit.

