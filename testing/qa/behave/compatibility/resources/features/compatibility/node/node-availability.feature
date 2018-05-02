@compatibility @node @availability
Feature: Compatibility - Node high availability and operational continuity
  To support a highly available Corda network, a Corda node must have the ability to continue transacting with another Corda node
  when one node in an H/A cluster fails

  Scenario Outline: Corda (OS) node can transact with another Corda (OS) node where node configuration is changed on one of the Corda OS nodes
    Given a node A of version <Corda-Node-Version-X> in high-availability mode
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has the finance app installed
    When the network is ready
    Then node A can issue 1000 GBP
    And node A can transfer 100 GBP to node B
    And node A primary node fails
    And node A can transfer 100 GBP to node B

  Examples:
      | Corda-Node-Version-X         |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |