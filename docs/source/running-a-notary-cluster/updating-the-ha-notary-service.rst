==============================
Updating the HA notary service
==============================

Version 3 to Version 4
======================

- Backup your data, see :doc:`operating percona <./operating-percona>`.
- Test you can restore from backup.
- Stop the notary service.
- Create table with the script below.
- Install the new version of the Corda JAR.
- Restart the notary.


Script to create table
-----------------------

.. code:: sql

	CREATE TABLE IF NOT EXISTS notary_committed_transactions (
                        	transaction_id BINARY(32) NOT NULL,
                        	CONSTRAINT tid PRIMARY KEY (transaction_id)
                        );
