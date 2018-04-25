@compatibility @nms
Feature: Compatibility - NMS node identity (nodeinfo) changes
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  after making changes to its node information: addresses, legalIdentitiesAndCerts, platformVersion

  Scenario Outline: Corda (OS) node can transact with another Corda (OS) node where a node adds new addresses to its nodeinfo
    Given a node A of version <Corda-Node-Version>
    And node A has the finance app installed
    And node A adds a new nodeinfo address
    And a node B of version <Corda-Node-Version>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

  Examples:
      | Corda-Node-Version           |
      | corda-3.0                    |
      | corda-3.1                    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |