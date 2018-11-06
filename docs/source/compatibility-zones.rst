.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

What is a compatibility zone?
=============================

Every Corda node is part of a "zone" (also sometimes called a Corda network) that is *permissioned*. Production
deployments require a secure certificate authority. We use the term "zone" to refer to a set of technically compatible
nodes reachable over a TCP/IP network like the internet. The word "network" is used in Corda but can be ambiguous with
the concept of a "business network", which is usually more like a membership list or subset of nodes in a zone that
have agreed to trade with each other.

How do I become part of a compatibility zone?
---------------------------------------------

Bootstrapping a compatibility zone
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You can easily bootstrap a compatibility zone for testing or pre-production use with either the
:doc:`network-bootstrapper` or the :doc:`network-builder` tools.

Joining an existing compatibility zone
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
After the testing and pre-production phases, users are encouraged to join an existing compatibility zone such as Corda
Network (the main compatibility zone) or the Corda Testnet. See :doc:`joining-a-compatibility-zone`.

Setting up a dynamic compatibility zone
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Some users may also be interested in setting up their own dynamic compatibility zone. For instructions and a discussion
of whether this approach is suitable for you, see :doc:`setting-up-a-dynamic-compatibility-zone`.
