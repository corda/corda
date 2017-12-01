Deploying a node
================

.. contents::

.. note:: These instructions are intended for people who want to deploy a Corda node to a server,
   whether they have developed and tested a CorDapp following the instructions in :doc:`generating-a-node`
   or are deploying a third-party CorDapp.

Linux (systemd): Installing and running Corda as a systemd service
------------------------------------------------------------------
We recommend creating systemd services to run a node and the optional webserver. This provides logging and service
handling, and ensures the Corda service is run at boot.

**Prerequisites**:

   * Oracle Java 8. The supported versions are listed in :doc:`getting-set-up`

1. Add a system user which will be used to run Corda:

    ``sudo adduser --system --no-create-home --group corda``

2. Create a directory called ``/opt/corda`` and change its ownership to the user you want to use to run Corda:

   ``mkdir /opt/corda; chown corda:corda /opt/corda``

3. Download the `Corda jar <https://r3.bintray.com/corda/net/corda/corda/>`_
   (under ``/VERSION_NUMBER/corda-VERSION_NUMBER.jar``) and place it in ``/opt/corda``

3. Create a directory called ``plugins`` in ``/opt/corda`` and save your CorDapp jar file to it. Alternatively, download one of
   our `sample CorDapps <https://www.corda.net/samples/>`_ to the ``plugins`` directory

4. Save the below as ``/opt/corda/node.conf``. See :doc:`corda-configuration-file` for a description of these options

   .. code-block:: json

      basedir : "/opt/corda"
      p2pAddress : "example.com:10002"
      rpcAddress : "example.com:10003"
      webAddress : "0.0.0.0:10004"
      h2port : 11000
      emailAddress : "you@example.com"
      myLegalName : "O=Bank of Breakfast Tea, L=London, C=GB"
      keyStorePassword : "cordacadevpass"
      trustStorePassword : "trustpass"
      useHTTPS : false
      devMode : false
      networkMapService {
          address="networkmap.foo.bar.com:10002"
          legalName="O=FooBar NetworkMap, L=Dublin, C=IE"
      }
      rpcUsers=[
          {
              user=corda
              password=portal_password
              permissions=[
                  ALL
              ]
          }
      ]

5. Make the following changes to ``/opt/corda/node.conf``:

   *  Change the ``p2pAddress`` and ``rpcAddress`` values to start with your server's hostname or external IP address.
      This is the address other nodes or RPC interfaces will use to communicate with your node
   *  Change the ports if necessary, for example if you are running multiple nodes on one server (see below)
   *  Enter an email address which will be used as an administrative contact during the registration process. This is
      only visible to the permissioning service
   *  Enter your node's desired legal name. This will be used during the issuance of your certificate and should rarely
      change as it should represent the legal identity of your node

      * Organization (``O=``) should be a unique and meaningful identifier (e.g. Bank of Breakfast Tea)
      * Location (``L=``) is your nearest city
      * Country (``C=``) is the `ISO 3166-1 alpha-2 code <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>`_
   *  Change the RPC username and password

6. Create a ``corda.service`` file based on the example below and save it in the ``/etc/systemd/system/`` directory

    .. code-block:: shell

       [Unit]
       Description=Corda Node - Bank of Breakfast Tea
       Requires=network.target

       [Service]
       Type=simple
       User=corda
       WorkingDirectory=/opt/corda
       ExecStart=/usr/bin/java -Xmx2048m -jar /opt/corda/corda.jar
       Restart=on-failure

       [Install]
       WantedBy=multi-user.target

7. Make the following changes to ``corda.service``:

    * Make sure the service description is informative - particularly if you plan to run multiple nodes.
    * Change the username to the user account you want to use to run Corda. **We recommend that this is not root**
    * Set the maximum amount of memory available to the Corda process by changing the ``-Xmx2048m`` parameter
    * Make sure the ``corda.service`` file is owned by root with the correct permissions:
        * ``sudo chown root:root /etc/systemd/system/corda.service``
        * ``sudo chmod 644 /etc/systemd/system/corda.service``

.. note:: The Corda webserver provides a simple interface for interacting with your installed CorDapps in a browser.
   Running the webserver is optional.

8. Create a ``corda-webserver.service`` file based on the example below and save it in the ``/etc/systemd/system/``
   directory.

    .. code-block:: shell

       [Unit]
       Description=Webserver for Corda Node - Bank of Breakfast Tea
       Requires=network.target

       [Service]
       Type=simple
       User=username
       WorkingDirectory=/opt/corda
       ExecStart=/usr/bin/java -jar /opt/corda/corda-webserver.jar
       Restart=on-failure

       [Install]
       WantedBy=multi-user.target

9. Provision the required certificates to your node. Contact the network permissioning service or see
   :doc:`permissioning`

