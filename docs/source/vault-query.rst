Vault Query
===========

Corda has been architected from the ground up to encourage usage of industry standard, proven query frameworks and libraries for accessing RDBMS backed transactional stores (including the Vault).

Corda provides a number of flexible query mechanisms for accessing the Vault:

- VaultService API
- custom JPA/JPQL queries
- custom 3rd party Data Access frameworks such as `Spring Data <http://projects.spring.io/spring-data>`_

The majority of query requirements can be satified by using the Vault API:

.. literalinclude:: ../../core/src/main/kotlin/net/corda/core/node/services/Services.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryAPI
    :end-before: DOCEND VaultQueryAPI

This API provides both static (snapshot) and dynamic (snapshot with streaming updates) methods for a defined set of filter criteria.
Simple pagination (page number and size) and ordering (ascending, descending) is also specifiable.

The ``QueryCriteria`` interface provides a flexible mechanism for specifying filtering, and simple pagination and ordering criteria. 
There are four implementations of this interface which can be chained together to define advanced filters.

	1. ``VaultQueryCriteria`` provides filterable criteria on attributes within the Vault states table: status (UNCONSUMED, CONSUMED), state reference(s), contract state type(s), notaries, soft locked states, timestamps (RECORDED, CONSUMED). Additiionally, you may specify simple pagination and ordering.

	   .. note:: Sensible defaults are defined for frequently used attributes (status = UNCONSUMED, ordering = Order.ASC).

	2. ``FungibleAssetQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``FungibleAsset`` contract state interface, used to represent assets that are fungible, countable and issued by a specific party (eg. ``Cash.State`` and ``CommodityContract.State`` in the Corda finance module). 
	   
	   .. note:: Contract states that extend the ``FungibleAsset`` interface now automatically persist associated state attributes. 

	3. ``LinearStateQueryCriteria`` provides filterable criteria on attributes defined in the Corda Core ``LinearState`` and ``DealState`` contract state interfaces, used to represent entities that continuously supercede themselves, all of which share the same *linearId* (eg. trade entity states such as the ``IRSState`` defined in the SIMM valuation demo)
	   
	   .. note:: Contract states that extend the ``LinearState`` or ``DealState`` interfaces now automatically persist associated state attributes. 

	4. ``VaultCustomQueryCriteria`` provides the means to specify one or many arbitrary expressions on attributes defined by a custom contract state that implements its own schema as described in the Persistence_ documentation and associated examples.
	
		.. note:: Custom contract states that implement the ``Queryable`` interface may now extend the ``FungiblePersistentState``, ``LinearPersistentState`` or ``DealPersistentState`` classes when implementing their ``MappedSchema``. Previously, all custom contracts extended the root ``PersistentState`` class and defined repeated mappings of ``FungibleAsset`` and ``LinearState`` attributes.

Examples of these ``QueryCriteria`` objects are presented below for Kotlin and Java.

The Vault Query API leverages the rich semantics of the underlying Requery_ persistence framework adopted by Corda.

.. _Requery: https://github.com/requery/requery/wiki
.. _Persistence: https://docs.corda.net/persistence.html

.. note:: Permissioning at the database level will be enforced at a later date to ensure authenticated, role-based, read-only access to underlying Corda tables.

.. note:: API's now provide ease of use calling semantics from both Java and Kotlin.

.. note:: Current queries by ``Party`` use a party name and associated ``CompositeKey``. In future it may be possible to query on specific elements of a composite key hierarchy (parent and child nodes, weightings). This decomposition of a ``CompositeKey`` may also be used to model identity hierarchies.

Example usage
-------------

**Kotlin**

General queries using ``VaultQueryCriteria`` 

Query for all unconsumed states:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample1
    :end-before: DOCEND VaultQueryExample1

Query for unconsumed states for some state references:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample2
    :end-before: DOCEND VaultQueryExample2

Query for unconsumed states for several contract state types:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample3
    :end-before: DOCEND VaultQueryExample3

