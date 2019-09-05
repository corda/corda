Notary backend set-up - CockroachDB
===================================

CockroachDB's `documentation page <https://www.cockroachlabs.com/docs/stable/install-cockroachdb-linux.html>`__ explains the installation in detail.

CockroachDB provides a replicated datastore with self healing capabilities, in case of node failure. It implements CockroachDB SQL, which is compatible 
with the PostgresSQL dialect. When communicating with CockroachDB, a PostgresSQL driver will be required.

Installation
~~~~~~~~~~~~

It is possible to compile CockroachDB from source, but for the purposes of this guide, we will be downloading the binary instead. Note that this guide assumes 
a Linux based operating system. See the documentation linked above for other operating systems.

.. code:: sh
  wget -qO- https://binaries.cockroachdb.com/cockroach-v19.1.2.linux-amd64.tgz | tar  xvz

The following step might require elevated permissions - use the sudo command if required

.. code:: sh
  cp -i cockroach-v19.1.2.linux-amd64/cockroach /usr/local/bin


Start a 3 Node Cluster
~~~~~~~~~~~~~~~~~~~~~~

Use the ``cockroach start`` command to start 3 nodes:

.. code:: sh
  cockroach start \
  --insecure \
  --store=fault-node1 \
  --listen-addr=localhost:26257 \
  --http-addr=localhost:8080 \
  --join=localhost:26257,localhost:26258,localhost:26259


In a new terminal window, start node 2:
.. code:: sh
  cockroach start \
  --insecure \
  --store=fault-node2 \
  --listen-addr=localhost:26258 \
  --http-addr=localhost:8081 \
  --join=localhost:26257,localhost:26258,localhost:26259


In a new terminal window, start node 3:
.. code:: sh
  cockroach start \
  --insecure \
  --store=fault-node3 \
  --listen-addr=localhost:26259 \
  --http-addr=localhost:8082 \
  --join=localhost:26257,localhost:26258,localhost:26259

Initialize the Cluster
~~~~~~~~~~~~~~~~~~~~~~

In a new terminal, use the ``cockroach init`` command to perform a one-time initialization of the cluster:
.. code:: sh
cockroach init --insecure --host=localhost:26257

Connect to the SQL Shell
~~~~~~~~~~~~~~~~~~~~~~~~

Use the following command to connect to the built-in SQL Shell of any of the nodes:

.. code:: sh
  cockroach sql --insecure --host=localhost:26257
  
With the shell open, one can now run the necessary SQL scripts to create users and initialize the schema.

Create the Corda User
~~~~~~~~~~~~~~~~~~~~~

.. code:: sql

  CREATE USER corda if not exists with password '{{ password }}';

Create the Database and Tables
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: sql

  create database if not exists corda;

  create table corda.notary_committed_states (
    state_ref varchar(73) not null, 
    consuming_transaction_id varchar(64) not null, 
    constraint id1 primary key (state_ref)
    );
    
  create table corda.notary_committed_transactions (
    transaction_id varchar(64) not null,
    constraint id2 primary key (transaction_id)
    );
    
  create table corda.notary_request_log (
    id varchar(76) not null,
    consuming_transaction_id varchar(64),
    requesting_party_name varchar(255),
    request_timestamp timestamp not null,
    request_signature bytea not null,
    constraint id3 primary key (id)
    );


The JDBC Url
~~~~~~~~~~~~

Since CockroachDB uses the PostgresSQL driver, the JDBC Url for it would be:

``jdbc:postgresql://{{host}}:{{port}}/{{databaseName}}``

Remember to store this URL inside the node's configuration in order to connect to the database.

In the next section, we're :doc:`installing-the-notary-service`.

