Corda Enterprise 3.0
====================

Welcome to the documentation website for **Corda Enterprise 3.0**, based on the Corda 3.1 open source release. Corda Enterprise adds:

* High performance, thanks to multi-threaded flow execution and extensive tuning.
* Support for more database backends:

   * SQL Server 2017
   * Azure SQL
   * Oracle 11g RC2
   * Oracle 12c
   * PostgreSQL 9.6

* The Corda Firewall, for termination of TLS connections within your network's DMZ.
* High availability features to support node-to-node failover.
* Support for advanced database migrations.

You can learn more in the :doc:`release-notes`.

Corda Enterprise is binary compatible with apps developed for the open source node. This docsite is intended for
administrators and advanced users who wish to learn how to install and configure an enterprise deployment. For
application development please continue to refer to `the main project documentation website <https://docs.corda.net/>`_.

------------

.. toctree::
   :caption: Corda Enterprise
   :maxdepth: 1

   hot-cold-deployment
   database-migration
   corda-firewall

.. toctree::
   :caption: Development
   :maxdepth: 1

   quickstart-index.rst
   key-concepts.rst
   building-a-cordapp-index.rst
   tutorials-index.rst
   tools-index.rst
   node-internals-index.rst
   component-library-index.rst
   troubleshooting.rst
   json.rst

.. toctree::
   :caption: Operations
   :maxdepth: 2

   corda-nodes-index.rst
   corda-networks-index.rst
   azure-vm.rst
   aws-vm.rst
   loadtesting.rst
   certificate-revocation

.. toctree::
   :caption: Design docs
   :maxdepth: 2

   design/design-review-process.md
   design/certificate-hierarchies/design.md
   design/failure-detection-master-election/design.md
   design/float/design.md
   design/hadr/design.md
   design/kafka-notary/design.md
   design/monitoring-management/design.md
   design/sgx-integration/design.md

.. toctree::
   :caption: Participate
   :maxdepth: 2

   release-process-index.rst
   corda-repo-layout.rst
   deterministic-modules.rst
   building-the-docs.rst
