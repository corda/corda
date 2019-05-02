Firewall upgrade
****************

.. contents::

Introduction
============

Corda Firewall 4.x brings with it an few changes, some related to deployment and configuration. The first part of the guide
covers the upgrade of existing firewall deployments, from the simplest operating mode to the full HA DMZ ready mode. For
more information on supported operating modes please see :doc:`Operating modes of the Bridge and Float <corda-firewall-component>`.
The **Embedded Developer Node** is left out as it is not impacted. The second part explains the steps to evolve the upgraded
environments to use the new 4.x features such as standalone Artemis with HA and shared bridge. For consistency, this guide uses the same
hostname and port values as main firewall guide.

Upgrade
=======

When upgrading, it's important to note that one of the main configuration differences is the renaming of all terms containing *bridge*
to use *firewall*. This applies to the configuration files for the bridge and float which are now called *firewall.conf*.
There are properties which have been renamed or reworked, such as *customSSLConfiguration* which was previously
used to override SSL configuration for bridge-to-artemis or bridge-to-float connections. For more information on the new properties, please see
:doc:`Firewall configuration <firewall-configuration-file>`.
One other major change is the binary file name has changed from  **corda-bridgeserver.jar** to **corda-firewall.jar**. Any existing deployment
scripts will require updating as well.

Node + Bridge (no float, no DMZ)
--------------------------------

For this type of deployment, version 3.x would have the following configuration:

+------------------------------------------------+------------------------------------------------+
| node.conf 3.x                                  | node.conf 4.x                                  |
+================================================+================================================+
| ..  code-block:: javascript                    | ..  code-block:: javascript                    |
|                                                |                                                |
|     myLegalName="O=Bank A,L=London,C=GB"       |     myLegalName="O=Bank A,L=London,C=GB"       |
|     p2pAddress="banka.com:10005"               |     p2pAddress="banka.com:10005"               |
|     messagingServerAddress="nodeserver:11005"  |     messagingServerAddress="nodeserver:11005"  |
|     messagingServerExternal = false            |     messagingServerExternal = false            |
|     rpcSettings {                              |     rpcSettings {                              |
|         address="nodeserver:10006"             |         address="nodeserver:10006"             |
|         adminAddress="nodeserver:10007"        |         adminAddress="nodeserver:10007"        |
|     }                                          |     }                                          |
|     enterpriseConfiguration = {                |     enterpriseConfiguration = {                |
|     	  externalBridge = true                  |         externalBridge = true                  |
|     }                                          |     }                                          |
|     keyStorePassword = "keyPass"               |     keyStorePassword = "keyPass"               |
|     trustStorePassword = "trustPass"           |     trustStorePassword = "trustPass"           |
+------------------------------------------------+------------------------------------------------+

+---------------------------------------------------+--------------------------------------------------+
| bridge.conf 3.x                                   | firewall.conf 4.x                                |
+===================================================+==================================================+
| ..  code-block:: javascript                       | ..  code-block:: javascript                      |
|     :emphasize-lines: 1                           |     :emphasize-lines: 1                          |
|                                                   |                                                  |
|     bridgeMode = SenderReceiver                   |     firewallMode = SenderReceiver                |
|     outboundConfig {                              |     outboundConfig {                             |
|         artemisBrokerAddress = "nodeserver:11005" |         artemisBrokerAddress = "nodeserver:11005"|
|     }                                             |     }                                            |
|     inboundConfig {                               |     inboundConfig {                              |
|         listeningAddress = "bridgeexternal:10005" |         listeningAddress = "bridgeexternal:10005"|
|     }                                             |     }                                            |
|     networkParametersPath = network-parameters    |     networkParametersPath = network-parameters   |
|     sslKeystore = "./nodeCerts/sslkeystore.jks"   |     sslKeystore = "./nodeCerts/sslkeystore.jks"  |
|     keyStorePassword = "keyPass"                  |     keyStorePassword = "keyPass"                 |
|     trustStoreFile = "./nodeCerts/truststore.jks" |     trustStoreFile = "./nodeCerts/truststore.jks"|
|     trustStorePassword = "trustPass"              |     trustStorePassword = "trustPass"             |
+---------------------------------------------------+--------------------------------------------------+

DMZ ready (node + bridge + float)
---------------------------------

