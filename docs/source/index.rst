Welcome to the Corda documentation!
===================================

.. warning:: This build of the docs is from the "|version|" branch, not a milestone release. It may not reflect the
   current state of the code. `Read the docs for milestone release M11.1 <https://docs.corda.net/releases/release-M11.1/>`_.

`Corda <https://www.corda.net/>`_ is an open-source distributed ledger platform. The latest *milestone* (i.e. stable)
release is M11.1. The codebase is on `GitHub <https://github.com/corda>`_, and our community can be found on
`Slack <https://slack.corda.net/>`_ and in our `forum <https://discourse.corda.net/>`_.

If you're new to Corda, you should start by learning about its motivating vision and architecture. A good introduction
is the `Introduction to Corda webinar <https://vimeo.com/192757743/c2ec39c1e1>`_ and the `Introductory white paper`_. As
you become more familiar with Corda, readers with a technical background will also want to dive into the `Technical white paper`_,
which describes the platform's envisioned end-state.

.. note:: Corda training is now available in London, New York and Singapore! `Learn more. <https://www.corda.net/corda-training/>`_

Corda is designed so that developers can easily extend its functionality by writing CorDapps
(**Cor**\ da **D**\ istributed **App**\ lication\ **s**\ ). Some example CorDapps are available in the Corda repo's
`samples <https://github.com/corda/corda/tree/master/samples>`_ directory. To run these yourself, make
sure you follow the instructions in :doc:`getting-set-up`, then go to
:doc:`running-the-demos`.

If, after running the demos, you're interested in writing your own CorDapps, you can use the 
`CorDapp template <https://github.com/corda/cordapp-template>`_ as a base. A simple example CorDapp built upon the template is available `here <https://github.com/corda/cordapp-tutorial>`_, and a video primer on basic CorDapp structure is available `here <https://vimeo.com/192797322/aab499b152>`_.

From there, you'll be in a position to start extending the example CorDapp yourself (e.g. by writing new states, contracts,
and/or flows). For this, you'll want to refer to this docsite, and to the `tutorials <https://docs.corda.net/tutorial-contract.html>`_
in particular. If you get stuck, get in touch on `Slack <https://slack.corda.net/>`_ or the `forum <https://discourse.corda.net/>`_.

Once you're familiar with Corda and CorDapp development, we'd encourage you to get involved in the development of the
platform itself. Find out more about `contributing to Corda <https://github.com/corda/corda/wiki/Corda's-Open-Source-Approach>`_.

.. _`Introductory white paper`: _static/corda-introductory-whitepaper.pdf
.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf

Documentation Contents:
=======================

.. toctree::
   :maxdepth: 2

   quickstart-index.rst
   key-concepts.rst
   building-a-cordapp-index.rst
   corda-nodes-index.rst
   corda-networks-index.rst
   tutorials-index.rst
   tools-index.rst
   node-internals-index.rst
   component-library-index.rst
   release-process-index.rst
   faq.rst
   troubleshooting.rst
   other-index.rst
   glossary.rst