10. You can now start a node and its webserver and set the services to start on boot by running the following ``systemctl`` commands:

   * ``sudo systemctl daemon-reload``
   * ``sudo systemctl enable --now corda``
   * ``sudo systemctl enable --now corda-webserver``

You can run multiple nodes by creating multiple directories and Corda services, modifying the ``node.conf`` and
``service`` files so they are unique.

Windows: Installing and running Corda as a Windows service
----------------------------------------------------------
We recommend running Corda as a Windows service. This provides service handling, ensures the Corda service is run
at boot, and means the Corda service stays running with no users connected to the server.

**Prerequisites**:

   * Oracle Java 8. The supported versions are listed in :doc:`getting-set-up`

1. Create a Corda directory and download the Corda jar. Replace ``VERSION_NUMBER`` with the desired version. Here's an
   example using PowerShell:

   .. code-block:: PowerShell

        mkdir C:\Corda
        wget http://jcenter.bintray.com/net/corda/corda/VERSION_NUMBER/corda-VERSION_NUMBER.jar -OutFile C:\Corda\corda.jar

2. Create a directory called ``plugins`` in ``/opt/corda`` and save your CorDapp jar file to it. Alternatively,
   download one of our `sample CorDapps <https://www.corda.net/samples/>`_ to the ``plugins`` directory

3. Save the below as ``C:\Corda\node.conf``. See :doc:`corda-configuration-file` for a description of these options

   .. code-block:: json

        basedir : "C:\\Corda"
        p2pAddress : "example.com:10002"
        rpcAddress : "example.com:10003"
        webAddress : "0.0.0.0:10004"
        h2port : 11000
        emailAddress: "you@example.com"
        myLegalName : "O=Bank of Breakfast Tea, L=London, C=GB"
        keyStorePassword : "cordacadevpass"
        trustStorePassword : "trustpass"
        extraAdvertisedServiceIds: [ "" ]
        useHTTPS : false
        devMode : false
        networkMapService {
                address="networkmap.foo.bar.com:10002"
                legalName="O=FooBar NetworkMap, L=Dublin, C=IE"
        }
        rpcUsers=[
            {
                user=corda
                password=portal_password
                permissions=[
                    ALL
                ]
            }
        ]

4. Make the following changes to ``C:\Corda\node.conf``:

   *  Change the ``p2pAddress`` and ``rpcAddress`` values to start with your server's hostname or external IP address.
      This is the address other nodes or RPC interfaces will use to communicate with your node
   *  Change the ports if necessary, for example if you are running multiple nodes on one server (see below)
   *  Enter an email address which will be used as an administrative contact during the registration process. This is
      only visible to the permissioning service
   *  Enter your node's desired legal name. This will be used during the issuance of your certificate and should rarely
      change as it should represent the legal identity of your node

      * Organization (``O=``) should be a unique and meaningful identifier (e.g. Bank of Breakfast Tea)
      * Location (``L=``) is your nearest city
      * Country (``C=``) is the `ISO 3166-1 alpha-2 code <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>`_
   *  Change the RPC username and password

5. Copy the required Java keystores to the node. See :doc:`permissioning`

6. Download the `NSSM service manager <nssm.cc>`_

7. Unzip ``nssm-2.24\win64\nssm.exe`` to ``C:\Corda``

8. Save the following as ``C:\Corda\nssm.bat``:

   .. code-block:: batch

      nssm install cordanode1 C:\ProgramData\Oracle\Java\javapath\java.exe
      nssm set cordanode1 AppDirectory C:\Corda
      nssm set cordanode1 AppParameters "-Xmx2048m -jar corda.jar --config-file=C:\corda\node.conf"
      nssm set cordanode1 AppStdout C:\Corda\service.log
      nssm set cordanode1 AppStderr C:\Corda\service.log
      nssm set cordanode1 Description Corda Node - Bank of Breakfast Tea
      nssm set cordanode1 Start SERVICE_AUTO_START
      sc start cordanode1

9. Modify the batch file:

    * If you are installing multiple nodes, use a different service name (``cordanode1``) for each node
    * Set the amount of Java heap memory available to this node by modifying the -Xmx argument
    * Set an informative description

10. Run the batch file by clicking on it or from a command prompt

11. Run ``services.msc`` and verify that a service called ``cordanode1`` is present and running

12. Run ``netstat -ano`` and check for the ports you configured in ``node.conf``

13. You may need to open the ports on the Windows firewall

Testing your installation
-------------------------
You can verify Corda is running by connecting to your RPC port from another host, e.g.:

        ``telnet your-hostname.example.com 10002``

If you receive the message "Escape character is ^]", Corda is running and accessible. Press Ctrl-] and Ctrl-D to exit
telnet.