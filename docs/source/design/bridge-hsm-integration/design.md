# Corda Firewall TLS HSM integration

## Background
### Current System Overview
The current Corda firewall supports lots of possible configurations, but the main operating mode is with an HA bridge cluster communicating
to a Float in the DMZ. As shown in the diagram below:
![](../../resources/bridge/ha_nodes/ha_nodes.png)

## Requirements
* Support same list of HSMs that is supported for legal identities
* Continue to support original file based configuration
* Continue to support DMZ mode of Corda Firewall. In this mode the 'float' portion operates in the DMZ with no connection initiated to 
any trusted zone components e.g. HSM. However, we will also allow the float to use a dedicated HSM for the local authenticated TLS link with the 'bridge'
component in the internal zone
* Private keys for public TLS not to be available in DMZ firewall component (not even in memory)
* Continue to support SNI multi-node operation 
* Continue to support HA integration 
* Do not change node to bridge protocol and ideally it should be possible to upgrade the Corda firewall
jars to Corda Enterprise 4.1 without updating the node from Corda Enterprise 4.0
* No change in performance in packets after TLS connection establishment 
* Fully compatible with peer node that does not use HSM
* Minimum changes to bridge and float to bound dev risk
* Must support RSA only HSM as some of the HSM vendors do not provide Elliptic Curve support e.g. Azure Key Vault.
* Full documentation and deployment instruction required for final shipping including consideration of key creation
* Handle silently HSM session timeout
* Handle HSM HA if available for that HSM
* No requirement to ship with HSM jar. Assume dynamically linked to any HSM jars
* No dependency on Corda Node module in Corda Fireewall code base
* Support different HSM for different key classes e.g. SSL Peer vs Tunnel vs artemis?

## Asumptions
* Assume HSM keys are read only
* Assume that the alias names used to lookup individual network keys in the HSM do not have to conform to any specific rules and may require 
configuration driven enumeration.

## Current Design (ENT v4.0)

### Firewall Components
Internally the Corda firewall is implemented as a set of services using a simple supervision framework to manage connection/disconnection
events and leadership changes. An overview diagram is shown below with descriptions following
![](./images/firewall%20v4.0.png)

#### Service Descriptions
* **BridgeSupervisorService**  
Holds and initialises all bridge related services

    * **BridgeMasterService**  
    If HA enabled it coordinates Master election once BridgeArtemisConnectionService is up.
    
    * **BridgeArtemisConnectionService**  
    Connects to Artemis and retries if connection lost. Signals connection active to other services and provides access to an Artemis ``SessionFactory``
    object which other services use to send, or receive messages over Artemis. 
    
    * **IncomingMessageFilterService**  
    Validates inbound messages coming via BridgeReceiverService and pushes good packets onto Artemis. The delivery Ack from Artemis then triggers return Acks to the original peer sender.
    
    * **BridgeReceiverService**  
    Handles inbound message path from Float. It uses an AMQPClient to tunnel simple control actions to the external Float.
    
    * **BridgeSenderService**  
    Responsible for outbound message sending. Creates a BridgeContolListener on activate and stops on deactivate.
    
        * **BridgeControlListener**  
        When started sends request for bridge snapshot from nodes. Then manages connections to peers via AMQPBridgeManager when it receives control messages from nodes.
        
            * **AMQPBridgeManager**  
            Owns AMQPBridge objects that use AMQPClients to manage each outgoing bridge. Once AMQPClient link established the AMQPBridge  subscribes to Artemis and pushes messages to peers via the AMQPClients.
        
    * **FirewallAuditService**  
    Logs information about connection, disconnections, packets, etc
    
    
* **FloatSupervisorService**  

    * **BridgeAMQPListenerService**  
    This service controls the externally accessible P2P listening port. It requires activation calls containing the nodes’ ssl private keys. It then activates its AMQPServer
    
    * **FloatControlService**  
    Acts as communication server for control messages from the Bridge. Contains an AMQPServer to manage the actual communications.

