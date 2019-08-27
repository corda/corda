|release|
=========

Welcome to the documentation website for |release|, based on the Corda 4.0 open source release.

|release| builds on the performance, scalability, high-availability, enhanced DMZ security, and multiple database vendor support
introduced in Corda Enterprise 3.0 with the following important new additions:

* **Multiple nodes behind a single firewall**:
  multi-tenancy of Corda Firewall (float and bridge) components enables multiple Corda nodes to multiplex all remote peer-to-peer message traffic
  through a single Corda Firewall.

* **Hardware Security Module (HSM) support**:
  for Node CA and Legal Identity signing keys in hardware security modules provides increased security.
  This release includes full integration with Azure Key Vault, Gemalto Luna and Utimaco HSM devices.

* **High Availability improvements**:
  this release builds on the Hot-Cold High Availability configuration available in Corda Enterprise 3.x with improved deployment
  configurations to simplify operational management and reduce overall VM footprint.

* **Operational Deployment improvements**:
  introduces improvements that optimize larger scale deployments, reduce the cost of infrastructure, and minimize the operational complexity
  of multi-node hosting.

* **Performance Test Suite for benchmarking**:
  a toolkit to allow customers to test and validate Corda for their infrastructure performance and determine whether or not improvements are needed
  before going live.

|release| also includes the new features of Corda 4, notably:

   * **Reference input states**:
  these allow smart contracts to read data from the ledger without simultaneously updating it.

* **State pointers**:
  these work together with the reference states feature to make it easy for data to point to the latest version of any other piece of data
  on the ledger by ``StateRef`` or linear ID.

* **Signature constraints**:
  facilitate upgrading CorDapps in a secure manner using standard public key signing mechanisms and controls.

* **Security upgrades** to include:

  - Sealed JARs are a security upgrade that ensures JARs cannot define classes in each other's packages, thus ensuring Java's package-private
    visibility feature works.

  - ``@BelongsToContract`` annotation: allows annotating states with which contract governs them.

  - Two-sided ``FinalityFlow`` and ``SwapIdentitiesFlow`` to prevent nodes accepting any finalised transaction, outside of the context of a containing flow.

  - Package namespace ownership: allows app developers to register their keys and Java package namespaces
    with the zone operator. Any JAR that defines classes in these namespaces will have to be signed by those keys.

* **Versioning**:
  applications can now specify a **target version** in their JAR manifest that declares which version of the platform the app was tested against.
  They can also specify a **minimum platform version** which specifies the minimum version a node must be running on
  to allow the app to start using new features and APIs of that version.

You can learn more about all new features in the :doc:`Enterprise <release-notes-enterprise>` and :doc:`Open Source <release-notes>` release notes.

.. only:: htmlmode

   .. note:: You can read this site offline by `downloading the PDF`_.

   .. _`downloading the PDF`: _static/corda-developer-site.pdf

Corda Enterprise is binary compatible with apps developed for the open source node. This docsite is intended for
administrators and advanced users who wish to learn how to install and configure an enterprise deployment. For
application development please continue to refer to `the main project documentation website <https://docs.corda.net/>`_.

.. note:: Corda Enterprise provides platform API version 4, which matches the API available in open source Corda 4.x releases.

.. Documentation is not included in the pdf unless it is included in a toctree somewhere

   Welcome to Corda, a platform for building decentralized applications. This guidebook covers everything you need to know to create
   apps, run nodes and networks, and operate your new decentralized business network.

   If you're completely new to distributed ledger technology (DLT) or Corda and would like a business-oriented overview, we recommend
   reading the introductory white paper. If you'd like a detailed architectural description of how the Corda protocol works, why
   it's designed how it is and what future work is planned, we recommend reading the technical white paper. Both white papers can be
   found on `the Corda documentation website`_.

   But if you'd like to dive in and start writing apps, or running nodes, this guidebook is for you. It covers the open source Corda
   distribution. Commercial distributions (like Corda Enterprise from R3) have their own user guides that describe their enhanced features.

   We look forward to seeing what you can do with Corda!

   .. _`the Corda documentation website`: https://docs.corda.net

.. toctree::
    :maxdepth: 1
    :hidden:
    :titlesonly:
    :if_tag: htmlmode

   release-notes-enterprise.rst
   app-upgrade-notes-enterprise.rst
   node-upgrade-notes.rst
   corda-api.rst
   version-compatibility.rst
   platform-support-matrix.rst
   cheat-sheet.rst

.. conditional-toctree::
   :caption: Contents
   :maxdepth: 2
   :if_tag: pdfmode

   release-notes-enterprise.rst
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
   :hidden:
   :titlesonly:
   :caption: Corda Enterprise
   :maxdepth: 1
   :if_tag: htmlmode

   hot-cold-deployment
   node-database-intro
   cryptoservice-configuration
   corda-firewall
   sizing-and-performance
   running-a-notary-cluster/toctree
   performance-testing/toc-tree.rst
   tools-index-enterprise.rst

.. conditional-toctree::
   :hidden:
   :titlesonly:
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
   versioning-and-upgrades.rst
   cordapp-advanced-concepts.rst
   troubleshooting.rst

.. conditional-toctree::
   :hidden:
   :titlesonly:
   :caption: Operations
   :maxdepth: 2
   :if_tag: htmlmode

   corda-nodes-index.rst
   corda-networks-index.rst
   docker-image.rst
   node-cloud.rst
   loadtesting.rst
   cli-application-shell-extensions.rst
   certificate-revocation

.. Documentation is not included in the pdf unless it is included in a toctree somewhere

.. conditional-toctree::
   :caption: Corda Network
   :maxdepth: 2
   :if_tag: htmlmode
   :hidden:
   :titlesonly:

   corda-network/index.md
   corda-network/UAT.md

.. conditional-toctree::
   :caption: Contents
   :maxdepth: 2
   :if_tag: pdfmode

   deterministic-modules.rst
   release-notes-enterprise.rst
   changelog-enterprise.rst
