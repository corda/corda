Node Explorer
=============

.. note:: To run Node Explorer on your machine, you will need JavaFX for Java 8. If you don't have JavaFX
          installed, you can either download and build your own version of OpenJFK, or use a pre-existing
          build, like the one offered by Zulu. They have community builds of OpenJFX for Window, macOS and Linux
          available on their `website <https://www.azul.com/downloads/zulu/zulufx/>`_.

The node explorer provides views into a node's vault and transaction data using Corda's RPC framework.
The user can execute cash transaction commands to issue and move cash to other parties on the network or exit cash (eg. remove from the ledger)

Running the UI
--------------
**Windows**::

    gradlew.bat tools:explorer:run

**Other**::

    ./gradlew tools:explorer:run

.. note:: In order to connect to a given node, the node explorer must have access to all CorDapps loaded on that particular node.
          By default, it only has access to the finance CorDapp.
          All other CorDapps present on the node must be copied to a ``cordapps`` directory located within the directory from which the node explorer is run.

Running demo nodes
------------------

Node Explorer is included with the :doc:`demobench` application, which allows
you to create local Corda networks on your desktop. For example:

    * Notary
    * Bank of Breakfast Tea    (*Issuer node* for GBP)
    * Bank of Big Apples       (*Issuer node* for USD)
    * Alice                    (*Participant node*, for user Alice)
    * Bob                      (*Participant node*, for user Bob)

DemoBench will deploy all nodes with Corda's Finance CorDapp automatically, and
allow you to launch an instance of Node Explorer for each. You will also be logged
into the Node Explorer automatically.

When connected to an *Issuer* node, a user can execute cash transaction commands to issue and move cash to itself or other
parties on the network or to exit cash (for itself only).

When connected to a *Participant* node a user can only execute cash transaction commands to move cash to other parties on the network.

The Node Explorer is also available as a stand-alone JavaFX application. It is
available from the Corda repositories as ``corda-tools-explorer``, and can be
run as

    java -jar corda-tools-explorer.jar

.. note:: Use the Explorer in conjunction with the Trader Demo and Bank of Corda samples to use other *Issuer* nodes.

Interface
---------
Login
  User can login to any Corda node using the explorer.
  Corda node address, username and password are required for login, the address is defaulted to localhost:0 if left blank.
  Username and password can be configured via the ``rpcUsers`` field in node's configuration file.
  
.. image:: resources/explorer/login.png
   :scale: 50 %
   :align: center
     
Dashboard
  The dashboard shows the top level state of node and vault.
  Currently, it shows your cash balance and the numbers of transaction executed.
  The dashboard is intended to house widgets from different CordApps and provide useful information to system admin at a glance. 

.. image:: resources/explorer/dashboard.png
  
Cash
  The cash view shows all currencies you currently own in a tree table format, it is grouped by issuer -> currency.
  Individual cash transactions can be viewed by clicking on the table row. The user can also use the search field to narrow down the scope.

.. image:: resources/explorer/vault.png

New Transactions
  This is where you can create new cash transactions.
  The user can choose from three transaction types (issue, pay and exit) and any party visible on the network.

  General nodes can only execute pay commands to any other party on the network.

.. image:: resources/explorer/newTransactionCash.png

Issuer Nodes
  Issuer nodes can execute issue (to itself or to any other party), pay and exit transactions.
  The result of the transaction will be visible in the transaction screen when executed.

.. image:: resources/explorer/newTransactionIssuer.png

Transactions
  The transaction view contains all transactions handled by the node in a table view. It shows basic information on the table e.g. Transaction ID, 
  command type, USD equivalence value etc. User can expand the row by double clicking to view the inputs, 
  outputs and the signatures details for that transaction.  
  
.. image:: resources/explorer/transactionView.png

Network
  The network view shows the network information on the world map. Currently only the user's node is rendered on the map. 
  This will be extended to other peers in a future release.
  The map provides an intuitive way of visualizing the Corda network and the participants. 

.. image:: resources/explorer/network.png


Settings
  User can configure the client preference in this view.

.. note:: Although the reporting currency is configurable, FX conversion won't be applied to the values as we don't have an FX service yet.


.. image:: resources/explorer/settings.png
