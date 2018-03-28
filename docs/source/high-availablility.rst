High Availability
=================

This section describes how to make a Corda node highly available.

Hot Cold
~~~~~~~~

In the hot cold configuration, failover is handled manually, by promoting the cold node after the former hot node
failed or was taken offline for maintainance.

RPC clients have to handle ``RPCException`` and implement application specific recovery and retry.

Prerequisites
-------------

* A load-balancer for P2P, RPC and web traffic
* A shared file system for the artemis and certificates directories
* A shared database, e.g. Azure SQL

The hot-cold deployment consists of two Corda nodes, a hot node that is currently handling request and running flows
and a cold backup node that can take over, if the hot node fails or is taken offline for an upgrade. Both nodes should
be able to connect to a shared database and a replicated file-system hosting the artemis and certificates directories.
The hot-cold ensemble should be fronted by a load-balancer for P2P, web and RPC traffic. The load-balancer should do
health monitoring and route the traffic to the node that is currently active. To prevent data corruption in case of
accidental simultaneous start of both nodes, the current hot node takes a leader lease in the form of a mutual exclusion
lock implemented by a row in the shared database.

Configuration
-------------

The configuration snippet below shows the relevant settings.

.. sourcecode:: none

    enterpriseConfiguration = {
        mutualExclusionConfiguration = {
            on = true
            machineName = ${HOSTNAME}
            updateInterval = 20000
            waitInterval = 40000
        }
    }

Fields
------

:on: Whether hot cold high availability is turned on, defaulted to ``false``.

:machineName: Unique name for node.

:updateInterval: Rate(milliseconds) at which the running node updates the mutual exclusion lease.

:waitInterval: Amount of time(milliseconds) to wait since last mutual exclusion lease update before being able to become the master node. This has to be greater than updateInterval.

Hot Warm
~~~~~~~~

In the future we are going to support automatic failover.
