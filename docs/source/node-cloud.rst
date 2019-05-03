Corda Enterprise cloud images
==========================================

Corda Enteprise is avaliable as a Virtual Machine image on AWS and Azure.
These are simple Linux VM with a JDK supported by a cloud provider and Corda Enterprise.
Along Corda Enterprise jar there is example node.conf file and dbconfig.conf file for H2 DB.
There is also the systemd service (called corda) ready to use.

Corda Enterprise for Azure
--------------------------

.. note:: Corda Enterprise for Azure based on Ubuntu Linux and has Azul Enterprise OpenJDK installed.

Follow the standard Azure procedure to install VM. You can find more details at Azure docs site: https://docs.microsoft.com/en-us/azure/virtual-machines/linux/. Please select a VM type with more than 4GB of memory.

When the machine is ready, please login to it using the credentials provided during deployment.

Next, become dedicated user 'corda' (alternatively you might become 'root' administrator):

.. code-block:: shell

  sudo -u corda bash

Go to corda directory:

.. code-block:: shell

  cd /opt/corda/current

Review and adjust the content of the configuration files to your needs.
The main configuration file is the 'node.conf' file and database specific configuration is stored in the 'dbconfig.conf' file.
All Corda configuration parameters are described in :doc:`corda-configuration-file`.
Remember to adjust the 'p2paddress' to a public IP address or FQDN.
The public IP address can be obtained from the shell using the following command:

.. code-block:: shell

  curl -H Metadata:true http://169.254.169.254/metadata/instance?api-version=2017-04-02| jq '.network.interface[0].ipv4.ipAddress[0].publicIpAddress'

In the same time keep the RPC addresses to one of the IP addresses of the VM. (Preconfigured value of 0.0.0.0 is fine).
Note that only p2p port (10002) is opened by default on an Azure firewall attached to the VM.
To enable RPC communication from a remote machine firewall has to be adjusted.

Copy selected DB drivers for production quality DB (e.g. Azure DB) into the 'drivers' directory.
More information on DB configuration can be found on :doc:`node-database`

Copy selected CorDapps into the cordapps directory and their configuration to cordapps/config subdirectory.

Copy the network root trust store for a Corda network you plan to join into the 'certificates' directory.


Start the initial registration process with:


.. code-block:: shell

  java -jar corda.jar initial-registration -p <PASSWORD_FOR_NETWORK_ROOT_TRUSTORE>

When the node registers the corda systemd process can be started.
Before that, it's worth to ensure that all files in /opt/corda/current are owned by user 'corda'.

.. code-block:: shell

  exit #to leave corda user shell
  sudo chown -R corda:corda /opt/corda/current # to change file ownership
  sudo systemctl start corda

You can check the status of corda service by running:

.. code-block:: shell

  sudo systemctl status corda


Corda Enterprise for AWS
--------------------------

.. note:: Corda Enterprise for Azure based on Amazon Linux 2 and has Corretto JDK installed.

Follow the standard Azure procedure to install VM. You can find more details at Azure docs site: https://aws.amazon.com/getting-started/tutorials/launch-a-virtual-machine/.
Please select a VM type with more than 4GB of memory.

When the machine is ready, please login to it using the credentials provided during deployment.

Next, become dedicated user 'corda' (alternatively you might become 'root' administrator):

.. code-block:: shell

  sudo -u corda bash

Go to corda directory:

.. code-block:: shell

  cd /opt/corda/current

Review and adjust the content of the configuration files to your needs.
The main configuration file is the 'node.conf' file and database specific configuration is stored in the 'dbconfig.conf' file.
All Corda configuration parameters are described in :doc:`corda-configuration-file`.
Remember to adjust the 'p2paddress' to a public IP address or FQDN.
The public IP address can be obtained from the shell using the following command:

.. code-block:: shell

  curl http://169.254.169.254/latest/meta-data/public-ipv4

In the same time keep the RPC addresses to one of the IP addresses of the VM.
(Preconfigured value of 0.0.0.0 is fine).
Note that only p2p port (10002) is opened by default in a Security Group attached to the VM.
To enable RPC communication from a remote machine firewall has to be adjusted.

Copy selected DB drivers for production quality DB into the 'drivers' directory.
More information on DB configuration can be found on :doc:`node-database`

Copy selected CorDapps into the cordapps directory and their configuration to cordapps/config subdirectory.

Copy the network root trust store for a Corda network you plan to join into the 'certificates' directory.


Start the initial registration process with:


.. code-block:: shell

  java -jar corda.jar initial-registration -p <PASSWORD_FOR_NETWORK_ROOT_TRUSTORE>

When the node registers the corda systemd process can be started.
Before that, it's worth to ensure that all files in /opt/corda/current are owned by user 'corda'.

.. code-block:: shell

  exit #to leave corda user shell
  sudo chown -R corda:corda /opt/corda/current # to change file ownership
  sudo systemctl start corda

You can check the status of corda service by running:

.. code-block:: shell

  sudo systemctl status corda
