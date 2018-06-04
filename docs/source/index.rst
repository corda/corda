Welcome to Corda !
==================

`Corda <https://www.corda.net/>`_ is a blockchain-inspired open source distributed ledger platform. If you’d like a
quick introduction to distributed ledgers and how Corda is different, then watch this short video:

.. raw:: html

    <embed>
      <iframe src="https://player.vimeo.com/video/205410473" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    </embed>

Want to see Corda running? Download our demonstration application `DemoBench <https://www.corda.net/downloads/>`_ or
follow our :doc:`quickstart guide </quickstart-index>`.

If you want to start coding on Corda, then familiarise yourself with the :doc:`key concepts </key-concepts>`, then read
our :doc:`Hello, World! tutorial </hello-world-introduction>`. For the background behind Corda, read the non-technical
`introductory white paper`_ or for more detail, the `technical white paper`_.

If you have questions or comments, then get in touch on `Slack <https://slack.corda.net/>`_ or ask a question on
`Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ .

We look forward to seeing what you can do with Corda!

.. note:: You can read this site offline. Either `download the PDF`_ or download the Corda source code, run ``gradle buildDocs`` and you will have
   a copy of this site in the ``docs/build/html`` directory.

.. _`introductory white paper`: _static/corda-introductory-whitepaper.pdf
.. _`technical white paper`: _static/corda-technical-whitepaper.pdf
.. _`download the PDF`: _static/corda-developer-site.pdf

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

.. toctree::
   :caption: Operations
   :maxdepth: 2

   corda-nodes-index.rst
   corda-networks-index.rst
   azure-vm.rst
   aws-vm.rst
   loadtesting.rst

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

.. toctree::
   :caption: Participate
   :maxdepth: 2

   release-process-index.rst
   corda-repo-layout.rst
   deterministic-modules.rst
   building-the-docs.rst
   json.rst
