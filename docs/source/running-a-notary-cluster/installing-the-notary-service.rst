Configuring the notary worker nodes
===================================

In configuring the notary worker nodes, please note the following:

* Notice the parameters ``rewriteBatchedStatements=true&useSSL=false&failOverReadOnly=false``
  of the JDBC URL
* Put the IP address or host name of the nearest shared DB server first in the JDBC
  URL. When running a DB node and a notary worker node on a single machine, list the
  local IP first
* In addition to the connection to the shared DB holding the notary state,
  each notary worker needs to have access to its own local node DB. See the
  `dataSourceProperties` section in the configuration file
* Omit ``compatibilityZoneURL`` and set ``devMode = true`` when using the bootstrapper

The configuration below will result in the Percona notary implementation being used:

.. literalinclude:: resources/node.conf
  :caption: node.conf
  :name: node-conf

See :doc:`../corda-configuration-file` for a complete reference.

Configuration Obfuscation
+++++++++++++++++++++++++

Corda Enterprise comes with a tool for obfuscating secret values in configuration files, which is strongly recommended for production deployments.
For a notary worker node, the Percona XtraDB cluster IP addresses, database user credentials, ``keyStore`` and ``trustStore`` password fields in
the configuration file should be obfuscated. Usage instructions can be found on the :doc:`/tools-config-obfuscator` page.

Your configuration should look something like this:

.. literalinclude:: resources/config_obfuscator
   :name: config-obfuscator-notary

.. _mysql_driver:

JPA Notary
++++++++++

The configuration below will result in the JPA notary implementation being used. Note the lack of
the ``mysql`` configuration tag and the presence of the ``jpa`` configuration tag. Only the
``notary`` tag is included in this excerpt - the remainder of the configuration file does not
change.

.. literalinclude:: resources/jpa.conf
  :caption: jpa.conf
  :name: jpa-conf
