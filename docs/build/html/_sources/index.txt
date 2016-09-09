Welcome to the Corda repository!
================================

.. warning:: This build of the docs is from the *master branch*, not a milestone release. It may not reflect the
   current state of the code.

This documentation describes Corda, a proposed architecture for distributed ledgers, the vision for which is outlined in the `Corda Introductory Whitepaper`_.

.. _`Corda Introductory Whitepaper`: _static/corda-introductory-whitepaper.pdf

The goal of this prototype is to explore fundamentally better designs for distributed ledgers than what presently exists
on the market, tailor made for the needs of the financial industry. We are attempting to prove or disprove the
following hypothesis:

The combination of

* An upgraded state transition model
* Industry standard, production quality virtual machines and languages
* An advanced orchestration framework
* Limited data propagation
* Conflict resolution without proof of work or blocks

is sufficiently powerful to justify the creation of a new platform implementation.

Read on to learn:

.. toctree::
   :maxdepth: 2
   :caption: Overview

   inthebox
   getting-set-up
   data-model
   transaction-data-types
   consensus
   messaging
   creating-a-cordapp
   running-the-demos
   node-administration
   corda-configuration-files

.. toctree::
   :maxdepth: 2
   :caption: Contracts

   contract-catalogue
   contract-irs

.. toctree::
   :maxdepth: 2
   :caption: Tutorials

   where-to-start
   tutorial-contract
   tutorial-contract-clauses
   tutorial-test-dsl
   protocol-state-machines
   oracles
   event-scheduling

.. toctree::
   :maxdepth: 2
   :caption: Appendix

   release-process
   release-notes
   visualiser
   codestyle
   building-the-docs

