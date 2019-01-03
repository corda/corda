Corda Firewall Cookbook
=======================

Adding new nodes to existing shared Bridge
------------------------------------------
Prerequisites
^^^^^^^^^^^^^

This guide assumes you have a working corda HA setup, with a shared bridge, artemis broker and one or more Corda node.

Instructions
^^^^^^^^^^^^

1. Backup and shutdown all Corda components - Nodes, Bridges, Artemis broker and Float.

2. Register your new entities with the network operator. See :doc:`joining-a-compatibility-zone`.

3. Locate the SSL keystore file in node's certificate folder. e.g. ``<node base directory>/certificates/sslkeystore.jks``

4. Copy the SSL keystores generated from the registration process to Bridge if they are on a different host.

5. Using the :doc:`HA Utilities <ha-utilities>`, copy the newly acquired legal entity's SSL key to the bridge's SSL keystore.
   ``ha-utilities import-ssl-key --node-keystores <<Node keystore path>> --node-keystore-passwords=<<Node keystore password>> --bridge-keystore=<<Bridge keystore path>> --bridge-keystore-password=<<Bridge keystore password>>``

6. Start the Bridge and other nodes.
