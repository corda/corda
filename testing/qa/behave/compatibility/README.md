Compatibility Test Suite (CTS)
=============================

Compatibility / Interoperability testing refers to validating the run-time behaviour of different versions of  
components within the Corda Platform and ensuring that data is compatible, both "on the wire" (P2P AMQP and RPC Kyro 
serialization forms) and "at rest" (persisted to a database) between collaborating components.

Compatibility testing focuses at three different levels:

1. Corda Network (Compatibility Zone - level, Doorman, Notary)
2. Corda Node (eg. participant nodes, oracles)
3. Corda Applications (CorDapps) 

Corda Network
-------------
Components within scope for this level of testing include:
- Doorman
- Network Map Service
- Notary (and notary clusters)
- Ancillary support infrastructure services: Float, Bridge Manager, HA supervisors 

The following aspects of a Corda Network are configurable/changeable over time and require validating via CTS coverage:

- Permissioning: provision of an identity certificate signed by the networks root CA
- Network Parameters changes (such as adding/removing a Notary, whitelisting CorDapps for constraint checking),
  and distribution to nodes via bootstrapper (OS) / HTTP NMS distribution (Enterprise)
- Nodes and Oracles addition and removal (with provisioned identities) via bootstrapping / registration with Network Map
    a) OS: `nodeInfo` distribution into a node's `additional-node-infos` directory
    b) Enterprise: HTTP NMS (node configured with `compatibilityZoneURL`)
- Versioning: `platformVersion` (part of a node's `NodeInfo`)
- Change in HA setup

References:
 
1. https://docs.corda.r3.com/head/setting-up-a-corda-network.html
2. https://docs.corda.r3.com/head/permissioning.html
3. https://docs.corda.r3.com/head/network-map.html
4. https://docs.corda.r3.com/head/network-map.html#network-parameters-update-process
5. https://docs.corda.r3.com/head/versioning.html
6. https://docs.corda.r3.com/head/high-availability.html

Corda Node
----------
Refers exclusively to exercising the capabilities of a Corda Node, to include:

- Node Configuration changes (such as change of database, rpcUser permissions, address changes, standalone broker).
  Impact and consequences of running in`devMode` (uses pre-configured keystores, no security) 
- Node Start-up options
       
References: 

1. https://docs.corda.r3.com/head/node-administration.html
2. https://docs.corda.r3.com/head/corda-configuration-file.html
     
Corda Application (CorDapp) 
-----------------
Refers to testing the evolution of CorDapps with respect to changes to the following:
- States
- Contracts
- Services
- Flows
- Utilities and library functions (eg. cryptography)

References:

- https://docs.corda.net/head/upgrading-cordapps.html

Specifically, compatibility testing takes the following types of CorDapp-related versioning changes into consideration:

1. Flow Versioning.

    The `version` property of an `@InitiatingFlow` determines the version in effect at execution.
  
    A non-backwards compatible change is one where the interface of the flow changes: specifically, the sequence of `send` 
    and `receive` calls between an `InitiatingFlow` and an `InitiatedBy` flow, including the types of the data sent and received.
  
    There are 3 failure scenarios to test for:
  
    a) flows that hang (due to the expectation that a response is expected but never received)
    
    b) incorrect data types in `send` or `receive` calls: "Expected Type X but Received Type Y"
    
    c) early termination: "Counterparty flow terminated early on the other side"
   
    Inlined Flows are not versioned as they inherit the version of their parent `initiatingFlow` or `inittiatedFlow`. 
    There are 2 scenarios to consider: 
  
    a) Inlined flows that perform `send` and `receive` calling with other inlined flows 
       A change to the interface here must be considered a change to the parent flow interfaces.
       
    b) Utility inlined flows that perform a local function (eg. query the vault).
  
    Flow Draining mode is used to ensure outstanding checkpointed flows are flushed before upgrading to a new flow version.
   
2. State and contract.

   Two types of contract/state upgrade:
   
   a) Implicit, using CZ Whitelisting of multiple CorDapp version (subsequently enforced by contract constraint logic)
   
   b) Explicit, by creating a special contract upgrade transaction and getting all participants of a state to sign it using 
      contract upgrade flows.
      Upgraded contracts must implement the `UpgradedContract` interface.

3. State and state schema.

   Two scenarios:
   
   a) Schema additions (backwards compatible)
   
   b) Schema deletions/type modifications require use of `ContractUpgradeFlow` and must define a new version of the `MappedSchema`

4. Serialisation of custom types.

   AMQP serialisation rules:
   - Constructor
   - JavaBean getter
   - Generics
   - Superclass (abstract)
   - Cyclic object graph cycles are not supported
   - Enums may not evolve!

Reference Corda Applications
----------------------------
The intent of the CTS is to incorporate samples, demos and customer driven "use case scenarios" into an ever growing
regression test suite.   

The following categories reflect our current ecosystem of CorDapps:

Corda Repository samples:
-------------------------
1) Trader demo
2) IRS demo
3) SIMM Valuation demo
4) Bank of Corda
5) Notary demo
6) Attachment demo
    
Corda.net samples (https://www.corda.net/samples/):
-----------------
General
   - Yo!
   - IOU 
   - Obligations (uses confidential identities)
   - Negotiation

Observers
   - Crowdfunding

Attachments
   - FTP
   - Blacklist

Confidential Identities
   - Whistle Blower

Oracles
   - Prime numbers
   - Options

Scheduled activities:
   - Heartbeat

Accessing external data:
   - Flow HTTP
   - Flow DB access

Upgrading CorDapps
   - Contract upgrades

Alternative node web-servers
   - Spring Webserver
   - Yo! CorDapp Spring Webserver

RPC Clients
   - NodeInfo
   - Ping-Pong

DevRel other
------------
   - https://github.com/CaisR3 

Corda Incubator/Accelerator demos:
---------------------------
   - Wildfire (https://bitbucket.org/R3-CEV/wildfire)
   - Ubin (https://github.com/project-ubin/ubin-corda) 
   - Reference data    
   