### Current Key Management
The Corda firewall was designed to minimise the impact of any compromise of the 'float' component in the DMZ, which therefore contains only very limited keys on disk
and the P2P key is held only transiently in memory and wiped if the float loses connection to the bridge.
See [TLS management discussion](https://github.com/corda/corda/blob/master/docs/source/design/float/decisions/ssl-termination.md)
and [Float Design Review](https://github.com/corda/corda/blob/master/docs/source/design/float/design.md) for the historical discussion.

A diagram of the current key management is:
![](./images/Node%20TLS%20Corda%20ENT%20v4.0.png)

The current key management solution uses encrypted KeyStore files for holding public certificates and private keys,
although it has always been intended to move towards HSM support.
We do support AES encryption of the access passwords to these keystores in the configuration files. The goal of this next phase is to
build out the production HSM support to further restrict key access.


## Design Proposal
### Key Management
The proposed overview with full HSM support is shown below. The key technical challenge has been to find a way to prevent the float initiating
any connection to an HSM containing the Corda Network keys as we have to regard the DMZ machine as potentially compromised by an attacker.
We will support the float machine in the DMZ being optionally able to access a dedicated HSM for the tunnel TLS key,
but this HSM has to be regarded as tainted from a security perspective. We have therefore carried out a prior POC and developed a technique
to intercept signing operations inside the TLS handshake engine and this allows the commands to be tunneled to the bridge for HSM based authorization signing.
This is possible by creating a Java JCA custom ``KeyStore`` ``Provider`` that allows for access to the private key to be intercepted using standard
Java techniques. We refer to this custom plugin as the ``DelegatedKeystore`` in the discussion.
Any attacker is then unable to gain wider access to the HSM and the only interactions between bridge and float are for the stereotypical
operation of signing the ServerKeyExchange messages, which the bridge can further confirm is well formed.

The ``DelegatedKeystore`` is also used elsewhere to allow us to select the source HSM by context in other components of the bridge/float.
The existing service architecture also handles correct handling of disconnects from the HSM and will ensure that HA failover can only occur
to a valid site if there are network issues.
![](./images/Node%20TLS%20Corda%20ENT%20v4.1%20with%20HSM.png)

### Internal Service Changes
Internally the changes shown in diagram below area quite small:
1. Creation of a few new services which provide the ``DelegatedKeystore`` to the existing services where they would historically load from file.
2. Altering the tunnel messages to support the signing requests and to provision only the public TLS certificates, rather than the previous
exchange of encrypted private keys.
3. Configuration management and loading of the drivers for the specific ``CryptoService`` implementation that actually communicates with the HSMs.
Note that the drivers will need to be separately installed into a folder as we are not licenced to ship the HSM verdor files as part of our distribution.

![](./images/firewall%20with%20HSM.png)
### New Services
* **TLSSigningService**  
Handle TLS signing request, delegate signing operation to external process using DelegatedKeystore.

* **CryptoServiceSigningService**  
An implementation of ``TLSSigningService``, delegating signing operation to ``CryptoService``, which supports HSMs and java keystore file.

* **AMQPSigningService**  
An implementation of ``TLSSigningService``, delegating signing operation to the bridge via AMQP messages.


### Configuration of HSMs
We are still investigating what format of config file has the best usability, especially as we need to ensure that pre-existing
configuration files are still valid. However, in keeping with Node HSM configuration we expect that the type of HSM will selected via
a ``cryptoServiceName`` configuration property and all of the vendor specific configuration will be in a separate (encrypted) file referred
to by a ``cryptoServiceConf`` property.
We will also ensure that we can register different HSM connection details for different roles i.e. Network Keys, Tunnel and possibly Artemis.
To aid compatibility we will not depend upon certificate chains being in the HSM, but will access the public certificates through the existing
file stores. Our tool chain will be extended to ensure that the approrpiate files can be generated during registration.
The configuration section for the HSM should also allow the aliases to be manually specified along with their associated X500 name.

### Known limitations of the design
There are known limitation in the Artemis library with regard to flexibility of keys and our recommendation is to regard the Artemis TLS as
a simple internal access token to the Artemis broker. These private keys do not affect the security guarantees of the bridge, although TLS to the 
Artemis broker does provide privacy protection against internal packet snooping. Do note that all Artemis access happens internally in the trusted zone and whilst
we will investigate moving to full HSM support we cannot promise this for Corda Enterprise 4.1 and may still need to use files in the node
and on the broker itself. The primary restriction on internal hijack is that all transactions must be signed by the node legal identity keys
which will be stored in another HSM.