Corda Firewall
==============

Corda Enterprise ships a component called the *Corda Firewall*. The firewall is actually made up of two separate programs,
called the *bridge* and the *float*. These handle outbound and inbound connections respectively, and allow a node
administrator to minimise the amount of code running in a network's DMZ. The firewall provides some basic protection
features in this release: future releases may add enhanced monitoring and audit capabilities.

.. toctree::

   corda-bridge-component
   bridge-configuration-file
