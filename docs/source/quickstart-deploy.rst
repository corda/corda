Running the example CorDapp
===========================

At this point we've set up the development environment, and have an example CorDapp in an IntelliJ project. In this section, the CorDapp will be deployed to locally running Corda nodes.

The local Corda network includes one notary, and three nodes, each representing parties in the network. A Corda node is an individual instance of Corda representing one party in a network. For more information on nodes, see the `node documentation <./key-concepts-node.html>`_.

Before continuing, ensure that you've `set up your development environment <./quickstart-index.html>`_.

Step One: Deploy the CorDapp locally
------------------------------------

The first step is to deploy the CorDapp to nodes running locally.

1. Navigate to the root directory of the example CorDapp.

2. To deploy the nodes on Windows run the following command: ``gradlew clean deployNodes``

  To deploy the nodes on Mac or Linux run the following command: ``./gradlew clean deployNodes``

3. To best understand the deployment process, there are several perspectives it is helpful to see. On Windows run the following command: ``workflows-kotlin\build\nodes\runnodes``

  On Mac/Linux run the following command: ``workflows-kotlin/build/nodes/runnodes``

  This command opens four terminal windows: the notary, and a node each for PartyA, PartyB, and PartyC. A notary is a validation service that prevents double-spending, enforces timestamping, and may also validate transactions. For more information on notaries, see the `notary documentation <./key-concepts-notaries.html>`_.

  .. note::

    Maintain window focus on the node windows, if the nodes fail to load, close them using ``ctrl + d``. The ``runnodes`` script opens each node directory and runs ``java -jar corda.jar``.

4. Go through the tabs to see the perspectives of other network members.

Step Two: Run a CorDapp transaction
-----------------------------------

1. Open the terminal window for PartyA. From this window, any flows executed will be from the perspective of PartyA.

2. To execute the ``ExampleFlow.kt`` flow, run the following command: ``flow start ExampleFlow iouValue: 1, otherParty: PartyB``

   A flow is the mechanism by which a transaction takes place using Corda. This flow creates an instance of the IOU state, which requires an ``iouValue`` property. Flows are contained in CorDapps, and define the mechanisms by which parties transact. For more information on flows, see the `flow documentation <key-concepts-flows.html>`_.

3. To check whether PartyB has received the transaction, open the terminal window showing PartyB's perspective, and run the following command: ``run vaultQuery contractStateType: com.example.state.IOUState``

  This command displays all of the IOU states in the node's vault. States are immutable objects that represent shared facts between the parties. States serve as the inputs and outputs of transactions.

Next steps
----------

After deploying the example CorDapp, the next step is to start `writing a CorDapp <./quickstart-build.html>`_ containing your own contract, states, and flows.
