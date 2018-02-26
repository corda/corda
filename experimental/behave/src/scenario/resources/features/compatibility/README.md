
Compatibility / Interoperability testing refers validating the run-time behaviour of different versions of  
components within the Corda Platform at three different levels:

1. Corda Network (Compatibility Zone - level)
2. Corda Node
3. CorDapps 

Corda Network
=============
See 
(1) https://docs.corda.net/head/permissioning.html
(2) https://docs.corda.net/head/setting-up-a-corda-network.html
(3) https://docs.corda.net/head/network-map.html
(4) https://docs.corda.net/head/versioning.html

- Permissioning: provision of an identity certificate signed by the networks root CA
- Network Parameters changes (such as adding/removing a Notary, whitelisting CorDapps for constraint checking),
  and distribution to nodes via bootstrapper (OS) / HTTP NMS distribution (Enterprise)
  Log check for: "Downloaded new network parameters" (see `ParametersUpdateInfo` and associated workflow defined in https://docs.corda.net/head/network-map.html#network-parameters-update-process)
- Nodes and Oracles addition and removal (with provisioned identities) via bootstrapping / registration with Network Map
    a) OS: `nodeInfo` distribution into a node's `additional-node-infos` directory
    b) Enterprise: HTTP NMS (node configured with `compatibilityZoneURL`)
- Versioning: `platformVersion` (part of a node's `NodeInfo`)

Corda Node
==========
See 
(1) https://docs.corda.net/head/corda-configuration-file.html
(2) Startup options ???

- Node Configuration changes (such as change of database, rpcUser permissions, address changes, standalone broker)
  Beware of `devMode` (uses pre-configured keystores, no security)
  
- Node Start options:

  Operational modes:
    --initial-registration 
    --just-generate-node-info
    --bootstrap-raft-cluster
    
  Optional parameters:
    --base-directory (default .)
    --network-root-truststore <Path>  (default: certificates/network-root-truststore.jks)
    --network-root-truststore-password
    --config-file (default `node.conf`)
    --log-to-console (print to console as well as to file)
    --logging-level ([ERROR,WARN,INFO,DEBUG,TRACE])  (default INFO)    
    --no-local-shell
    --sshd
    
    Others:
     --version
     --help    
       
     
Corda Application (CorDapp) 
=================
See https://docs.corda.net/head/upgrading-cordapps.html

The following different elements of a CorDapp may change:
- States
- Contracts
- Services
- Flows
- Utilities and library functions (eg. cryptography)

Compatibility testing takes the following types of version change into consideration:
1. Flow
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
   
2. State and contract

   Two types of contract/state upgrade:
   a) Implicit, using CZ Whitelisting of multiple CorDapp version (subsequently enforced by contract constraint logic)
   b) Explicit, by creating a special contract upgrade transaction and getting all participants of a state to sign it using 
      contract upgrade flows.
      Upgraded contracts must implement the `UpgradedContract` interface.

3. State and state schema

   Two scenarios:
   a) Schema additions (backwards compatible)
   b) Schema deletions/type modifications require use of `ContractUpgradeFlow` and must define a new version of the `MappedSchema`

4. Serialisation of custom types

   AMQP serialisation rules:
   - Constructor
   - JavaBean getter
   - Generics
   - Superclass (abstract)
   - Cyclic object graph cycles are not supported
   - Enums may not evolve!

Corda Applications
==================
Corda Repository samples:
-------------------------
    1) Trader demo
    2) IRS demo
    3) SIMM Valuation demo
    4) Bank of Corda
    5) Notary demo
    6) Attachment demo
    7) Network visualiser
    8) Cordapp Configuration (???)
    
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
    -Ping-Pong

DevRel other
------------
https://github.com/CaisR3 

Corda Incubator/Accelerator demos:
---------------------------
Wildfire (https://bitbucket.org/R3-CEV/wildfire)
Ubin (https://github.com/project-ubin/ubin-corda) 
Reference data


    

    
   

