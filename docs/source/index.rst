Welcome to Corda !
==================

.. only:: html

   `Corda <https://www.corda.net/>`_ is an open-source blockchain platform. If you’d like a quick introduction to blockchains and how Corda is different, then watch this short video:

   .. raw:: html

       <embed>
         <iframe src="https://player.vimeo.com/video/205410473" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
       </embed>

   If you want to start coding on Corda, then familiarise yourself with the :doc:`key concepts </key-concepts>`, then read
   our :doc:`Hello, World! tutorial </hello-world-introduction>`.

   * For the background behind Corda read the `introductory white paper`_ (`Simplified Chinese`_, `Traditional Chinese`_, `Japanese`_).
   * For much more architectural detail, the `technical white paper`_.

   If you have questions or comments, then get in touch on `Slack <https://slack.corda.net/>`_ or ask a question on
   `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ .

   We look forward to seeing what you can do with Corda!

   .. note:: You can read this site offline. Either `download the PDF`_ or download the Corda source code, run ``gradle buildDocs`` and you will have
      a copy of this site in the ``docs/build/html`` directory.

   .. _`introductory white paper`: _static/corda-introductory-whitepaper.pdf
   .. _`Simplified Chinese`: _static/corda-introductory-whitepaper-zhs.pdf
   .. _`Traditional Chinese`: _static/corda-introductory-whitepaper-zht.pdf
   .. _`Japanese`: _static/corda-introductory-whitepaper-jp.pdf
   .. _`technical white paper`: _static/corda-technical-whitepaper.pdf
   .. _`download the PDF`: _static/corda-developer-site.pdf
   
.. only:: latex

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

   release-notes
   app-upgrade-notes
   node-upgrade-notes
   corda-api
   cheat-sheet

.. toctree::
   :caption: Development
   :maxdepth: 1
   :hidden:
   :titlesonly:

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

.. toctree::
   :caption: Operations
   :maxdepth: 2
   :hidden:
   :titlesonly:

   corda-nodes-index.rst
   corda-networks-index.rst
   docker-image.rst
   azure-vm.rst
   aws-vm.rst
   loadtesting.rst
   cli-application-shell-extensions.rst

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
   release-notes.rst
   changelog.rst

.. conditional-toctree::
   :caption: Participate
   :maxdepth: 2
   :if_tag: htmlmode
   :hidden:
   :titlesonly:

   contributing-index.rst
   deterministic-modules.rst
   design/design-docs-index.rst
   changelog
