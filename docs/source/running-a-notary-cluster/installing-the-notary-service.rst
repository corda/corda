=============================
Setting up the Notary Service
=============================

In the previous section of this tutorial we set up a Percona cluster.

On top of the Percona XtraDB Cluster we're deploying three notary worker nodes ``notary-{1,2,3}`` and
a single regular Corda node ``node-1`` that runs the notary health-check CorDapp.

If you're deploying VMs in your environment you might need to adjust the host names accordingly.

Configuration Files
+++++++++++++++++++

Below is a template for the notary configuration. Notice the parameters
``rewriteBatchedStatements=true&useSSL=false&failOverReadOnly=false`` of the
JDBC URL.  See :doc:`../corda-configuration-file` for a complete reference.

Put the IP address or host name of the nearest Percona server first in the JDBC
URL. When running a Percona and a Notary replica on a single machine, list the
local IP first.

In addition to the connection to the shared Percona DB holding the notary state,
each notary worker needs to have access to its own local node DB. See the
`dataSourceProperties` section in the configuration file.

.. literalinclude:: resources/node.conf
  :caption: node.conf
  :name: node-conf

.. note::

  Omit ``compatibilityZoneURL`` and set ``devMode = true`` when using the bootstrapper.

Configuration Obfuscation
+++++++++++++++++++++++++

Corda Enterprise comes with a tool for obfuscating secret values in configuration files, which is strongly recommended for production deployments.
For a notary worker node, the Percona XtraDB cluster IP addresses, database user credentials, ``keyStore`` and ``trustStore`` password fields in
the configuration file should be obfuscated. Usage instructions can be found on the :doc:`/tools-config-obfuscator` page.

Your configuration should look something like this:

.. literalinclude:: resources/config_obfuscator
   :name: config-obfuscator-notary

.. _mysql_driver:

MySQL JDBC Driver
+++++++++++++++++

Each worker node requires a MySQL JDBC driver to be placed in the ``drivers`` directory to be able to communicate with the Percona XtraDB Cluster.
Version 6.0.6 of the MySQL JDBC Type 4 driver, also known as mysql-connector-java, is supported by Corda Enterprise. The official driver can be obtained from `Maven <https://search.maven.org/artifact/mysql/mysql-connector-java/6.0.6/jar>`_.

Next Steps
++++++++++

.. toctree::
  :maxdepth: 1

  installing-the-notary-service-bootstrapper
  installing-the-notary-service-netman
