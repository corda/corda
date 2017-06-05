Overview
========

This section describes the key concepts and features of the Corda platform. It is intended for readers who are new to
Corda, and want to understand its architecture. It does not contain any code, and is suitable for non-developers.

This section should be read in order:

    * :doc:`key-concepts-ecosystem`
    * :doc:`key-concepts-ledger`
    * :doc:`key-concepts-states`
    * :doc:`key-concepts-contracts`
    * :doc:`key-concepts-transactions`
    * :doc:`key-concepts-flows`
    * :doc:`key-concepts-consensus`
    * :doc:`key-concepts-notaries`
    * :doc:`key-concepts-time-windows`
    * :doc:`key-concepts-oracles`
    * :doc:`key-concepts-node`
    * :doc:`key-concepts-tradeoffs`

The detailed thinking and rationale behind these concepts are presented in two white papers:

    * `Corda: An Introduction`_
    * `Corda: A Distributed Ledger`_ (A.K.A. the Technical White Paper)

Explanations of the key concepts are also available as `videos <https://vimeo.com/album/4555732/>`_:

.. raw:: html

    <p><a href="https://vimeo.com/213812040">The Corda Ledger</a></p>
    <iframe src="https://player.vimeo.com/video/213812040" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213812054">States</a></p>
    <iframe src="https://player.vimeo.com/video/213812054" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213879807">Transactions</a></p>
    <iframe src="https://player.vimeo.com/video/213879807" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214168839">Contracts</a></p>
    <iframe src="https://player.vimeo.com/video/214168839" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213879293">Legal Prose</a></p>
    <iframe src="https://player.vimeo.com/video/213879293" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213881538">Commands</a></p>
    <iframe src="https://player.vimeo.com/video/213881538" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213879314">Timestamps</a></p>
    <iframe src="https://player.vimeo.com/video/213879314" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/213879328">Attachments</a></p>
    <iframe src="https://player.vimeo.com/video/213879328" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214046145">Flows</a></p>
    <iframe src="https://player.vimeo.com/video/214046145" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214138438">Consensus</a></p>
    <iframe src="https://player.vimeo.com/video/214138438" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214138458">Notaries</a></p>
    <iframe src="https://player.vimeo.com/video/214138458" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214157956">Oracles</a></p>
    <iframe src="https://player.vimeo.com/video/214157956" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. raw:: html

    <p><a href="https://vimeo.com/214168860">Corda Node, CorDapps and Network</a></p>
    <iframe src="https://player.vimeo.com/video/214168860" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
    <p></p>

.. _`Corda: An Introduction`: _static/corda-introductory-whitepaper.pdf
.. _`Corda: A Distributed Ledger`: _static/corda-technical-whitepaper.pdf