+------------------------------------------------+------------------------------------------------+
| node.conf 3.x                                  | node.conf 4.x                                  |
+================================================+================================================+
| ..  code-block:: javascript                    | ..  code-block:: javascript                    |
|                                                |                                                |
|     myLegalName="O=Bank A,L=London,C=GB"       |     myLegalName="O=Bank A,L=London,C=GB"       |
|     p2pAddress="banka.com:10005"               |     p2pAddress="banka.com:10005"               |
|     messagingServerAddress="nodeserver:11005"  |     messagingServerAddress="nodeserver:11005"  |
|     messagingServerExternal = false            |     messagingServerExternal = false            |
|     rpcSettings {                              |     rpcSettings {                              |
|         address="nodeserver:10006"             |         address="nodeserver:10006"             |
|         adminAddress="nodeserver:10007"        |         adminAddress="nodeserver:10007"        |
|     }                                          |     }                                          |
|     enterpriseConfiguration = {                |     enterpriseConfiguration = {                |
|     	  externalBridge = true                  |         externalBridge = true                  |
|     }                                          |     }                                          |
|     keyStorePassword = "keyPass"               |     keyStorePassword = "keyPass"               |
|     trustStorePassword = "trustPass"           |     trustStorePassword = "trustPass"           |
+------------------------------------------------+------------------------------------------------+

+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+
| bridge.conf - Bridge configuration 3.x                                           | firewall.conf - Bridge configuration 4.x                                         |
+==================================================================================+==================================================================================+
| ..  code-block:: javascript                                                      | ..  code-block:: javascript                                                      |
|     :emphasize-lines: 1,8                                                        |     :emphasize-lines: 1,8                                                        |
|                                                                                  |                                                                                  |
|     bridgeMode = BridgeInner                                                     |     firewallMode = BridgeInner                                                   |
|     outboundConfig {                                                             |     outboundConfig {                                                             |
|         artemisBrokerAddress = "nodeserver:11005"                                |         artemisBrokerAddress = "nodeserver:11005"                                |
|     }                                                                            |     }                                                                            |
|     bridgeInnerConfig {                                                          |     bridgeInnerConfig {                                                          |
|         floatAddress = [ "dmzinternal:12005" ]                                   |         floatAddress = [ "dmzinternal:12005" ]                                   |
|         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |
|         customSSLConfiguration {                                                 |         tunnelSSLConfiguration {                                                 |
|             keyStorePassword = "bridgepass"                                      |             keyStorePassword = "bridgepass"                                      |
|             trustStorePassword = "trustpass"                                     |             trustStorePassword = "trustpass"                                     |
|             sslKeystore = "./bridgecerts/bridge.jks"                             |             sslKeystore = "./bridgecerts/bridge.jks"                             |
|             trustStoreFile = "./bridgecerts/trust.jks"                           |             trustStoreFile = "./bridgecerts/trust.jks"                           |
|             crlCheckSoftFail = true                                              |             revocationConfig {                                                   |
|                                                                                  |                 mode = SOFT_FAIL                                                 |
|                                                                                  |             }                                                                    |
|         }                                                                        |         }                                                                        |
|     }                                                                            |     }                                                                            |
|     networkParametersPath = network-parameters                                   |     networkParametersPath = network-parameters                                   |
|     sslKeystore = "./nodeCerts/sslkeystore.jks"                                  |     sslKeystore = "./nodeCerts/sslkeystore.jks"                                  |
|     keyStorePassword = "keyPass"                                                 |     keyStorePassword = "keyPass"                                                 |
|     trustStoreFile = "./nodeCerts/truststore.jks"                                |     trustStoreFile = "./nodeCerts/truststore.jks"                                |
|     trustStorePassword = "trustPass"                                             |     trustStorePassword = "trustPass"                                             |
+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+

