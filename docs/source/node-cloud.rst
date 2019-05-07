Corda Enterprise cloud images
=============================

Corda Enteprise is avaliable as a Virtual Machine image on AWS and Azure.
These are simple Linux VM images with a JDK supported by both cloud providers and Corda Enterprise.
Alongside the Corda Enterprise JAR the image contains an example `node.conf` file and `dbconfig.conf` file for H2 DB.
There is also the systemd service (called `corda`) ready to use.

Corda Enterprise for Azure
--------------------------

.. note:: Corda Enterprise for Azure is based on Ubuntu Linux and has Azul Enterprise OpenJDK installed.

Follow the standard Azure procedure to create a VM.
You can find more details at the Azure docs site: https://docs.microsoft.com/en-us/azure/virtual-machines/linux/.
Please select a VM type with more than 4GB of memory.

When the machine is ready, please log in to it using the credentials provided during deployment.

Next, change the session user to ``corda`` (alternatively, change the user to ``root`` to gain administrator privileges):

.. code-block:: shell

  sudo -u corda bash

Go to the Corda installation directory:

.. code-block:: shell

  cd /opt/corda/current

Review and adjust the content of the configuration files to your needs.
The main configuration file is the ``node.conf`` file and database specific configuration is stored in the ``dbconfig.conf`` file.
All Corda configuration parameters are described in :doc:`corda-configuration-file`.
Remember to adjust the ``p2paddress`` to match a FQDN or the public IP address of the VM.
The public IP address can be obtained from the shell using the following command:

.. code-block:: shell

  curl -H Metadata:true http://169.254.169.254/metadata/instance?api-version=2017-04-02| jq '.network.interface[0].ipv4.ipAddress[0].publicIpAddress'

Note that only the p2p port (10002) is opened by default on a Network Security Group attached to the VM.
To enable RPC communication from a remote machine firewall has to be adjusted.

For proudction usage, copy the required database drivers (e.g. Azure DB) into the ``drivers`` directory.
More information on database configuration can be found on :doc:`node-database`

Copy the selected CorDapps into the cordapps directory and their configuration to the ``cordapps/config`` subdirectory.

Copy the network root trust store for a Corda network you plan to join into the ``certificates`` directory.


Start the initial registration process with:


.. code-block:: shell

  java -jar corda.jar initial-registration -p <PASSWORD_FOR_NETWORK_ROOT_TRUSTORE>

After the node has registered, verify that all files in the ``/opt/corda/current`` directory (and its subdirectories) are owned by the ``corda`` user.
Then the systemd ``corda`` service can be started.

.. code-block:: shell

  exit #to leave corda user shell
  sudo chown -R corda:corda /opt/corda/current # to change file ownership
  sudo systemctl start corda

You can check the status of the ``corda`` service by running:

.. code-block:: shell

  sudo systemctl status corda


Corda Enterprise for AWS
--------------------------

.. note:: Corda Enterprise for AWS is based on Amazon Linux 2 and has Corretto JDK installed.

Follow the standard AWS procedure to install VM.
You can find more details at the AWS docs site: https://aws.amazon.com/getting-started/tutorials/launch-a-virtual-machine/.
Please select a VM type with more than 4GB of memory.

When the machine is ready, please log in to it using the credentials provided during deployment.

Next, change the session user to ``corda`` (alternatively, change the user to ``root`` to gain administrator privileges):

.. code-block:: shell

  sudo -u corda bash

Go to the Corda installation directory:

.. code-block:: shell

  cd /opt/corda/current

Review and adjust the content of the configuration files to your needs.
The main configuration file is the ``node.conf`` file and database specific configuration is stored in the ``dbconfig.conf`` file.
All Corda configuration parameters are described in :doc:`corda-configuration-file`.
Remember to adjust the ``p2paddress`` to match a FQDN or the public IP address of the VM.
The public IP address can be obtained from the shell using the following command:

.. code-block:: shell

  curl http://169.254.169.254/latest/meta-data/public-ipv4

Note that only the p2p port (10002) is opened by default in a Security Group attached to the VM.
To enable RPC communication from a remote machine firewall has to be adjusted.

For proudction usage, copy the required database drivers into the ``drivers`` directory.
More information on database configuration can be found at :doc:`node-database`

Copy the selected CorDapps into the cordapps directory and their configuration to the ``cordapps/config`` subdirectory.

Copy the network root trust store for a Corda network you plan to join into the ``certificates`` directory.


Start the initial registration process with:


.. code-block:: shell

  java -jar corda.jar initial-registration -p <PASSWORD_FOR_NETWORK_ROOT_TRUSTORE>

After the node has registered, verify that all files in the ``/opt/corda/current`` directory (and its subdirectories) are owned by the ``corda`` user.
Then the systemd ``corda`` service can be started.

.. code-block:: shell

  exit #to leave corda user shell
  sudo chown -R corda:corda /opt/corda/current # to change file ownership
  sudo systemctl start corda

You can check the status of the ``corda`` service by running:

.. code-block:: shell

  sudo systemctl status corda
