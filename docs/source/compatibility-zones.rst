.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

What is a compatibility zone?
=============================

Every Corda node is part of a "zone" (also sometimes called a Corda network) that is *permissioned*. Production
deployments require a secure certificate authority. Most users will join an existing network such as Corda
Network (the main network) or the Corda Testnet. We use the term "zone" to refer to a set of technically compatible nodes reachable
over a TCP/IP network like the internet. The word "network" is used in Corda but can be ambiguous with the concept
of a "business network", which is usually more like a membership list or subset of nodes in a zone that have agreed
to trade with each other.