+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+
| bridge.conf - Float configuration 3.x                                            | firewall.conf - Float configuration 4.x                                          |
+==================================================================================+==================================================================================+
| ..  code-block:: javascript                                                      | ..  code-block:: javascript                                                      |
|     :emphasize-lines: 1,8                                                        |     :emphasize-lines: 1,8                                                        |
|                                                                                  |                                                                                  |
|     bridgeMode = FloatOuter                                                      |     firewallMode = FloatOuter                                                    |
|     inboundConfig {                                                              |     inboundConfig {                                                              |
|         listeningAddress = "dmzexternal:10005"                                   |         listeningAddress = "dmzexternal:10005"                                   |
|     }                                                                            |     }                                                                            |
|     floatOuterConfig {                                                           |     floatOuterConfig {                                                           |
|         floatAddress = [ "dmzinternal:12005" ]                                   |         floatAddress = [ "dmzinternal:12005" ]                                   |
|         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |
|         customSSLConfiguration {                                                 |         tunnelSSLConfiguration {                                                 |
|             keyStorePassword = "floatpass"                                       |             keyStorePassword = "floatpass"                                       |
|             trustStorePassword = "trustpass"                                     |             trustStorePassword = "trustpass"                                     |
|             sslKeystore = "./floatcerts/float.jks"                               |             sslKeystore = "./floatcerts/float.jks"                               |
|             trustStoreFile = "./floatcerts/trust.jks"                            |             trustStoreFile = "./floatcerts/trust.jks"                            |
|             crlCheckSoftFail = true                                              |             revocationConfig {                                                   |
|                                                                                  |                 mode = SOFT_FAIL                                                 |
|                                                                                  |             }                                                                    |
|         }                                                                        |         }                                                                        |
|     }                                                                            |     }                                                                            |
|     networkParametersPath = network-parameters                                   |     networkParametersPath = network-parameters                                   |
+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+

DMZ ready with outbound SOCKS
-----------------------------

The changes for this deployment are the same as for **DMZ ready (node + bridge + float)** with the additional renaming of the
SOCKS configuration property from **socksProxyConfig** to **proxyConfig**.

Full production HA DMZ ready (hot/cold node, hot/warm bridge)
-------------------------------------------------------------

+------------------------------------------------+------------------------------------------------+
| node.conf 3.x - Hot instance                   | node.conf 4.x - Hot instance                   |
+================================================+================================================+
| ..  code-block:: javascript                    | ..  code-block:: javascript                    |
|                                                |                                                |
|     myLegalName="O=Bank A,L=London,C=GB"       |     myLegalName="O=Bank A,L=London,C=GB"       |
|     p2pAddress="banka.com:10005"               |     p2pAddress="banka.com:10005"               |
|     messagingServerAddress="nodeserver1:11005" |     messagingServerAddress="nodeserver1:11005" |
|     messagingServerExternal = false            |     messagingServerExternal = false            |
|     rpcSettings {                              |     rpcSettings {                              |
|         address="nodeserver1:10006"            |         address="nodeserver1:10006"            |
|         adminAddress="nodeserver1:10007"       |         adminAddress="nodeserver1:10007"       |
|     }                                          |     }                                          |
|     enterpriseConfiguration = {                |     enterpriseConfiguration = {                |
|     	  externalBridge = true                  |         externalBridge = true                  |
|         mutualExclusionConfiguration = {       |         mutualExclusionConfiguration = {       |
|             on = true                          |             on = true                          |
|             updateInterval = 20000             |             updateInterval = 20000             |
|             waitInterval = 40000               |             waitInterval = 40000               |
|         }                                      |         }                                      |
|     }                                          |     }                                          |
|     keyStorePassword = "keyPass"               |     keyStorePassword = "keyPass"               |
|     trustStorePassword = "trustPass"           |     trustStorePassword = "trustPass"           |
+------------------------------------------------+------------------------------------------------+

+------------------------------------------------+------------------------------------------------+
| node.conf 3.x - Cold instance                  | node.conf 4.x - Cold instance                  |
+================================================+================================================+
| ..  code-block:: javascript                    | ..  code-block:: javascript                    |
|                                                |                                                |
|     myLegalName="O=Bank A,L=London,C=GB"       |     myLegalName="O=Bank A,L=London,C=GB"       |
|     p2pAddress="banka.com:10005"               |     p2pAddress="banka.com:10005"               |
|     messagingServerAddress="nodeserver2:11005" |     messagingServerAddress="nodeserver2:11005" |
|     messagingServerExternal = false            |     messagingServerExternal = false            |
|     rpcSettings {                              |     rpcSettings {                              |
|         address="nodeserver2:10006"            |         address="nodeserver2:10006"            |
|         adminAddress="nodeserver2:10007"       |         adminAddress="nodeserver2:10007"       |
|     }                                          |     }                                          |
|     enterpriseConfiguration = {                |     enterpriseConfiguration = {                |
|     	  externalBridge = true                  |         externalBridge = true                  |
|         mutualExclusionConfiguration = {       |         mutualExclusionConfiguration = {       |
|             on = true                          |             on = true                          |
|             updateInterval = 20000             |             updateInterval = 20000             |
|             waitInterval = 40000               |             waitInterval = 40000               |
|         }                                      |         }                                      |
|     }                                          |     }                                          |
|     keyStorePassword = "keyPass"               |     keyStorePassword = "keyPass"               |
|     trustStorePassword = "trustPass"           |     trustStorePassword = "trustPass"           |
+------------------------------------------------+------------------------------------------------+

