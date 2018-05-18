Deploying Corda to Corda Testnet from a Google Cloud Platform VM
====================================================

.. contents::

https://testnet.corda.network enables a self service download
link with a node preconfigured to join the Corda Testnet. This
document will describe how to set up a virtual machine on the Google
Cloud Platform (GCP) to deploy your pre-generated Corda node and automatically connnect
to Testnet.

Pre-requisites
--------------
* Ensure you have a registered Google Cloud Platform account with
  `billing enabled <https://cloud.google.com/billing/docs/how-to/manage-billing-account>` which can create virtual machines under your subscription(s) and you are logged on to the GCP console: `<https://console.cloud.google.com>`.


Deploy Corda node
-----------------

Browse to `<https://console.cloud.google.com>` and log in with your
Google credentials.

**STEP 1: Create a GCP Project**

In the project drop down click on the plus icon to create a new
project to house your Corda resources.

.. image:: resources/consolegcp.png
   :scale: 50 %

.. image:: resources/console2.png
   :scale: 50 %
   
.. image:: resources/newprojectgcp.png
   :scale: 50 %

Enter a project name and click Create.

**STEP 2: Launch the VM**

In the left hand side nav click on Compute Engine.

.. image:: resources/gcpcompute.png
   :scale: 50 %

Click on Create Instance.

.. image:: resources/consolegcpcreatevm.png
   :scale: 50 %

Fill in the form with the desired VM specs:

Recommended minimum 4vCPU with 15GB memory and 40GB Persistent disk.
Ubuntu 16.04 LTS.

Allow full API access.

Dont worry about firewall settings as we will configure those later. 

.. image:: resources/gcpconsolevmsettings.png
   :scale: 50 %

Click Create and wait a few sections for your instance to provision
and start running.

**STEP 3: Connect to your VM and set up the environment**

Once your instance is running click on the SSH button to launch a
cloud SSH terminal in a new window. 

.. image:: resources/gcpconsolelaunchssh.png
   :scale: 50 %
   
.. image:: resources/gcpshell.png
   :scale: 50 %

Now run the following to configure the firewall to allow Corda traffic

.. code:: bash

    gcloud compute firewall-rules create nodetonode --allow tcp:10002
    gcloud compute firewall-rules create nodetorpc --allow tcp:10003
    gcloud compute firewall-rules create webserver --allow tcp:8080


Next we promote the ephemeral IP address associated with this
instance to a static IP address.

First check the region and select the one you are using from the list:

.. code:: bash

    gcloud compute regions list

Find your external IP:

.. code:: bash

    gcloud compute addresses list

Then run this command with the ephemeral IP address as the argument to
the --addresses flag and the region:

.. code:: bash

    gcloud compute addresses create corda-node --addresses 35.204.53.61 --region europe-west4

**STEP 4: Download and set up your Corda node**

Now your GCP environment is configured you can switch to the Testnet 
web application and click on the copy to clipboard button to get a one
time installation script:


.. image:: resources/testnet-platform.png
   :scale: 50 %

You can generate as many Testnet identites as you like by refreshing
this page to generate a new one time link. 
	   
In your terminal paste the command you just copied to install and run
your unique Corda instance:

.. code:: bash

    ONE_TIME_DOWNLOAD_KEY=a1ecd6eb-62f8-4ccb-8ba6-e57b70bfc7fd sudo bash -c "$(curl -L https://cces.corda.r3cev.com/api/user/node/install.sh)"

You can now navigate to the external web address of the instance and
see any cordapps running on port 8080 (if you have any installed). 
