Deploying an example CorDapp
============================

At this point we've set up the development environment, and have a sample CorDapp in an IntelliJ project. In this section, we'll deploy an instance of this CorDapp running on local nodes, including one notary, and two nodes, each representing one party in a transaction.

Steps
-----

Before continuing, ensure that you have


1. Navigate to the root directory of the sample CorDapp.

2. Deploy the CorDapp by running the following command: ``./gradlew deployNodes``.

3. To best understand the deployment process, there are several perspectives it is helpful to see. Run the following command: ``build/nodes/runnodes``

  This command opens three terminal windows: the notary, and a node each for PartyA and PartyB. A notary is a validation service that prevents double-spending and

4. Click the second terminal window to see the perspective of PartyA.

5. After the PartyA node has been successfully deployed, flows can be executed from the perspective of PartyA. To execute the **AWBDJAKLJWLDNLAWND** flow, run the following command: ``flow start <name> target: <name2>``

  A flow is the mechanism by which a transaction takes place using Corda. Flows are written as part of the CorDapp, and define the mechanisms by which parties transact.

6. To check whether PartyB has received the transaction, open the terminal window showing PartyB's perspective, and run the following command: ``run vaultQuery contractStateType:net.corda.<STUFF>``

  This command displays all of the STUFF states in the node's vault. States are immutable objects that represent shared facts between the parties. States serve as the inputs and outputs of transactions.

Next steps
----------

After deploying the sample CorDapp, a useful next step is to look into the contents of a CorDapp in more detail, to better understand the concepts of flow, state, transactions, and contracts, before writing your own CorDapp.