+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+
| bridge.conf - Bridge configuration 3.x (same for every instance)                 | firewall.conf - Bridge configuration 4.x (same for every instance)               |
+==================================================================================+==================================================================================+
| ..  code-block:: javascript                                                      | ..  code-block:: javascript                                                      |
|     :emphasize-lines: 1,9                                                        |     :emphasize-lines: 1,9                                                        |
|                                                                                  |                                                                                  |
|     bridgeMode = BridgeInner                                                     |     firewallMode = BridgeInner                                                   |
|     outboundConfig {                                                             |     outboundConfig {                                                             |
|         artemisBrokerAddress = "nodeserver1:11005"                               |         artemisBrokerAddress = "nodeserver1:11005"                               |
|         alternateArtemisBrokerAddresses = ["nodeserver2:11005"]                  |         alternateArtemisBrokerAddresses = ["nodeserver2:11005"]                  |
|     }                                                                            |     }                                                                            |
|     bridgeInnerConfig {                                                          |     bridgeInnerConfig {                                                          |
|         floatAddress = [ "dmzinternal1:12005", "dmzinternal2:12005" ]            |         floatAddress = [ "dmzinternal1:12005", "dmzinternal2:12005" ]            |
|         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |
|         customSSLConfiguration {                                                 |         tunnelSSLConfiguration {                                                 |
|             keyStorePassword = "bridgepass"                                      |             keyStorePassword = "bridgepass"                                      |
|             trustStorePassword = "trustpass"                                     |             trustStorePassword = "trustpass"                                     |
|             sslKeystore = "./bridgecerts/bridge.jks"                             |             sslKeystore = "./bridgecerts/bridge.jks"                             |
|             trustStoreFile = "./bridgecerts/trust.jks"                           |             trustStoreFile = "./bridgecerts/trust.jks"                           |
|             crlCheckSoftFail = true                                              |             revocationConfig {                                                   |
|                                                                                  |                 mode = SOFT_FAIL                                                 |
|                                                                                  |             }                                                                    |
|         }                                                                        |         }                                                                        |
|     }                                                                            |     }                                                                            |
|     haConfig {                                                                   |     haConfig {                                                                   |
|        haConnectionString = "zk://zookeep1:11105,zk://zookeep2:11105,            |        haConnectionString = "zk://zookeep1:11105,zk://zookeep2:11105,            |
|                              zk://zookeep3:11105"                                |                              zk://zookeep3:11105"                                |
|     }                                                                            |     }                                                                            |
|     networkParametersPath = network-parameters                                   |     networkParametersPath = network-parameters                                   |
|     sslKeystore = "./nodeCerts/sslkeystore.jks"                                  |     sslKeystore = "./nodeCerts/sslkeystore.jks"                                  |
|     keyStorePassword = "keyPass"                                                 |     keyStorePassword = "keyPass"                                                 |
|     trustStoreFile = "./nodeCerts/truststore.jks"                                |     trustStoreFile = "./nodeCerts/truststore.jks"                                |
|     trustStorePassword = "trustPass"                                             |     trustStorePassword = "trustPass"                                             |
+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+

