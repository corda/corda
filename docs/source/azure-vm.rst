Building a Corda Network on Azure Marketplace
=============================================

To help you design, build and test Corda CorDapps a Corda network can be deployed on the `Microsoft Azure Marketplace <https://azure.microsoft.com/en-gb/overview/what-is-azure>`_

This Corda network offering builds a pre-configured network of Corda nodes as Ubuntu virtual machines (VM) comprising a Network Map Service node, Notary node and up to nine Corda nodes using a version of Corda that you choose. The simple Yo! CorDapp is pre-installed and helps you learn the basic principles of Corda. When you are ready to go further developing on Corda and start making contributions to the project head over to the `GitHub Repos <https://github.com/corda/>`_.

Pre-requisites
--------------
* Ensure you have a registered Microsoft Azure account and are logged on to the Azure portal.
* It is recommended you generate a private-public SSH key pair (see `here <https://www.digitalocean.com/community/tutorials/how-to-set-up-ssh-keys--2/>`_)


Deploying the VM
----------------

Search the Azure Marketplace for Corda.
Click the 'Create' button.

STEP 1: Basics

Define the basic parameters which will be used to pre-configure your Corda nodes.

* **Resource prefix**: Choose an appropriate descriptive name for your Corda nodes. This name will prefix the node hostnames
* **VM user name**: This is the user login name on the Ubuntu VM. Leave it as azureuser or define your own
* **Authentication type**: Select 'SSH public key', then paste the contents of your SSH public key file (see pre-requisites, above) into the box below. Alternatively select 'Password' to use a password of your choice to administer the VM
* **Restrict accedd by IP address**: Leave this as 'No'
* **Subscription**: Select which of your Azure subscriptions you want to use
* **Resource group**: Choose to 'Create new' and provide a useful name of your choice
* **Location**: Select the geographical location physically closest to you
 
.. image:: resources/azure_multi_node_step1.png
  :width: 300px

Click 'OK'

STEP 2: Network Size and Performance

Define the number of Corda nodes in your network and the size of VM.

* **Number of Network Map nodes**: There can only be one Network Map node in this network. Leave as '1'
* **Number of Notary nodes**: There can only be one Notary node in this network. Leave as '1'
* **Number of participant nodes**: This is the number of Corda nodes in your network. At least 2 is recommended (so you can send transactions between them) and there is a limit of 9
* **Storage performance**: Leave as 'Standard'
* **Virtual machine size**: It is recommended to use '4x Standard D1 v2' based on performance versus cost

.. image:: resources/azure_multi_node_step2.png
  :width: 300px
 
Click 'OK'

STEP 3: Corda Specific Options

Define the version of Corda you want on your nodes and the type of notary.

* **Corda version (as seen in Maven Central)**: Type the version of Corda you want your nodes to use. The version value must exactly match the directory name in `Maven Central <http://repo1.maven.org/maven2/net/corda/corda/>`_, for example 0.11.0
* **Notary type**: Select either 'Non Validating" (notary only checks whether a state has been previously used and marked as historic. Faster processing) or 'Validating' (notary performs transaction verification by seeing input and output states. Slower processing). More information on notaries can be found `here <https://vimeo.com/album/4555732/video/214138458>`_

.. image:: resources/azure_multi_node_step3.png
  :width: 300px
  
Click 'OK'

STEP 4: Summary

A summary of your selections is shown.

.. image:: resources/azure_multi_node_step4.png
  :width: 300px

Click 'OK'

The the deployment process will start and typically takes 8-10 minutes to complete.

Once deployed, click 'Overview' to see the virtual machine details. Note down the **Public IP address** for your Corda nodes. You will need these to connect to UI screens via your web browser:

.. image:: resources/azure_ip.png
  :width: 300px


Using the Yo! CorDapp
---------------------
The pre-loaded Yo! CordDapp on your Corda nodes lets you send simple Yo messages to other Corda nodes on the network. The Yo! CorDapp is running by default when your Corda nodes start.

Open a browser tabs and browse to the following URL:

.. sourcecode:: shell

	http://(public IP address):(port)/web/yo

where (public IP address) is the public IP address of one of your Corda nodes on the Azure Corda network and (port) is the web server port number for your Corda node, 10004 by default

You will be able to view the Yo! CordDapp web interface:

.. image:: resources/Yo_web_ui.png
  :width: 300px

* **Sending a Yo message via the web interface**

In the browser window type the following URL to send a Yo message to a target node on your Corda network:

.. sourcecode:: shell

	http://(public IP address):(port)/api/yo/yo?target=(legalname of target node)
	
where (public IP address) is the public IP address of one of your Corda nodes on the Azure Corda network and (port) is the web server port number for your Corda node, 10004 by default and (legalname of target node) is the Legal Name for the target node as defined in the node.conf file

* **Sending a Yo message via the shell**

You can send basic commands to your Corda node remotely using the `shell framework <https://docs.corda.net/shell.html>`_. 

Viewing the IRS demo
--------------------
The IRS demo creates three nodes: Bank A, Bank B and a node that runs a notary, a network map and an interest rates oracle together. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the time on a simulated clock passes. Each bank node listens on a different port - those used by the demo are:

**IRS demo ports:** **11005 (node A for Bank A)**, **11007 (node B for Bank B)**

Open two browser tabs and direct one to each of the following:

.. sourcecode:: shell

	http://localhost:11005/web/irsdemo
	http://localhost:11007/web/irsdemo
	
You will be able to see the nodes' view of the ledger.

.. image:: resources/azure_vm_10_52.png
  :width: 300px

Now let's take a look at how the interest rates oracle provides interest rates for a deal with a semi-annual payment frequency, and how the two counterparties to the trade see the same deal information on their own nodes, i.e. you see what I see.

1. In the browser tab for Bank A click 'Create Deal' from the top navigation bar
2. Modify the terms of the IRS deal, or leave as default
3. Click 'Submit' to create the deal
4. In the browser tab for Bank A click 'Recent Deals' from the top navigation bar to view the deal
5. In the browser tab for Bank B click 'Recent Deals' from the top navigation bar to view the deal. Compare the economic details to those shown in the Bank A tab

.. image:: resources/azure_vm_10_54.png
  :width: 300px


Viewing logs
------------
Users may wish to view the raw logs generated by each node, which contain more information about the operations performed by each node.

You can access these using an SSH client of your choice (e.g. Putty) and logging into the virtual machine using the public IP address.
Once logged in, navigate to 

.. sourcecode:: shell

	/opt/corda/logs

You can open log files with any text editor.

.. image:: resources/azure_vm_10_49.png
  :width: 300px
  
Next Steps
----------
Now you have taken a look at two Corda demos do go and visit the `dedicated Corda website <https://www.corda.net>`_

Or to get straight into the Corda open source codebase, head over to the `Github Corda repo <https://www.github.com/corda>`_
