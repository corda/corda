HA notary service set-up
========================

Double curly braces ``{{ }}`` are used to represent placeholder values throughout this guide.

HA notary implementations
-------------------------

There are two HA notary implementations available in Corda Enterprise:

  * The JPA notary - this is the standard HA notary and is recommended for all new deployments
  * The Percona notary - this is a legacy notary that connects specifically to a Percona/XtraDB (Percona) cluster

The JPA notary uses the Java Persistence API (JPA) interface to connect to the notary state DB. It has been tested against and confirmed to work with the following database versions in HA mode:

* CockroachDB version 19.1.2
* Oracle 12cR2 in an RAC configuration

And with the following database versions in non-HA mode:

* Percona version 5.7
* Postgres version 9.6
* CockroachDB version 19.1.2
* SQL Server 2017 CU13
* Oracle 11gR2
* Oracle 12cR2

It can also be configured to connect to other JPA-compliant databases.

The corresponding database driver must be in the form of a JAR file and located inside the "drivers"
folder of the notary service. Note that R3 does not provide database support. Should you require
support for your database, we recommend contacting your database vendor instead.

If CockroachDB is used as the database, each worker node requires a PostgreSQL JDBC driver to be
placed in the ``drivers`` directory to be able to communicate with the CockroachDB Cluster.
The official driver can be obtained from Maven or the `PostgreSQL JDBC Driver page <https://jdbc.postgresql.org/>`_.

.. _mysql_driver:

If Percona is used as the database, each worker node requires a MySQL JDBC driver to be placed in
the ``drivers`` directory to be able to communicate with the Percona XtraDB Cluster. The official
driver can be obtained from Maven or the
`MySQL Connector/J download page <https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-installing.html>`_.
Note that this is true regardless of whether the Percona notary implementation is used directly or
Percona is connected via JPA.

Prerequisites
-------------

* Java runtime
* Corda Enterprise JAR
* Notary Health-Check JAR
* Bootstrapper JAR (only required when setting up network without doorman and network map)
* Network Registration tool (only required when setting up a network with doorman and network map)
* Root access to a Linux machine or VM to install Percona
* The private IP addresses of your DB hosts (where we're going to install Percona)
* The public IP addresses of your notary hosts (in order to advertise these IPs for P2P traffic)

Your Corda Enterprise distribution should contain all the JARs listed above.

Networking
----------

Client nodes communicate with the notary cluster via P2P messaging, with the messaging layer
selecting an appropriate notary worker node by the service legal name. The notary worker P2P ports
should be reachable from the internet (or at least from the rest of the Corda network you're
building or joining). Client nodes connect to the notary cluster members round-robin.

Each notary worker needs access to its individual node database. It also communicates with the
underlying database cluster via JDBC.

The Percona nodes communicate with each other via group communication (GComm). The Percona
replicas should only be reachable from each other and from the worker nodes.

We recommend running the worker nodes and the Percona service in a joined private subnet, opening
up the P2P ports of the workers for external traffic.

Legal names and identities
--------------------------

Every notary worker node has two legal names:

* Its own legal name, specified in the node's configuration file as ``myLegalName`` (e.g ``O=Worker 1, C=GB, L=London``)
* The service legal name, specified in the node's configuration file by ``notary.serviceLegalName`` (e.g. ``O=HA Notary,C=GB, L=London``)

Only the service legal name is included in the network parameters. CorDapp developers should
select the notary service identity from the network map cache, for example:

.. code:: kotlin

  serviceHub.networkMapCache.getNotary(CordaX500Name("HA Notary", "London", "GB"))

Every notary worker's keystore contains the private key of both the node itself and the
private key of the notary service (with aliases ``identity-private-key`` and
``distributed-notary-private key`` in the keystore).

.. note:: 

  If you want to connect to a Corda network with a doorman and network map service,
  use the registration tool to create your service identity. In you want to set up a test network
  for development or a private network without using a doorman or network map, using the
  bootstrapper is recommended.

Expected data volume
--------------------

Non-validating notaries store roughly one kilobyte per transaction.

Security
--------

Credentials
~~~~~~~~~~~

Make sure you have the following credentials available, creating them if necessary and always
keeping them safe.

================================ ============================================================================================================
Password or Keystore             Description
================================ ============================================================================================================
database root password           used to create the Corda user, setting up the DB and tables (only required for some installation methods)
Corda DB user password           used by the notary service to access the DB
SST DB user password             used by the Percona cluster for data replication (SST stands for state snapshot transfer)
Network root truststore password (not required when using the bootstrapper)
Node keystore password           (not required when using the bootstrapper)
The network root truststore      (not required when using the bootstrapper)
================================ ============================================================================================================

Keys and certificates
---------------------

Notary keys are stored in the same way as for regular Corda nodes in the ``certificates``
directory. You can find out more in the :doc:`../permissioning` document.
