@compatibility @notary
Feature: Compatibility - Notary cluster member changes.
  To support an interoperable Corda network, a Corda OS node must have the ability to transact with an R3 Corda Notary cluster.

  Scenario Outline: Corda (OS) health checker node can interact with R3 Corda RAFT notary cluster.
    Given a node PartyA of version <Corda-Node-Version>
    And node PartyA has app installed: <Cordapp-Name>
    And a 3 node validating RAFT notary cluster of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can run <Cordapp-Name> <NumIterations> <SleepMillis>

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Cordapp-Name      | NumIterations | SleepMillis |
      | corda-3.0          | r3-master               | notaryhealthcheck | 10            | 200         |
