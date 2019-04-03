# Node Explorer

The node explorer provides views of the node's vault and transaction data using Corda's RPC framework.
The user can execute cash transaction commands to issue and move cash to other parties on the network or exit cash using the user interface.

## Running the UI

**Windows:**

    gradlew.bat tools:explorer:run

**Other:**

    ./gradlew tools:explorer:run
    
## Running Demo Nodes

Node Explorer is included with the [DemoBench](https://docs.corda.net/demobench.html) application,
which allows you to create local Corda networks on your desktop. For example:

    * Notary
    * Bank of Breakfast Tea    (*Issuer node* for GBP)
    * Bank of Big Apples       (*Issuer node* for USD)
    * Alice                    (*Participant node* for user Alice)
    * Bob                      (*Participant node* for user Bob)

DemoBench will deploy all nodes with Corda's Finance CorDapp automatically, and allow you to launch an
instance of Node Explorer for each.

## TODOs:
- Shows more useful information in the dashboard.
- Improve Network View, display other nodes in the world map and show transactions between them.
- Add a new view showing node's state machines.
- Link transaction input to its originated transaction to make it easier to trace back.
- Shows Node information (Configuration, properties etc.) in Settings view. 
- Support other contract types.


More information can be found in the [Project website](https://corda.net) and [Documentation](https://docs.corda.net).
