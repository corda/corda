Welcome to the Corda!
=====================

.. warning:: This build of the docs is from the *master branch*, not a milestone release. It may not reflect the
   current state of the code.

This is the developer guide for Corda, a proposed architecture for distributed ledgers. Here are the sources
of documentation you may find useful, from highest level to lowest:

1. The `Introductory white paper`_ describes the motivating vision and background of the project. It is the kind
   of document your boss should read. It describes why the project exists and briefly compares it to alternative
   systems on the market.
2. This user guide. It describes *how* to use the system to write apps. It assumes you already have read the
   relevant sections of the technology white paper and now wish to learn how to use it.
3. The `API docs`_.

.. _`Introductory white paper`: _static/corda-introductory-whitepaper.pdf
.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf
.. _`API docs`: api/index.html

Read on to learn:

.. toctree::
   :maxdepth: 2
   :caption: Overview

   inthebox
   getting-set-up
   data-model
   transaction-data-types
   merkle-trees
   consensus
   messaging
   persistence
   creating-a-cordapp
   running-the-demos
   node-administration
   corda-configuration-files

.. toctree::
   :maxdepth: 2
   :caption: Tutorials

   where-to-start
   tutorial-contract
   tutorial-contract-clauses
   tutorial-test-dsl
   tutorial-clientrpc-api
   protocol-state-machines
   oracles
   tutorial-attachments
   event-scheduling

.. toctree::
   :maxdepth: 2
   :caption: Contracts

   contract-catalogue
   contract-irs
   initialmarginagreement

.. toctree::
   :maxdepth: 2
   :caption: Node API

   clientrpc

.. toctree::
   :maxdepth: 2
   :caption: Appendix

   secure-coding-guidelines
   release-process
   release-notes
   network-simulator
   node-explorer
   codestyle
   building-the-docs
