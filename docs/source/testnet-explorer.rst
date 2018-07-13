Using the Node Explorer to test a Corda Enterprise node on Corda Testnet
========================================================================

This document will explain how to test the installation of a Corda Enterprise node on Azure or AWS.


Prerequisites
-------------

This guide assumes you have deployed a Corda Enterprise node to either Azure or AWS using one of:

* Azure Resource Manager Templates (ARM Templates) on the `Azure Marketplace`_
* `AWS Quick Start Template`_

.. _`Azure Marketplace`: https://portal.azure.com/#blade/Microsoft_Azure_Marketplace/GalleryFeaturedMenuItemBlade/selectedMenuItemId/Blockchain_MP/resetMenuId/
.. _`AWS Quick Start Template`: https://aws.amazon.com/quickstart/

It also assumes your node is provisioned and connected to the Corda Testnet although the instructions below should work
for any Corda Enterprise node connected to any Corda network.


Get the testing tools
---------------------

To run the tests and make sure your node is connecting correctly to the network you will need to download and install a
couple of resources.

1. Log into your Cloud VM via SSH. 


2. Stop the Corda node(s) running on your cloud instance.

   .. code:: bash

       sudo systemctl stop corda
       
   .. warning:: If this is an HA node, make sure to stop both the hot and cold nodes before proceeding. Any database migration should be performed whilst both nodes are offline.

3. Copy the finance CorDapp from ``/home/ubuntu/apps/`` to ``/opt/corda/cordapps/``. 

   This is required to run some flows to check your connections, and to issue/transfer cash to counterparties.

   .. code:: bash

       sudo cp /home/ubuntu/apps/*.jar /opt/corda/cordapps/
    
   .. note:: 

    If you are not using a cloud template then you will need to download and manually install these files to the default install location. 
     
4. Create a symbolic link to the shared database driver folder

   .. code:: bash

       sudo ln -s /opt/corda/drivers /opt/corda/plugins
    
5. Execute the database migration. This is required so that the node database has the right schema for finance transactions defined in the installed CorDapp.

   .. code:: bash
   
       cd /opt/corda
       sudo java -jar /home/ubuntu/tools/corda-tools-database-manager-3.0.jar --base-directory /opt/corda --execute-migration

6. Add the following line to your ``node.conf``:

   .. code:: bash
   
       custom : { issuableCurrencies : [ USD ] }
   
   .. note:: Make sure that the config file is in the correct format, e.g., by ensuring that there's a comma at the end of the line prior to the added config.

7. Restart the Corda node:

   .. code:: bash

       sudo systemctl start corda

   Your node is now running the Finance Cordapp.
   
   .. note:: You can double-check that the CorDapp is loaded in the log file ``/opt/corda/logs/node-<VM-NAME>.log``. This file will list installed apps at startup.

8. Now download the Node Explorer to your local machine:

   A copy of the Enterprise node explorer is included in the ``/home/ubuntu/tools/`` directory of the VM. Run the following command from your local machine.

   .. code:: bash

       scp ubuntu@<IP>:tools/corda-tools-node-explorer-<VERSION>.jar .

   .. note:: The Enterprise Node Explorer is incompatible with open source versions of Corda and vice versa as they currently use different serialisation schemes (Kryo vs AMQP).

9. Run the Node Explorer tool on your local machine.

   .. code:: bash

       java -jar corda-tools-explorer-<VERSION>.jar

   .. image:: resources/explorer-login.png


Connect to the node
-------------------

To connect to the node you will need:

* The IP address of your node (the public IP of your cloud instance). You can find this in the instance page of your cloud console.
* The port number of the RPC interface to the node, specified in ``/opt/corda/node.conf`` in the ``rpcSettings`` section, (by default this is 10003 on Testnet).
* The username and password of the RPC interface of the node, also in the ``node.conf`` in the ``rpcUsers`` section, (by default the username is ``cordazoneservice`` on Testnet).

Click on ``Connect`` to log into the node.

Check your network identity and counterparties
----------------------------------------------

Once Explorer has logged in to your node over RPC click on the ``Network`` tab in the side navigation of the Explorer UI:

.. image:: resources/explorer-network.png

If your Enterprise node is correctly configured and connected to the Testnet then you should be able to see the identities of your node, the Testnet notary and the network map listing all the counterparties currently on the network. 


Test issuance transaction
-------------------------

Now we are going to try and issue some cash to a 'bank'. Click on the ``Cash`` tab. 

.. image:: resources/explorer-cash-issue1.png

Now click on ``New Transaction`` and create an issuance to a known counterparty on the network by filling in the form:

.. image:: resources/explorer-cash-issue2.png

Click ``Execute`` and the transaction will start.

.. image:: resources/explorer-cash-issue3.png

Click on the red X to close the notification window and click on ``Transactions`` tab to see the transaction in progress, or wait for a success message to be displayed:

.. image:: resources/explorer-transactions.png

Congratulations! You have now successfully installed a CorDapp and executed a transaction on the Corda Testnet.
