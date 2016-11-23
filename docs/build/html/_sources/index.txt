Welcome to the Corda!
=====================

.. warning:: This build of the docs is from the *master branch*, not a milestone release. It may not reflect the
   current state of the code.

This is the developer guide for Corda, a proposed architecture for distributed ledgers. Here are the sources
of documentation you may find useful, from highest level to lowest:

1. The `Introductory white paper`_ describes the motivating vision and background of the project. It is the kind
   of document your boss should read. It describes why the project exists and briefly compares it to alternative
   systems on the market.
2. The `Technical white paper`_ describes the entire intended design from beginning to end. It is the kind of
   document that you should read, or at least, read parts of. Note that because the technical white paper
   describes the intended end state, it does not always align with the implementation.
3. This user guide. It describes *how* to use the system to write apps, as currently implemented. It assumes
   you already have read the relevant sections of the technology white paper and now wish to learn how to use it.
4. The `API docs`_.

.. _`Introductory white paper`: _static/corda-introductory-whitepaper.pdf
.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf
.. _`API docs`: api/index.html

Read on to learn:

.. toctree::
   :maxdepth: 2
   :caption: Getting started

   inthebox
   getting-set-up
   running-the-demos

.. toctree::
   :maxdepth: 2
   :caption: Key concepts

   data-model
   transaction-data-types
   merkle-trees
   consensus

.. toctree::
   :maxdepth: 2
   :caption: The Corda node

   clientrpc
   messaging
   persistence
   node-administration
   corda-configuration-files
   node-services

.. toctree::
   :maxdepth: 2
   :caption: CorDapps

   creating-a-cordapp

.. toctree::
   :maxdepth: 2
   :caption: Tutorials

   where-to-start
   tutorial-contract
   tutorial-contract-clauses
   tutorial-test-dsl
   tutorial-clientrpc-api
   flow-state-machines
   oracles
   tutorial-attachments
   event-scheduling

.. toctree::
   :maxdepth: 2
   :caption: Other

   network-simulator
   node-explorer
   initial-margin-agreement

.. toctree::
   :maxdepth: 2
   :caption: Component library

   contract-catalogue
   contract-irs

.. toctree::
   :maxdepth: 2
   :caption: Appendix

   loadtesting
   secure-coding-guidelines
   release-process
   release-notes
   codestyle
   building-the-docs

.. toctree::
   :maxdepth: 2
   :caption: Glossary

   glossary
