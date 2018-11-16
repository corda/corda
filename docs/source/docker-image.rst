Official Corda Docker Image
===========================

Running a Node connected to a CompatibilityZone in Docker
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. note:: Requirements: A valid node.conf and a valid set of certificates - (signed by the CZ)

In this example, the certificates are stored at ``/home/user/cordaBase/certificates``, the node.conf is ``/home/user/cordaBase/config/node.conf`` and the CordApps to run are in ``/home/TeamCityOutput/cordapps``

.. code-block:: shell

    docker run -ti \
            --memory=2048m \
            --cpus=2 \
            -v /home/user/cordaBase/config:/etc/corda \
            -v /home/user/cordaBase/certificates:/opt/corda/certificates \
            -v /home/user/cordaBase/persistence:/opt/corda/persistence \
            -v /home/user/cordaBase/logs:/opt/corda/logs \
            -v /home/TeamCityOutput/cordapps:/opt/corda/cordapps \
            corda/corda-4.0-snapshot:latest

As the node runs within a container, several mount points are required

1. Cordapps - cordapps must be mounted at location ``/opt/corda/cordapps``
2. Certificates - certificates must be mounted at location ``/opt/corda/certificates``
3. Config - the node config must be mounted at location ``/etc/corda/node.config``
4. Logging - all log files will be written to location ``/opt/corda/logs``

If using the H2 database

5. Persistence - the folder to hold the H2 db files must be mounted at location ``/opt/corda/persistence``

Running a Node connected to a Bootstrapped Network
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

