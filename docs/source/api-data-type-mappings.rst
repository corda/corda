API: Data type mappings
=======================

Corda types are often used within data classes to represent fixed sized values, which are subsequently mapped to
attributes within a database.

The following table summarises the maximum permissible length and underlying database type Corda data classes are mapped to.
Assertions are used to enforce maximum sizing of fixed types throughout the Corda code.

+------------+------------+-----------+------------+------------+------------+
| Data Class | Field      | Max size | Corda type      | JPA Schema type | SQL 92 database type |
+============+============+===========+============+============+============+
| PartyAndReference | reference | 16 | OpaqueBytes | ByteSequence | VARCHAR(16) |
+------------+------------+-----------+------------+------------+------------+

Basic type mappings for JPA/Hibernate are defined `here https://docs.jboss.org/hibernate/orm/5.2/userguide/html_single/Hibernate_User_Guide.html#basic`_

.. note:: different database providers may interpret and map SQL-92 standard types to their own internal representation.

