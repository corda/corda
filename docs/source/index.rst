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

.. only:: htmlmode

   .. note:: You can read this site offline by `downloading the PDF`_.

   .. _`downloading the PDF`: _static/corda-developer-site.pdf

Corda Enterprise is binary compatible with apps developed for the open source node. This docsite is intended for
administrators and advanced users who wish to learn how to install and configure an enterprise deployment. For
application development please continue to refer to `the main project documentation website <https://docs.corda.net/>`_.

.. Documentation is not included in the pdf unless it is included in a toctree somewhere

.. conditional-toctree::
   :caption: Contents
   :maxdepth: 2
   :if_tag: pdfmode

   release-notes.rst
   key-concepts.rst
   quickstart-index.rst
   tutorials-index.rst
   building-a-cordapp-index.rst
   component-library-index.rst
   corda-nodes-index.rst
   corda-networks-index.rst
   tools-index.rst
   corda-firewall
   database-management
   hot-cold-deployment
   running-a-notary-cluster/toctree
   certificate-revocation
   node-internals-index.rst
   json.rst
   troubleshooting.rst

.. conditional-toctree::
   :caption: Corda Enterprise
   :maxdepth: 1
   :if_tag: htmlmode

   release-notes.rst
   version-compatibility.rst
   platform-support-matrix.rst
   hot-cold-deployment
   database-management
   corda-firewall
   sizing-and-performance
   running-a-notary-cluster/toctree

.. conditional-toctree::
   :caption: Development
   :maxdepth: 1
   :if_tag: htmlmode

   quickstart-index.rst
   key-concepts.rst
   building-a-cordapp-index.rst
   tutorials-index.rst
   tools-index.rst
   node-internals-index.rst
   component-library-index.rst
   serialization-index.rst
   json.rst
   troubleshooting.rst

.. conditional-toctree::
   :caption: Operations
   :maxdepth: 2
   :if_tag: htmlmode

   corda-nodes-index.rst
   corda-networks-index.rst
   certificate-revocation
