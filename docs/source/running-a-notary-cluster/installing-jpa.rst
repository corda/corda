Notary backend set-up - JPA
===========================

Prior to using the JPA notary, the database must be prepared. This can be performed using the 
:ref:`Corda Database Management Tool <database-management-tool-ref>`. If preferred, the required tables can be manually 
created. See below for example database scripts. Note that in these examples, a database named "corda" is created to 
house the tables - this is purely for example purposes. The database name could be any string supported by your 
database vendor - ensure that the configuration matches the database name.

Using the Corda Database Management Tool
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If using the Corda Database Management Tool to perform initial schema setup, take note of the following:

  * Specify the mode as being JPA_NOTARY by using the command-line parameter'--mode=JPA_NOTARY'
  * Ensure that the notary configuration element contains a jpa configuration element
  * Ensure that the dataSource properties collection contains the correct 'dataSourceClassName' entry
  * Ensure that the correct values for 'dataSource.user' and 'dataSource.password' are provided

Use the 'dry-run' command to generate SQL scripts which could be inspected prior to being run. Alternatively, use the 
'execute-migration' command to prepare the database, including table creation. Note that users and databases are not 
created. Thus, the database must already exist.

Please note that CockroachDB installations are not supported by the Corda Database Management Tool. It is recommended that 
the SQL script below be used as the basis for setting up a CockroachDB database.

.. note:: Creating the schema manually and then switching to using the Corda Database Management Tool is not supported. We 
    recommend that one method of creating the schema be selected from the start and that this method should then be used for
    the lifetime of the notary.

Database users
~~~~~~~~~~~~~~

We recommend creating one database user with schema modification rights so as to be able to create the schema objects 
necessary for the operation of the notary. However, this user should not be used for the operation of the notary for 
security reasons. We recommend the creation of a user with more limited permissions for the operation of the notary. This 
would be set in the configuration of the notary in the 'dataSource' section.

Percona
~~~~~~~~~~~~~~~~~~~~~~~~~~

.. sourcecode:: sql
  
  create database if not exists corda;
  
  create table notary_committed_states (
    state_ref varchar(73) not null, 
    consuming_transaction_id varchar(64) not null, 
    constraint id1 primary key (state_ref)
    );
  
  create table notary_committed_transactions (
    transaction_id varchar(64) not null,
    constraint id2 primary key (transaction_id)
    );
    
  create table notary_request_log (
    id varchar(76) not null,
    consuming_transaction_id varchar(64),
    requesting_party_name varchar(255),
    request_timestamp timestamp not null,
    request_signature varbinary(1024) not null,
    constraint id3 primary key (id)
    );

Postgres
~~~~~~~~

.. sourcecode:: sql
  
  create table notary_committed_states (
    state_ref varchar(73) not null, 
    consuming_transaction_id varchar(64) not null, 
    constraint id1 primary key (state_ref)
    );
    
  create table notary_committed_transactions (
    transaction_id varchar(64) not null,
    constraint id2 primary key (transaction_id)
    );
    
  create table notary_request_log (
    id varchar(76) not null,
    consuming_transaction_id varchar(64),
    requesting_party_name varchar(255),
    request_timestamp timestamp not null,
    request_signature bytea not null,
    constraint id3 primary key (id)
    );

CockroachDB
~~~~~~~~~~~

.. sourcecode:: sql
  
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

Oracle 11g
~~~~~~~~~~

.. sourcecode:: sql
  
  create table notary_committed_states (
    state_ref varchar(73) not null, 
    consuming_transaction_id varchar(64) not null, 
    constraint id1 primary key (state_ref)
    );
    
  create table notary_committed_transactions (
    transaction_id varchar(64) not null,
    constraint id2 primary key (transaction_id)
    );
    
  create table notary_request_log (
    id varchar(76) not null,
    consuming_transaction_id varchar(64),
    requesting_party_name varchar(255),
    request_timestamp timestamp not null,
    request_signature RAW(1024) not null,
    constraint id3 primary key (id)
    );


SQL Server
~~~~~~~~~~

.. sourcecode:: sql
  
  create database corda;

  use corda;

  create table notary_committed_states (
    state_ref varchar(73) not null, 
    consuming_transaction_id varchar(64) not null, 
    constraint id1 primary key (state_ref)
    );
    
  create table notary_committed_transactions (
    transaction_id varchar(64) not null,
    constraint id2 primary key (transaction_id)
    );
    
  create table notary_request_log (
    id varchar(76) not null,
    consuming_transaction_id varchar(64),
    requesting_party_name varchar(255),
    request_timestamp datetimeoffset not null,
    request_signature varbinary(1024) not null,
    constraint id3 primary key (id)
    );
    
