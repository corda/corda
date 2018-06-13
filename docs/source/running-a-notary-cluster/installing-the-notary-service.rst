=============================
Setting up the Notary Service
=============================

In the previous section of this tutorial we set up a Percona cluster.

On top of the Percona cluster we're deploying three Corda notary nodes ``notary-{1,2,3}`` and
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

.. literalinclude:: resources/node.conf
  :caption: node.conf
  :name: node-conf

.. note::

  Omit ``compatibilityZoneURL`` and set ``devMode = true`` when using the bootstrapper.


Next Steps
++++++++++

.. toctree::
  :maxdepth: 1

  installing-the-notary-service-bootstrapper
  installing-the-notary-service-netman
