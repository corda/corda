Deploying a node
================

.. contents::

.. note:: These instructions are intended for people who want to deploy a Corda node to a server,
   whether they have developed and tested a CorDapp following the instructions in :doc:`generating-a-node`
   or are deploying a third-party CorDapp.

Linux (systemd): Installing and running Corda as a systemd service
------------------------------------------------------------------
We recommend creating systemd services to run a node and its webserver. This provides logging and service handling,
ensures the Corda service is run at boot, and means the Corda service stays running with no users connected to the
server.

**Prerequisites**:

   * Oracle Java 8

1. Create a directory called ``/opt/corda`` and change its ownership to the user you want to use to run Corda:

   ``mkdir /opt/corda; chown USER_WHO_RUNS_CORDA:GROUP_WHO_RUNS_CORDA /opt/corda``

2. Download the `Corda jar <https://r3.bintray.com/corda/net/corda/corda/2.0.0/corda-2.0.0.jar>`_ and place it in
   ``/opt/corda``

3. Create a directory called ``plugins`` in ``/opt/corda`` and save your CorDapp jar file to it. Alternatively, download one of
   our `sample CorDapps <https://www.corda.net/samples/>`_ to the ``plugins`` directory

4. Save the below as ``/opt/corda/node.conf``:

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

   *  Change the ``p2pAddress`` and ``rpcAddress`` values to start with your server's hostname or external IP address
   *  Change the ports if necessary
   *  Enter an email address which will be used as a technical administrative contact
   *  Enter your node's desired legal name

      * Organization (``O=``) should be a unique and meaningful identifier (e.g. Bank of Breakfast Tea)
      * Location (``L=``) is your nearest city
      * Country (``C=``) is the `ISO 3166-1 alpha-2 code <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>`_
   *  Change the RPC username and password

6. Create a ``corda.service`` file based on the example below and save it in the ``/etc/systemd/system/`` directory

    .. code-block:: shell

       [Unit]
       Description=Corda Node
       Requires=network.target

       [Service]
       Type=simple
       User=username
       WorkingDirectory=/opt/corda
       ExecStart=/usr/bin/java -Xmx2048m -jar /opt/corda/corda.jar
       Restart=on-failure

       [Install]
       WantedBy=multi-user.target

7. Make the following changes to ``corda.service``:

    * Change the username to the user account you want to use to run Corda. **We recommend that this is not root**
    * Set the maximum amount of memory available to the Corda process by changing the ``-Xmx2048m`` parameter

.. note:: The Corda webserver provides a simple interface for interacting with your installed CorDapps in a browser.
   Running the webserver is optional.

8. Create a ``corda-webserver.service`` file based on the example below and save it in the ``/etc/systemd/system/``
   directory.

    .. code-block:: shell

       [Unit]
       Description=Simple webserver for Corda
       Requires=network.target

       [Service]
       Type=simple
       User=username
       WorkingDirectory=/opt/corda
       ExecStart=/usr/bin/java -jar /opt/corda/corda-webserver.jar
       Restart=on-failure

       [Install]
       WantedBy=multi-user.target

9. Copy the required Java keystores to the node. See :doc:`permissioning`

10. You can now start a node and its webserver by running the following ``systemctl`` commands:

   * ``systemctl daemon-reload``
   * ``systemctl corda start``
   * ``systemctl corda-webserver start``

You can run multiple nodes by creating multiple directories and Corda services, modifying the ``node.conf`` and
``service`` files so they are unique.

Windows: Installing and running Corda as a Windows service
----------------------------------------------------------
We recommend running Corda as a Windows service. This provides service handling, ensures the Corda service is run
at boot, and means the Corda service stays running with no users connected to the server.

**Prerequisites**:

   * Oracle Java 8

1. Create a Corda directory and download the Corda jar. Here's an example using PowerShell:

   .. code-block:: PowerShell

        mkdir C:\Corda
        wget http://jcenter.bintray.com/net/corda/corda/2.0.0/corda-2.0.0.jar -OutFile C:\Corda\corda.jar

2. Create a directory called ``plugins`` in ``/opt/corda`` and save your CorDapp jar file to it. Alternatively,
   download one of our `sample CorDapps <https://www.corda.net/samples/>`_ to the ``plugins`` directory

3. Save the below as ``C:\Corda\node.conf``:

   .. code-block:: json

        basedir : "C:\\Corda"
        p2pAddress : "your-hostname.example.com:10002"
        rpcAddress : "your-hostname.example.com:10003"
        webAddress : "0.0.0.0:10004"
        h2port : 11000
        emailAddress: "you@example.com"
        myLegalName : "O=A Bank, L=London, C=GB"
        keyStorePassword : "cordacadevpass"
        trustStorePassword : "trustpass"
        extraAdvertisedServiceIds: [ "" ]
        useHTTPS : false
        devMode : false
        networkMapService {
            address="one-networkmap.corda.r3cev.com:10002"
            legalName="O=TestNet NetworkMap, L=Dublin, C=IE"
        }
        rpcUsers=[
            {
                user=corda
                password=your_password_here
                permissions=[
                    ALL
                ]
            }
        ]

4. Make the following changes to ``/opt/corda/node.conf``:

   *  Change the ``p2pAddress`` and ``rpcAddress`` values to start with your server's hostname or external IP address
   *  Change the ports if necessary
   *  Enter an email address which will be used as a technical administrative contact
   *  Enter your node's desired legal name

      * Organization (``O=``) should be a unique and meaningful identifier (e.g. Bank of Breakfast Tea)
      * Location (``L=``) is your nearest city
      * Country (``C=``) is the `ISO 3166-1 alpha-2 code <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>`_
   *  Change the RPC username and password

5. Copy the required Java keystores to the node. See :doc:`permissioning`

6. Download the `NSSM service manager <nssm.cc>`_

7. Unzip ``nssm-2.24\win64\nssm.exe`` to ``C:\Corda``

8. Save the following as ``C:\Corda\nssm.bat``:

   .. code-block:: batch

      nssm install corda C:\ProgramData\Oracle\Java\javapath\java.exe
      nssm set corda AppDirectory C:\Corda
      nssm set corda AppParameters "-jar corda.jar -Xmx2048m --config-file=C:\corda\node.conf"
      nssm set corda AppStdout C:\Corda\service.log
      nssm set corda AppStderr C:\Corda\service.log
      sc start corda

9. Run the batch file by clicking on it or from a command prompt

10. Run ``services.msc`` and verify that a service called ``corda`` is present and running

11. Run ``netstat -ano`` and check for the ports you configured in ``node.conf``

12. You may need to open the ports on the Windows firewall

Testing your installation
-------------------------
You can verify Corda is running by connecting to your RPC port from another host, e.g.:

        ``telnet your-hostname.example.com 10002``

If you receive the message "Escape character is ^]", Corda is running and accessible. Press Ctrl-] and Ctrl-D to exit
telnet.