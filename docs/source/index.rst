Welcome to the Corda documentation!
===================================

.. warning:: This build of the docs is from the "|version|" branch, not a milestone release. It may not reflect the
   current state of the code. `Read the docs for milestone release M7 <https://docs.corda.net/releases/release-M7.0/>`_.

`Corda <https://www.corda.net/>`_ is an open-source distributed ledger platform. The latest *milestone* (i.e. stable) 
release is M7. The codebase is on `GitHub <https://github.com/corda>`_, and our community can be found on 
`Slack <https://slack.corda.net/>`_ and in our `forum <https://discourse.corda.net/>`_.

If you're new to Corda, you should start by learning about its motivating vision and architecture. A good introduction 
is the `Introduction to Corda webinar <https://vimeo.com/192757743/c2ec39c1e1>`_ and the `Introductory white paper`_. As 
they become more familiar with Corda, readers with a technical background will also want to dive into the `Technical white paper`_, 
which describes the platform's envisioned end-state.

Corda is designed so that developers can easily extend its functionality by writing CorDapps 
(**Cor**\ da **D**\ istributed **App**\ lication\ **s**\ ). An example CorDapp is available on 
`Github <https://github.com/corda/cordapp-template>`_. To get it running, follow the instructions in the 
`readme <https://github.com/corda/cordapp-template/blob/master/README.md>`_, or watch the 
`Corda Developers Tutorial <https://vimeo.com/192797322/aab499b152>`_.

Additional CorDapp samples are available in the Corda repo's `samples <https://github.com/corda/corda/tree/master/samples>`_ 
directory. These are sophisticated CorDapps that implement more complex functionality. You can find directions for 
running these samples `here <https://docs.corda.net/running-the-demos.html>`_. 

From there, you'll be in a position to start extending the example CorDapp yourself (e.g. by writing new states, contracts, 
and/or flows). For this, you'll want to refer to this docsite, and to the `tutorials <https://docs.corda.net/#tutorials>`_ 
in particular. If you get stuck, get in touch on `Slack <https://slack.corda.net/>`_ or the `forum <https://discourse.corda.net/>`_.

Once you're familiar with Corda and CorDapp development, we'd encourage you to get involved in the development of the 
platform itself. Find out more about `contributing to Corda <https://github.com/corda/corda/wiki/Corda's-Open-Source-Approach>`_.

.. _`Introductory white paper`: _static/corda-introductory-whitepaper.pdf
.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf

Documentation Contents:
=======================

.. toctree::
   :maxdepth: 2
   :caption: Getting started

   inthebox
   getting-set-up
   getting-set-up-fault-finding
   running-the-demos
   CLI-vs-IDE

.. toctree::
   :maxdepth: 2
   :caption: Key concepts

   data-model
   transaction-data-types
   merkle-trees
   consensus
   clauses

.. toctree::
   :maxdepth: 2
   :caption: CorDapps

   creating-a-cordapp
   tutorial-cordapp

.. toctree::
   :maxdepth: 2
   :caption: The Corda node

   clientrpc
   messaging
   persistence
   node-administration
   corda-configuration-file
   corda-plugins
   node-services
   node-explorer
   permissioning

.. toctree::
   :maxdepth: 2
   :caption: Tutorials

   tutorial-contract
   tutorial-contract-clauses
   tutorial-test-dsl
   tutorial-integration-testing
   tutorial-clientrpc-api
   tutorial-building-transactions
   flow-state-machines
   flow-testing
   running-a-notary
   using-a-notary
   oracles
   tutorial-attachments
   event-scheduling

.. toctree::
   :maxdepth: 2
   :caption: Other

   network-simulator

.. toctree::
   :maxdepth: 2
   :caption: Component library

   contract-catalogue
   contract-irs

.. toctree::
   :maxdepth: 2
   :caption: Appendix

   loadtesting
   setting-up-a-corda-network
   secure-coding-guidelines
   release-process
   release-notes
   codestyle
   building-the-docs
   publishing-corda
   azure-vm

.. toctree::
   :maxdepth: 2
   :caption: Glossary

   glossary
