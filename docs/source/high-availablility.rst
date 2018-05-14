High Availability
=================

This section describes how to make a Corda node highly available.

Hot Cold
~~~~~~~~

In the hot cold configuration, failover is handled manually, by promoting the cold node after the former hot node
failed or was taken offline for maintenance.

For RPC clients there is a way to recover in case of failover, see section below.

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

:updateInterval: Period(milliseconds) over which the running node updates the mutual exclusion lease.

:waitInterval: Amount of time(milliseconds) to wait since last mutual exclusion lease update before being able to become the master node. This has to be greater than updateInterval.

RPC failover
------------

In case of hot-cold there will be a short period of time when none of the nodes available and accepting connections.
If the RPC client has not been connected at all and makes its first RPC connection during this instability window, the connection will be rejected
as if server address does not exists. The only choice client has in this case is to catch corresponding exception during ``CordaRPCClient.start()``
and keep on re-trying.

The following code snippet illustrates that.

.. sourcecode:: Kotlin

    fun establishConnectionWithRetry(nodeHostAndPort: NetworkHostAndPort, username: String, password: String): CordaRPCConnection {

        val retryInterval = 5.seconds

        do {
            val connection = try {
                logger.info("Connecting to: $nodeHostAndPort")
                val client = CordaRPCClient(
                        nodeHostAndPort,
                        object : CordaRPCClientConfiguration {
                            override val connectionMaxRetryInterval = retryInterval
                        }
                )
                val _connection = client.start(username, password)
                // Check connection is truly operational before returning it.
                val nodeInfo = _connection.proxy.nodeInfo()
                require(nodeInfo.legalIdentitiesAndCerts.isNotEmpty())
                _connection
            } catch(secEx: ActiveMQSecurityException) {
                // Happens when incorrect credentials provided - no point to retry connecting.
                throw secEx
            }
            catch(th: Throwable) {
                // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                logger.info("Exception upon establishing connection: " + th.message)
                null
            }

            if(connection != null) {
                logger.info("Connection successfully established with: $nodeHostAndPort")
                return connection
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
        } while (connection == null)

        throw IllegalArgumentException("Never reaches here")
    }

If, however, the RPC client was connected through load-balancer to a node and failover occurred it will take sometime for cold instance to start-up.
Acceptable behavior in this case would be for RPC client to keep re-trying to connect and once connected - back-fill any data that might have been missed since connection was down.
In a way this scenario is no different to a temporal loss of connectivity with a node even without any form of High Availability.

In order to achieve said re-try/back-fill functionality the client needs to install ``onError`` handler on the ``Observable`` returned by ``CordaRPCOps``.
Please see code below which illustrates how this can be achieved.

.. sourcecode:: Kotlin

    fun performRpcReconnect(nodeHostAndPort: NetworkHostAndPort, username: String, password: String) {

        val connection = establishConnectionWithRetry(nodeHostAndPort, username, password)
        val proxy = connection.proxy

        val (stateMachineInfos, stateMachineUpdatesRaw) = proxy.stateMachinesFeed()

        val retryableStateMachineUpdatesSubscription: AtomicReference<Subscription?> = AtomicReference(null)
        val subscription: Subscription = stateMachineUpdatesRaw
                .startWith(stateMachineInfos.map { StateMachineUpdate.Added(it) })
                .subscribe({ clientCode(it) /* Client code here */ }, {
                    // Terminate subscription such that nothing gets past this point to downstream Observables.
                    retryableStateMachineUpdatesSubscription.get()?.unsubscribe()
                    // It is good idea to close connection to properly mark the end of it. During re-connect we will create a new
                    // client and a new connection, so no going back to this one. Also the server might be down, so we are
                    // force closing the connection to avoid propagation of notification to the server side.
                    connection.forceClose()
                    // Perform re-connect.
                    performRpcReconnect(nodeHostAndPort, username, password)
                })

        retryableStateMachineUpdatesSubscription.set(subscription)
    }

In this code snippet it is possible to see that function ``performRpcReconnect`` creates RPC connection and installs error handler
upon subscription to an ``Observable``. The call to this ``onError`` handler will be made when failover happens then the code
will terminate existing subscription, closes RPC connection and recursively calls ``performRpcReconnect`` which will re-subscribe
once RPC connection comes back online.

Client code if fed with instances of ``StateMachineInfo`` using call ``clientCode(it)``. Upon re-connect this code receives
all the items. Some of these items might have already been delivered to client code prior to failover occurred.
It is down to client code in this case to have a memory and handle those duplicating items as appropriate.

Hot Warm
~~~~~~~~

In the future we are going to support automatic failover.
