Deploying a node
================

.. contents::

.. note:: These instructions are intended for people who want to deploy a Corda node to a server,
   whether they have developed and tested a CorDapp following the instructions in :doc:`generating-a-node`
   or are deploying a third-party CorDapp.

Installing and running Corda as a system service (Linux with systemd)
---------------------------------------------------------------------
We recommend creating systemd services to run a node and its webserver. This provides logging and service handling,
ensures the Corda service is run at boot, and means the Corda service stays running with no users connected to the
server.

1. Create a directory called ``/opt/corda`` and change its ownership to the user you want to use to run Corda:

   * ``mkdir /opt/corda; chown user:user /opt/corda``

2. Save the below as ``/opt/corda/node.conf``:

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

3. Make the following changes to ``/opt/corda/node.conf``:

   *  Change the ``p2pAddress`` and ``rpcAddress`` values to start with your server's hostname or external IP address
   *  Change the ports if necessary
   *  Enter an email address which will be used as a technical administrative contact
   *  Enter your node's desired legal name

      * Organization (``O=``) should be a unique and meaningful identifier (e.g. Bank of Breakfast Tea)
      * Location (``L=``) is your nearest city
      * Country (``C=``) is the `ISO 3166-1 alpha-2 code <https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2>`_

4. Create a ``corda.service`` file based on the example below and save it in the ``/etc/systemd/system/`` directory.

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

5. Make the following changes to ``corda.service``:

    * Change the username to the user account you want to use to run Corda. **We recommend that this is not root**
    * Set the maximum amount of memory available to the Corda process by changing the ``-Xmx2048m`` parameter

.. note:: The Corda webserver provides a simple interface for interacting with your installed CorDapps in a browser.
   Running the webserver is optional.

6. Create a ``corda-webserver.service`` file based on the example below and save it in the ``/etc/systemd/system/``
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

7. Copy the required Java keystores to the node. See :doc:`permissioning`.

8. You can now start a node and its webserver by running the following ``systemctl`` commands:

   * ``systemctl daemon-reload``
   * ``systemctl corda start``
   * ``systemctl corda-webserver start``

You can run multiple nodes by creating multiple directories and Corda services, modifying the ``node.conf`` and
``service`` files so they are unique.