+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+
| bridge.conf - Float configuration 3.x hot instance                               | firewall.conf - Float configuration 4.x hot instance                             |
+==================================================================================+==================================================================================+
| ..  code-block:: javascript                                                      | ..  code-block:: javascript                                                      |
|     :emphasize-lines: 1,8                                                        |     :emphasize-lines: 1,8                                                        |
|                                                                                  |                                                                                  |
|     bridgeMode = FloatOuter                                                      |     firewallMode = FloatOuter                                                    |
|     inboundConfig {                                                              |     inboundConfig {                                                              |
|         listeningAddress = "dmzexternal1:10005"                                  |         listeningAddress = "dmzexternal1:10005"                                  |
|     }                                                                            |     }                                                                            |
|     floatOuterConfig {                                                           |     floatOuterConfig {                                                           |
|         floatAddress = [ "dmzinternal1:12005" ]                                  |         floatAddress = [ "dmzinternal1:12005" ]                                  |
|         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |
|         customSSLConfiguration {                                                 |         tunnelSSLConfiguration {                                                 |
|             keyStorePassword = "floatpass"                                       |             keyStorePassword = "floatpass"                                       |
|             trustStorePassword = "trustpass"                                     |             trustStorePassword = "trustpass"                                     |
|             sslKeystore = "./floatcerts/float.jks"                               |             sslKeystore = "./floatcerts/float.jks"                               |
|             trustStoreFile = "./floatcerts/trust.jks"                            |             trustStoreFile = "./floatcerts/trust.jks"                            |
|             crlCheckSoftFail = true                                              |             revocationConfig {                                                   |
|                                                                                  |                 mode = SOFT_FAIL                                                 |
|                                                                                  |             }                                                                    |
|         }                                                                        |         }                                                                        |
|     }                                                                            |     }                                                                            |
|     networkParametersPath = network-parameters                                   |     networkParametersPath = network-parameters                                   |
+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+

+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+
| bridge.conf - Float configuration 3.x warm instance                              | firewall.conf - Float configuration 4.x warm instance                            |
+==================================================================================+==================================================================================+
| ..  code-block:: javascript                                                      | ..  code-block:: javascript                                                      |
|     :emphasize-lines: 1,8                                                        |     :emphasize-lines: 1,8                                                        |
|                                                                                  |                                                                                  |
|     bridgeMode = FloatOuter                                                      |     firewallMode = FloatOuter                                                    |
|     inboundConfig {                                                              |     inboundConfig {                                                              |
|         listeningAddress = "dmzexternal2:10005"                                  |         listeningAddress = "dmzexternal2:10005"                                  |
|     }                                                                            |     }                                                                            |
|     floatOuterConfig {                                                           |     floatOuterConfig {                                                           |
|         floatAddress = [ "dmzinternal2:12005" ]                                  |         floatAddress = [ "dmzinternal2:12005" ]                                  |
|         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |         expectedCertificateSubject = "CN=Float Local,O=Local Only,L=London,C=GB" |
|         customSSLConfiguration {                                                 |         tunnelSSLConfiguration {                                                 |
|             keyStorePassword = "floatpass"                                       |             keyStorePassword = "floatpass"                                       |
|             trustStorePassword = "trustpass"                                     |             trustStorePassword = "trustpass"                                     |
|             sslKeystore = "./floatcerts/float.jks"                               |             sslKeystore = "./floatcerts/float.jks"                               |
|             trustStoreFile = "./floatcerts/trust.jks"                            |             trustStoreFile = "./floatcerts/trust.jks"                            |
|             crlCheckSoftFail = true                                              |             revocationConfig {                                                   |
|                                                                                  |                 mode = SOFT_FAIL                                                 |
|                                                                                  |             }                                                                    |
|         }                                                                        |         }                                                                        |
|     }                                                                            |     }                                                                            |
|     networkParametersPath = network-parameters                                   |     networkParametersPath = network-parameters                                   |
+----------------------------------------------------------------------------------+----------------------------------------------------------------------------------+


Reconfiguring to the shared Corda Firewall Architecture
=======================================================

In 4.x, it is possible to for multiple nodes representing multiple identities to reside behind the same Corda Firewall.
To achieve this, the nodes can be configured to use an external Artemis server. Furthermore, the Artemis server can be run
in HA mode with replication and failback. Reconfiguring a node and bridge to use external artemis does not affect the float configuration,
therefore it will not be discussed.

Client connections to external Artemis require separate SSL key and trust stores. These can be created using the *ha-utilities* tool
For more information please see :doc:`HA Utilities <ha-utilities>`. There is also an example of keystore generation in
:doc:`Firewall configuration <firewall-configuration-file>` under the *Artemis keystore generation* section.

For the purpose of this guide, the Artemis connection key and trust stores will be named *artemis.jks* and *artemis-truststore.jks*.
The machines hosting the Artemis instances are *artemisserver1* and *artemisserver2*.

Node + Bridge to Node + Artemis + Bridge
----------------------------------------