Query for unconsumed states for a given notary:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample4
    :end-before: DOCEND VaultQueryExample4

Query for unconsumed states recorded between two time intervals:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample5
    :end-before: DOCEND VaultQueryExample5

Query for consumed states after specific time:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample6
    :end-before: DOCEND VaultQueryExample6

Query for all states with pagination specification:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample7
    :end-before: DOCEND VaultQueryExample7

LinearState and DealState queries using ``LinearStateQueryCriteria``

Query for unconsumed linear states for given linear ids:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample8
    :end-before: DOCEND VaultQueryExample8

.. note:: this example was previously executed using the deprecated extension method:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultDeprecatedQueryExample1
    :end-before: DOCEND VaultDeprecatedQueryExample1

Query for all linear states associated with a linear id:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample9
    :end-before: DOCEND VaultQueryExample9

.. note:: this example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultDeprecatedQueryExample2
    :end-before: DOCEND VaultDeprecatedQueryExample2

Query for unconsumed deal states with deals references:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample10
    :end-before: DOCEND VaultQueryExample10

Query for unconsumed deal states with deals parties:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample11
    :end-before: DOCEND VaultQueryExample11

FungibleAsset and DealState queries using ``FungibleAssetQueryCriteria``

Query for fungible assets for a given currency:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample12
    :end-before: DOCEND VaultQueryExample12

Query for fungible assets for a given currency and minimum quantity:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample13
    :end-before: DOCEND VaultQueryExample13

Query for fungible assets for a specifc issuer party:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample14
    :end-before: DOCEND VaultQueryExample14

Query for consumed fungible assets with a specific exit key:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample15
    :end-before: DOCEND VaultQueryExample15

Custom contract state queries using ``VaultCustomQueryCriteria``

Query for custom attributes on contract that extends *FungibleAsset*:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample16
    :end-before: DOCEND VaultQueryExample16

Chaining query criteria specifications

Query for consumed linear states for linearId between two timestamps:

.. literalinclude:: ../../node/src/test/kotlin/net/corda/node/services/vault/VaultQueryTests.kt
    :language: kotlin
    :start-after: DOCSTART VaultQueryExample17
    :end-before: DOCEND VaultQueryExample17

**Java examples**

Query for all consumed contract states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample1
    :end-before: DOCEND VaultJavaQueryExample1

.. note:: this example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultDeprecatedJavaQueryExample1
    :end-before: DOCEND VaultDeprecatedJavaQueryExample1

Query for all deal states:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultJavaQueryExample2
    :end-before: DOCEND VaultJavaQueryExample2

.. note:: this example was previously executed using the deprecated method:

.. literalinclude:: ../../node/src/test/java/net/corda/node/services/vault/VaultQueryJavaTests.java
    :language: java
    :start-after: DOCSTART VaultDeprecatedJavaQueryExample2
    :end-before: DOCEND VaultDeprecatedJavaQueryExample2

Other use case scenarios
------------------------

For advanced use cases that require sophisticated pagination, sorting, grouping, and aggregation functions, it is recommended that the CorDapp developer utilise one of the many proven frameworks that ship with this capability out of the box. Namely, implementations of JPQL (JPA Query Language) such as **Hibernate** for advanced SQL access, and **Spring Data** for advanced pagination and ordering constructs.

The Corda Tutorials provide examples satisfying these additional Use Cases:

     1. Template / Tutorial CorDapp service using Vault API Custom Query to access attributes of IOU State
     2. Template / Tutorial CorDapp service query extension executing Named Queries via JPQL (Hibernate_)
     3. `Advanced pagination <https://docs.spring.io/spring-data/commons/docs/current/api/>`_ queries using Spring Data JPA_
        
 .. _Hibernate: http://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#hql
 .. _JPA: https://docs.spring.io/spring-data/jpa/docs/current/reference/htmls
 
