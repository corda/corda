# Corda Firewall TLS HSM integration

## Background

## Requirement

* Support list of HSMs that is supported for legal identities
* Continue to support original file based configuration
* Continue to support DMZ mode of float with no connection initiated to trusted zone components e.g. HSM, however also allow tunnel cert HSM as an option
* Private keys for public ssl not to be available in DMZ float(not even in memory)
* Continue to support SNI multi-node operation 
* Continue to support HA integration 
* Do not change node to bridge protocol (investigate possibility of drop in bridge replacement in ENT 4)
* No change in performance in packets after TLS connection establishment 
* Fully compatible with peer node that does not use HSM
* Minimum changes to bridge and float to bound dev risk
* Must support RSA only HSM
* Full documentation and deployment instruction required for final shipping including consideration of key creation
* Handle silently HSM session timeout
* Handle HSM HA if available for that HSM
* No requirement to ship with HSM jar. Assume dynamically linked to any HSM jars
* No dependency on Corda Node module in float
* Support different HSM for different key classes e.g. SSL Peer vs Tunnel vs artemis?
* Upfront limitation that artemis might not work with HSM to be acceptable 
* Assume HSM keys are read only
* Support possibly arbitrary alias name

## Current Design (ENT v4.0)
### Current System Overview
The current Corda firewall supports lots of possible configurations, but the main operating mode is with an HA bridge cluster communicating
to a Float in the DMZ. As shown in the diagram below:
![](../../resources/bridge/ha_nodes/ha_nodes.png)

### Firewall Components
Internally the Corda firewall is implemented as a set of services using a simple supervision framework to manage connection/disconnection
events and leadership changes. An overview diagram is shown below with descriptions following
![](./images/firewall v4.0.png)

#### Service Descriptions
* **BridgeSupervisorService**  
Holds and initialises all bridge related services

    * **BridgeMasterService**  
    If HA enabled it coordinates Master election once BridgeArtemisConnectionService is up.
    
    * **BridgeArtemisConnectionService**  
    Connects to Artemis and retries if connection lost. Signals connection active to other services and provides access to SessionFactory for services that need to send, or receive over Artemis. 
    
    * **IncomingMessageFilterService**  
    Validates inbound messages coming via BridgeReceiverService and pushes good packets onto Artemis. The delivery Ack from Artemis then triggers return Acks to the original peer sender.
    
    * **BridgeReceiverService**  
    Handles inbound message path from Float. If FloatSupervisorService is in-process it uses direct calls, else it uses an AMQPClient to tunnel actions to the external Float.
    
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
We do support AES encryption of the access passwords to these keystores in the configuration files, but obviously this is not a full strength solution. 


## Target Solution
* **TLSSigningService**  
Handle TLS signing request, delegate signing operation to external process using DelegatedKeystore.

* **CryptoServiceSigningService**  
An implementation of ``TLSSigningService``, delegating signing operation to ``CryptoService``, which supports HSMs and java keystore file.

* **AMQPSigningService**  
An implementation of ``TLSSigningService``, delegating signing operation to the bridge via AMQP messages.

![](./images/firewall with HSM.png)