+------------------------------------------------+----------------------------------------------------------------------+
| node.conf - Internal Artemis                   | node.conf - External Artemis (HA mode)                               |
+================================================+======================================================================+
| ..  code-block:: javascript                    | ..  code-block:: javascript                                          |
|     :emphasize-lines: 3,4                      |     :emphasize-lines: 3,4,10-16                                      |
|                                                |                                                                      |
|     myLegalName="O=Bank A,L=London,C=GB"       |     myLegalName="O=Bank A,L=London,C=GB"                             |
|     p2pAddress="banka.com:10005"               |     p2pAddress="banka.com:10005"                                     |
|     messagingServerAddress="nodeserver:11005"  |     messagingServerAddress="artemisserver1:11005"                    |
|     messagingServerExternal = false            |     messagingServerExternal = true                                   |
|     rpcSettings {                              |     rpcSettings {                                                    |
|         address="nodeserver:10006"             |         address="nodeserver:10006"                                   |
|         adminAddress="nodeserver:10007"        |         adminAddress="nodeserver:10007"                              |
|     }                                          |     }                                                                |
|     enterpriseConfiguration = {                |     enterpriseConfiguration = {                                      |
|                                                |         messagingServerConnectionConfiguration = "CONTINUOUS_RETRY"  |
|                                                |         messagingServerBackupAddresses = ["artemisserver2:11005"]    |
|                                                |         messagingServerSslConfiguration = {                          |
|                                                |                     sslKeystore = artemis/artemis.jks                |
|                                                |                     keyStorePassword = artemisStorePass              |
|                                                |                     trustStoreFile = artemis/artemis-truststore.jks  |
|                                                |                     trustStorePassword = artemisTrustpass            |
|                                                |         }                                                            |
|         externalBridge = true                  |         externalBridge = true                                        |
|     }                                          |     }                                                                |
|     keyStorePassword = "keyPass"               |     keyStorePassword = "keyPass"                                     |
|     trustStorePassword = "trustPass"           |     trustStorePassword = "trustPass"                                 |
+------------------------------------------------+----------------------------------------------------------------------+

+---------------------------------------------------+----------------------------------------------------------------------+
| firewall.conf - Bridge Internal Artemis           | firewall.conf - Bridge External Artemis                              |
+===================================================+======================================================================+
| ..  code-block:: javascript                       | ..  code-block:: javascript                                          |
|     :emphasize-lines: 3-10                        |     :emphasize-lines: 3-10,17,18                                     |
|                                                   |                                                                      |
|     firewallMode = SenderReceiver                 |     firewallMode = SenderReceiver                                    |
|     outboundConfig {                              |     outboundConfig {                                                 |
|         artemisBrokerAddress = "nodeserver:11005" |         artemisBrokerAddress = "artemisserver1:11005"                |
|                                                   |         alternateArtemisBrokerAddresses = [ "artemisserver2:11005" ] |
|                                                   |         artemisSSLConfiguration {                                    |
|                                                   |             keyStorePassword = "artemisStorePass"                    |
|                                                   |             trustStorePassword = "artemisTrustpass"                  |
|                                                   |             sslKeystore = "artemis/artemis.jks"                      |
|                                                   |             trustStoreFile = "artemis/artemis-truststore.jks"        |
|                                                   |             revocationConfig {                                       |
|                                                   |               mode = SOFT_FAIL                                       |
|                                                   |             }                                                        |
|                                                   |         }                                                            |
|     }                                             |     }                                                                |
|     inboundConfig {                               |     inboundConfig {                                                  |
|         listeningAddress = "bridgeexternal:10005" |         listeningAddress = "bridgeexternal:10005"                    |
|     }                                             |     }                                                                |
|     networkParametersPath = network-parameters    |     networkParametersPath = network-parameters                       |
|     sslKeystore = "./nodeCerts/sslkeystore.jks"   |     sslKeystore = "./nodeCerts/unitedSslKeystore.jks"                |
|     keyStorePassword = "keyPass"                  |     keyStorePassword = "keyPass"                                     |
|     trustStoreFile = "./nodeCerts/truststore.jks" |     trustStoreFile = "./nodeCerts/truststore.jks"                    |
|     trustStorePassword = "trustPass"              |     trustStorePassword = "trustPass"                                 |
+---------------------------------------------------+----------------------------------------------------------------------+

Multiple nodes behind the Bridge
--------------------------------

To add additional nodes behind the same Corda firewall (either all-in-one bridge or bridge and float), it's sufficient
to configure the new nodes to connect to Artemis as shown in the previous section. The same applies for the bridge. The additional
nodes need to set their P2P address as the shared float's address. Furthermore, all previous floats except the shared one need to be shut down.