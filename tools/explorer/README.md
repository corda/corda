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

**These Corda nodes will be created on the following port on localhost.**

   * Notary -> 20005            (Does not accept logins)
   * UK Bank Plc -> 20011       (*Issuer node*)
   * USA Bank Corp -> 20008     (*Issuer node*)
   * Alice -> 20017
   * Bob -> 20014

Explorer login credentials to the Issuer nodes are defaulted to ``manager`` and ``test``.
Explorer login credentials to the Participants nodes are defaulted to ``user1`` and ``test``.
Please note you are not allowed to login to the notary.

## TODOs:
- Shows more useful information in the dashboard.
- Improve Network View, display other nodes in the world map and show transactions between them.
- Add a new view showing node's state machines.
- Link transaction input to its originated transaction to make it easier to trace back.
- Shows Node information (Configuration, properties etc.) in Settings view. 
- Support other contract types.


More information can be found in the [Project website](https://corda.net) and [Documentation](https://docs.corda.net).
