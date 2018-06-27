# Node Explorer

The node explorer provides views of the node's vault and transaction data using Corda's RPC framework.
The user can execute cash transaction commands to issue and move cash to other parties on the network or exit cash using the user interface.

## Running the UI

**Windows:**

    gradlew.bat tools:explorer:run

**Other:**

    ./gradlew tools:explorer:run
    

## Running Demo Nodes

A demonstration Corda network topology is configured with 5 nodes playing the following roles:
1. Notary
2. Issuer nodes (representing two fictional central banks - UK Bank Plc issuer of GBP and USA Bank Corp issuer of USD)
3. Participant nodes (representing two users - Alice and Bob)

The Issuer nodes have the ability to issue, move and exit cash amounts.
The Participant nodes are only able to spend cash (eg. move cash).

**Windows:**

    gradlew.bat tools:explorer:runDemoNodes

**Other:**

    ./gradlew tools:explorer:runDemoNodes

**These Corda nodes will be created on localhost:**

   * Notary (Does not accept logins)
   * UK Bank Plc (*Issuer node*)
   * USA Bank Corp (*Issuer node*)
   * Alice
   * Bob
   
Check the gradle output for the ports of the nodes - when they are ready to be used, a list of started nodes along
with their respective RPC ports will be printed.

Explorer login credentials to the Issuer nodes are defaulted to ``manager`` and ``test``.
Explorer login credentials to the Participant nodes are defaulted to ``user1`` and ``test``.
Please note you are not allowed to login to the notary.

## Running Simulation Nodes

Building on the demonstration Corda network topology described above, simulation mode performs continuous
issue, move and exit cash operations across all participant nodes.

**Windows:**

    gradlew.bat tools:explorer:runSimulationNodes

**Other:**

    ./gradlew tools:explorer:runSimulationNodes

## Running Flow Triage scenario

Once again, building on the demonstration Corda network topology described above, this scenario mode triggers 
an exception within a flow which can then be visualized using the "Flow Triage" panel within the Explorer.
The "Flow Triage" panel will be enhanced in the future to enable operators to take corrective actions upon flow failures 
(eg. retry, terminate, amend and replay)

**Windows:**

    gradlew.bat tools:explorer:runFlowTriageNodes

**Other:**

    ./gradlew tools:explorer:runFlowTriageNodes

## Business Network reference implementation

An additional "IOU" panel is now visible in the main Explorer dashboard to demonstrate the new Business Networks concept using a sample IOU product.

Business Networks are introduced in order to segregate Corda Nodes that do not need to transact with each other or indeed even know of each others existence.

Whenever an IOU transaction is entered, membership checks are performed to ensure that participants are included in the same
Business Network.
Upon startup both "Alice" and "Bob" are configured to be part of the same Business Network and therefore can transact with each other. 
The transactions panel been also extended to be able to display details of the IOU transactions.

Use the following Business Network configuration file to define what participants are included within its membership:
`samples\business-network-demo\build\resources\main\net\corda\sample\businessnetwork\membership\internal\AliceBobMembershipList.csv`
could be modified (no restart necessary) and then IOU transaction will no longer be possible between "Alice" and "Bob".

Please note Business Networks functionality only applies to the same IOU CorDapp, Cash payments CorDapp will allow transactions between
"Alice" and "Bob" at all times.

## TODOs:
- Shows more useful information in the dashboard.
- Improve Network View, display other nodes in the world map and show transactions between them.
- Add a new view showing node's state machines.
- Link transaction input to its originated transaction to make it easier to trace back.
- Shows Node information (Configuration, properties etc.) in Settings view. 
- Support other contract types.


More information can be found in the [Project website](https://corda.net) and [Documentation](https://docs.corda.net).