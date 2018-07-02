================================
Percona, the underlying Database
================================

Percona's `documentation page <https://www.percona.com/doc>`__ explains the installation in detail.

In this section we're setting up a
three-node Percona cluster.  A three-node cluster can tolerate one crash
fault. In production, you probably want to run five nodes, to be able to 
tolerate up to two faults.

Host names and IP addresses used in the example are listed in the table below.

========= ========
Host      IP
========= ========
percona-1 10.1.0.1
percona-2 10.1.0.2
percona-3 10.1.0.3
========= ========

Installation
============

Percona provides repositories for the YUM and APT package managers.
Alternatively you can install from source. For simplicity, we are going to
install Percona using the default data directory ``/var/lib/mysql``.

.. note::

  The steps below should be run on all your Percona nodes, unless otherwise
  mentioned. You should write down the host names or IP addresses of all your
  Percona nodes before starting the installation, to configure the data
  replication and later to configure the JDBC connection of your notary
  cluster.

Run the commands below on all nodes of your Percona cluster to configure the
Percona repositories and install the service.

.. code:: sh
  
  wget https://repo.percona.com/apt/percona-release_0.1-4.$(lsb_release -sc)_all.deb
  sudo dpkg -i percona-release_0.1-4.$(lsb_release -sc)_all.deb
  sudo apt-get update
  sudo apt-get install percona-xtradb-cluster-57

The service will start up automatically after the installation, you can confirm that the service is
running with ``service mysql status``, start the service with ``sudo service mysql start`` and stop with
``sudo service mysql stop``.

Configuration
=============

Configure the MySQL Root Password (if necessary)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Some distributions allow root access to the database through a Unix domain socket, others
require you to find the temporary password in the log file and change it upon
first login.

Stop the Service
^^^^^^^^^^^^^^^^

.. code:: sh

  sudo service mysql stop

Setup replication
^^^^^^^^^^^^^^^^^

Variables you need to change from the defaults are listed in the table below.

======================  ===========================================================  ==========================================================
Variable Name           Example                                                      Description                   
======================  ===========================================================  ==========================================================
wsrep_cluster_address   gcomm://10.1.0.1,10.1.0.2,10.1.0.3                           The addresses of all the cluster nodes (host and port)
wsrep_node_address      10.1.0.1                                                     The address of the Percona node
wsrep_cluster_name      notary-cluster-1                                             The name of the Percona cluster
wsrep_sst_auth          username:password                                            The credentials for SST
wsrep_provider_options  "gcache.size=8G"                                             Replication options
======================  ===========================================================  ==========================================================

Configure all replicas via
``/etc/mysql/percona-xtradb-cluster.conf.d/wsrep.cnf`` as shown in the template
below. 

.. literalinclude:: resources/wsrep.cnf
   :caption: wsrep.cnf
   :name: wsrep-cnf

The file ``/etc/mysql/percona-xtradb-cluster.conf.d/mysqld.cnf`` contains additional settings like the data directory. We're assuming
you keep the default ``/var/lib/mysql``.

Configure AppArmor, SELinux or other Kernel Security Module
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you're changing the location of the database data directory, you might need to
configure your security module accordingly.

On the first Percona node
^^^^^^^^^^^^^^^^^^^^^^^^^

Start the Database
~~~~~~~~~~~~~~~~~~

.. code:: sh

  sudo /etc/init.d/mysql bootstrap-pxc


Watch the logs using ``tail -f /var/log/mysqld.log``. Look for a log entry like
``WSREP: Setting wsrep_ready to true``.

Create the Corda User
~~~~~~~~~~~~~~~~~~~~~

.. code:: sql

  CREATE USER corda IDENTIFIED BY '{{ password }}';

Create the Database and Tables
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: sql

  CREATE DATABASE corda;
 
  CREATE TABLE IF NOT EXISTS corda.notary_committed_states (
                                issue_transaction_id BINARY(32) NOT NULL,
                                issue_transaction_output_id INT UNSIGNED NOT NULL,
                                consuming_transaction_id BINARY(32) NOT NULL,
                                CONSTRAINT id PRIMARY KEY (issue_transaction_id, issue_transaction_output_id)
                                );
         
  GRANT SELECT, INSERT ON corda.notary_committed_states TO 'corda';
         
  CREATE TABLE IF NOT EXISTS corda.notary_request_log (
                                consuming_transaction_id BINARY(32) NOT NULL,
                                requesting_party_name TEXT NOT NULL,
                                request_signature BLOB NOT NULL,
                                request_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                request_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                CONSTRAINT rid PRIMARY KEY (request_id)
                                );
         
  GRANT INSERT ON corda.notary_request_log TO 'corda';

Create the SST User
~~~~~~~~~~~~~~~~~~~

.. code:: sql
  
  CREATE USER ‘{{ sst_user }}’@’localhost’ IDENTIFIED BY ‘{{ sst_pass }}‘;
  GRANT RELOAD, LOCK TABLES, PROCESS, REPLICATION CLIENT ON *.* TO ‘{{ sst_user }}’@’localhost’;
  FLUSH PRIVILEGES;


On all other Nodes
^^^^^^^^^^^^^^^^^^

Once you have updated the ``wsrep.cnf`` on all nodes, start MySQL on all the
remaining nodes of your cluster. Run this command on all nodes of your cluster,
except the first one. The config file is shown `above <#wsrep-cnf>`__.

.. code:: sh

  service mysql start

Watch the logs using ``tail -f /var/log/mysqld.log``. Make sure you can start
the MySQL client on the command line and access the ``corda`` database on all
nodes.

.. code:: sh

  mysql
  mysql> use corda;
  # The output should be `Database changed`.

In the next section, we're :doc:`installing-the-notary-service`. You can read about :doc:`operating-percona` in a later section of this tutorial.

