@compatibility @node @cordapps
Feature: Compatibility testing of Options CorDapp (https://github.com/CaisR3/cordapp-option)
  To support an interoperable Corda network, different CorDapps must have the ability to transact in mixed Corda (OS) and R3 Corda (Enterprise) networks.

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node using the Options sample application.
    Given a validating notary Controller of version <Corda-Node-Version>
    And a node Issuer of version <Corda-Node-Version>
    And node Issuer has app installed: <Cordapp-Name>
    And a node PartyA of version <Corda-Node-Version> with proxy
    And node PartyA has app installed: <Cordapp-Name>
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has app installed: <Cordapp-Name>
    And a node Oracle in location New_York and country US of version <Corda-Node-Version>
    And node Oracle has app installed: <Cordapp-Name>
    When the network is ready
    And node PartyA has loaded app <Cordapp-Name>
    And node PartyB has loaded app <Cordapp-Name>
    Then node PartyA can self-issue 10000 USD
    And node PartyA vault contains total cash of 10000 USD
    And node PartyA can issue an option CALL 90 GBP 2022_04_12 Wilburton_State_Bank Issuer
    And node PartyA vault contains 1 <TradeType> states
    And node PartyA can trade option <TradeID> with node PartyB
#    And node PartyB vault contains 1 <TradeType> trade
#    And node PartyA vault contains 0 <TradeType> trade

    Examples:
      | Corda-Node-Version       | R3-Corda-Node-Version   | Cordapp-Name        | TradeType   | TradeID |
      | corda-3.0-pre-release-V3 | r3-master               | cordapp-option      | OptionState |         |
