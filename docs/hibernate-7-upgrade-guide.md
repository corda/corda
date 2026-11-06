# Upgrading to Hibernate 7 (Corda 4.15)

> **DRAFT.** Written in this repository so that it travels with the change (ENT-15178). It is intended to be ported to the
> [corda-docs](https://github.com/corda/corda-docs) repository. Sections marked **[VERIFY]** need confirming by the doc/QA team
> against a supported-platform matrix that this draft could not test (see [Testing status](#testing-status-remove-before-publishing)).

Corda 4.15 upgrades its object-relational mapper from Hibernate ORM 5.6 (end of life) to **Hibernate ORM 7.4.12.Final**.
Hibernate 6 and later implement *Jakarta* Persistence rather than *Java EE* Persistence, so the persistence annotations that
CorDapps use for their vault schemas move from the `javax.persistence` package to the `jakarta.persistence` package.

## Who needs to do what

| You are...                                    | What you need to do                                                                                              |
|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **A node operator**                           | Nothing to your node configuration or database. Check your database version is supported, and deploy CorDapps built for 4.15. |
| **A CorDapp developer** (with a custom schema)| Port your schema classes to `jakarta.persistence`, rebuild, and re-sign. A script helps with most of it.        |
| **A CorDapp developer** (no custom schema)    | Nothing, unless your code calls `ServiceHub.withEntityManager` or uses Hibernate or JPA classes directly.        |

**There is no database migration.** Corda 4.15 does not add, change or remove any table, column, index or sequence as part of this
upgrade, and Liquibase has no new changesets for it. Hibernate 7 has been configured to read and write the formats that existing
databases already use (see [What stays the same](#what-stays-the-same-in-the-database)). You do not need to run
`run-migration-scripts` for this change.

---

## For node operators

### 1. Check your database version

Hibernate 7 raised the minimum database versions it supports. **[VERIFY]** against the Corda 4.15 supported-platforms list.

| Database                | Minimum version supported by Hibernate 7 |
|-------------------------|------------------------------------------|
| PostgreSQL              | 13                                       |
| Oracle                  | 19c                                      |
| Microsoft SQL Server    | 2012 (11.0); Azure SQL supported         |
| H2 (development only)   | 2.1.214                                  |

Older versions may start, but Hibernate no longer tests against them and may generate SQL they do not support. If you run an older
version, upgrade the database *before* upgrading the node. Use a current JDBC driver for your database.

### 2. Deploy CorDapps built for Corda 4.15

A CorDapp whose schema was compiled against `javax.persistence` cannot be used by Hibernate 7. The node detects this when it starts
and **refuses to start**, with a message that names the offending class, for example:

```
The class com.example.MyState$PersistentMyState of the schema com.example.MySchemaV1 was built for Corda 4.14 or earlier, as it uses the
javax.persistence annotations. Corda 4.15 uses Hibernate 7, which requires jakarta.persistence. The CorDapp that contains the schema
needs to be rebuilt for Corda 4.15 ...
```

Nothing is changed in the database when this happens. Obtain updated CorDapps from their vendors (or see the next section if you build
them yourself) **before** starting the node on 4.15. CorDapps that have no custom schema pass this check. A flow built for 4.14 that
calls `serviceHub.withEntityManager` is not detected, and fails when it runs (see "Changes the script cannot make for you").

### 3. Things you may notice

* The log shows messages such as `HHH90006001: Setting 'hibernate.id.db_structure_naming_strategy' is still incubating`
  once per Hibernate session factory at start-up. They are informational. **[VERIFY]** whether these should be silenced by default.
* If your database does not match what Corda expects, start-up fails with `HibernateSchemaChangeException` and the reason, exactly as
  before. Run the node with the `run-migration-scripts` sub-command, or contact support. The reason now starts `Schema validation:`
  rather than `Schema-validation:`; update any log alert that matches the old text.
* If a flow ends up in the flow hospital with `Attempted to commit database transaction marked for rollback`, shortly after an
  `EntityExistsException` for the same entity, see "Recognising a failure caused by the `save` to `persist` change" in the CorDapp
  developer section below.
* With `exportHibernateJMXStatistics` (on by default in dev mode), the `org.hibernate:type=statistics` MBean has the same attributes as
  before, and some new ones (for example `QueryPlanCacheHitCount` and `SlowQueries`). Its `getSecondLevelCacheStatistics` and
  `getNaturalIdCacheStatistics` operations are gone. Corda does not use Hibernate's second-level cache, so they had nothing to report.
* The `--allow-hibernate-to-manage-app-schema` option (development only) still creates *missing* tables and columns, but, as before,
  does **not** alter existing ones. (Hibernate 7's own `update` mode would alter existing columns; Corda deliberately turns that off.)

### Rolling back

This upgrade changes no database structure, so the database can be used with the previous version of the node. CorDapps must be
rolled back with it.

---

## For CorDapp developers

### 1. What changes

| Hibernate 5 / Corda 4.14                                         | Hibernate 7 / Corda 4.15                                              |
|------------------------------------------------------------------|-----------------------------------------------------------------------|
| `import javax.persistence.*`                                      | `import jakarta.persistence.*`                                        |
| `javax.persistence:javax.persistence-api:2.2`                     | `jakarta.persistence:jakarta.persistence-api:3.2.0`                   |
| `org.hibernate:hibernate-core:5.x`                                | `org.hibernate.orm:hibernate-core:7.4.12.Final`                       |
| `@Type(type = "uuid-char")`                                       | `@JdbcTypeCode(SqlTypes.VARCHAR)`                                     |
| `@Type(type = "corda-wrapper-binary")`                            | `@JdbcTypeCode(SqlTypes.VARBINARY)`                                   |
| `session.save(x)`                                                 | `session.persist(x)`                                                  |
| `session.update(x)`, `session.saveOrUpdate(x)`                    | `session.merge(x)`                                                    |
| `session.delete(x)`                                               | `session.remove(x)`                                                   |
| `javax.persistence.EntityManager` in `serviceHub.withEntityManager {}` | `jakarta.persistence.EntityManager`                              |

### 2. Use the migration script

`tools/scripts/migrate-cordapp-to-hibernate7.sh` applies the mechanical changes above to your source tree (Kotlin, Java, Gradle) and
then lists everything that still needs a human decision.

```bash
tools/scripts/migrate-cordapp-to-hibernate7.sh --dry-run path/to/your/cordapp   # show what would change
tools/scripts/migrate-cordapp-to-hibernate7.sh path/to/your/cordapp             # apply, then review the report
```

It is safe to run twice. Always review the diff and run your tests.

The script changes the Hibernate dependencies that your build files declare. It also lists the entries in your build files that are
likely to bring in Hibernate 5 through another dependency: Corda 4.14 or earlier, and Spring Data JPA. Gradle cannot put Hibernate 5 on
the same classpath as Corda 4.15 (see "Gradle reports a capability conflict" below). The script does not resolve your dependencies, so
it cannot find every case. If Gradle reports the conflict, the script's report tells you how to find the cause.

### 3. Changes the script cannot make for you

**HQL and JPQL are validated strictly.** Hibernate 5 passed anything it did not recognise straight through to the database; Hibernate 7
rejects it with `SemanticException`.

```kotlin
// Before: tx_id is a column name. Rejected by Hibernate 7.
"from MyEntity where tx_id = :id"
// After: use the attribute name.
"from MyEntity where txId = :id"

// Before: compared an association to the id of the target entity. Rejected by Hibernate 7.
"... join Blob blob on checkpoint.blob = blob.id"
// After: compare the association to the entity.
"... join Blob blob on checkpoint.blob = blob"
```

**Custom Hibernate types.** `@Type(type = "...")`, `@TypeDef` and the `org.hibernate.type.*TypeDescriptor` classes no longer exist.
Use `@JdbcTypeCode`, `@JdbcType` / `@JavaType`, or a JPA `AttributeConverter`.

**Stricter mappings.** For example `@OrderColumn` is only allowed on a `java.util.List`, not a `Set`. An association to an entity
that was never persisted (for example a `@OneToOne` with `@PrimaryKeyJoinColumn`) is reported when the session is flushed; persist the
associated entity as well.

**Result order.** A query with no `ORDER BY` has no defined order. Hibernate 7 renders some queries differently (for example an
`IN` over a composite key), so a database may now return rows in a different order. Add an explicit sort.

**Duplicate `persist`.** If the session already holds a different instance with the same id, `persist` fails immediately with
`EntityExistsException` and marks the transaction for rollback, so the transaction cannot be committed even if you catch the exception.
`EntityManager.persist` already behaved like this with Hibernate 5, so code that uses `withEntityManager` is unaffected. Code that used
Hibernate's `Session.save` (for example in a service, through `AppServiceHub.database.transaction { session.save(...) }`) has to change
to `persist`, and is affected: `save` threw `NonUniqueObjectException` and did not mark the transaction for rollback, so code that caught
it and carried on now fails when the transaction commits. Check for the existing entity first instead (for example with `session.find`).
The node's own code also uses `persist` where it used `save`, so the same is true inside the node. Nothing in the node is known to rely on
the old behaviour; see "Recognising a failure caused by the `save` to `persist` change" below if you suspect it.

**Constraint violations.** A duplicate-key violation that was reported as a `PersistenceException` wrapping a
`ConstraintViolationException` is now reported as the `ConstraintViolationException` itself (which is itself a
`PersistenceException`). The same applies to Hibernate's other JDBC exceptions, such as `SQLGrammarException` and `DataException`.
Code that catches `PersistenceException` is unaffected. Code that tests `e.cause is ConstraintViolationException` has to test the
exception itself as well, for example by walking the cause chain. Corda cannot restore the old wrapping, because Hibernate creates these
exceptions.

**Recognising a failure caused by the `save` to `persist` change.** Code that relied on catching a duplicate and carrying on in the same
transaction (in a CorDapp, or in the node itself) fails in two steps:

1. The duplicate is reported where it happens:
   `jakarta.persistence.EntityExistsException: A different object with the same identifier value was already associated with this
   persistence context for entity [<entity class>#<id>]`, caused by `org.hibernate.NonUniqueObjectException` with the same message. With
   Corda 4.14 the same situation logged `org.hibernate.NonUniqueObjectException: A different object with the same identifier value was
   already associated with the session : [<entity class>#<id>]`. The entity class and id say which entity was persisted twice.
2. If the code catches that exception and carries on, the transaction can no longer commit, and none of its changes are saved:
   `net.corda.nodeapi.internal.persistence.RolledBackDatabaseSessionException: Attempted to commit database transaction marked for rollback`.
   * **In a flow**, the commit happens at the flow's next checkpoint: its next suspension (for example `send`, `receive`, `sleep` or a
     subflow that suspends) or the end of the flow. The node log shows `Flow [<flow id>] admitted to hospital`, then `Error 1 of 1:` with
     `net.corda.node.services.statemachine.StateTransitionException: Attempted to commit database transaction marked for rollback` (caused
     by `RolledBackDatabaseSessionException`), then `Flow error discharged from hospital ... (error was Attempted to commit database
     transaction marked for rollback)`. The flow is retried from its last checkpoint. If the duplicate happens again every time, it is
     retried three times, and then the log shows `Flow error kept for overnight observation ...` and the flow stays in the hospital.
   * **Outside a flow**, for example in a service using `AppServiceHub.database.transaction { ... }`, the `transaction` call itself throws
     `RolledBackDatabaseSessionException`.

With Corda 4.14 the same code would have logged only the `NonUniqueObjectException`, and the rest of the transaction would have been
committed. The fix is to check for the existing entity before persisting it (or to use `merge` if updating it is what was meant), rather
than catching the exception.

**Native SQL result types.** A native query (`createNativeQuery`) returns a `BIGINT` column, or `count(*)`, as a `Long` where Hibernate 5
returned a `BigInteger`. Code that casts the result (`result as BigInteger`) now fails with `ClassCastException: class java.lang.Long
cannot be cast to class java.math.BigInteger`. Use `(result as Number).toLong()`. Date and time columns still come back as
`java.sql.Timestamp`, `Date` and `Time`, because Corda sets `hibernate.query.native.prefer_jdbc_datetime_types`.

**`LIKE` patterns on H2, PostgreSQL and CockroachDB.** Hibernate 7 adds `escape ''` to a `LIKE` with no escape character on these databases,
so a backslash in a pattern is now an ordinary character. For example the pattern `a\_b` used to match only the value `a_b`; it now matches
values such as `a\xb` (a backslash, any one character, then `b`) and no longer matches `a_b`. Oracle and SQL Server never treated the
backslash specially and are unchanged. If you escape `_` or `%`, give the escape character explicitly, for example with the JPA criteria
API's `like(path, pattern, '\\')` in Kotlin. Vault `Builder.like` and `notLike` patterns behave in the same new way.

**Tables generated from your entities now get `CHECK` constraints.** When Hibernate generates the DDL for a CorDapp table (the database
management tool's "create migration SQL for CorDapp", or `--allow-hibernate-to-manage-app-schema` in development), an enum column gets
`check (col between 0 and N)` (ordinal) or `check (col in (...))` (string), and on Oracle a boolean gets `check (col in (0,1))`. Ordinal
enum columns also get a narrower type (`tinyint`, `smallint`, `number(3,0)`). Tables that already exist are not changed. If you later add
a constant to the enum, inserting it fails with a check-constraint violation (for example `ORA-02290: check constraint ... violated`,
`violates check constraint` on PostgreSQL, `The INSERT statement conflicted with the CHECK constraint` on SQL Server). Review generated
scripts and remove the `CHECK` clauses you do not want before you apply them.

**Arrays.** An `IntArray`, `LongArray`, `Array<String>` or similar attribute was stored as a serialised binary value with Hibernate 5.
Hibernate 7 maps it to the database's array type (or XML on SQL Server), so a table created before 4.15 fails validation with
`wrong column type ... expecting [... array ...]`. An `Array<Byte>` (`Byte[]`) or `Array<Char>` (`Character[]`) attribute, which Hibernate 5
stored as binary or character data, is now refused when the node starts (`MappingException`). Hibernate's
`hibernate.type.wrapper_array_handling=LEGACY` would restore those two, but it stops the node from starting on PostgreSQL, so Corda does
not set it. Use `ByteArray` or `String` instead, or an `AttributeConverter` that stores the value as before.

**Smaller differences.**
* `NOT IN` an empty list now matches every row on every database. With Hibernate 5 it matched no rows on PostgreSQL and SQL Server, every
  row on H2, and failed on Oracle.
* On SQL Server, `avg` of an integer column is no longer truncated to an integer (Hibernate 7 casts the argument to `float`), so it now
  returns the same value as on the other databases.
* On SQL Server, an `IN` list of more than 2,100 numeric values fails with `The incoming request has too many parameters`, because
  Hibernate 7 binds each value as a parameter where Hibernate 5 inlined numbers. Split such lists.
* `persist` and `merge` mark the transaction for rollback on any failure, not only a duplicate id (for example a `null` in a non-nullable
  property). Hibernate 5's `save` and `update` did not.

**Gradle reports a capability conflict on `hibernate-core`.** Hibernate 5 was published as `org.hibernate:hibernate-core`, and
Hibernate 6 and later as `org.hibernate.orm:hibernate-core`. Hibernate 7's Gradle metadata says that it provides both, so that Gradle
never puts two versions of Hibernate on one classpath. Corda 4.15 brings in Hibernate 7 through `corda-core`. If anything else in the
same configuration (for example the compile, runtime or test classpath) depends on Hibernate 5, Gradle refuses to resolve it:

```
> Could not resolve all files for configuration ':compileClasspath'.
   > Could not resolve org.hibernate.orm:hibernate-core:7.4.12.Final.
      > Module 'org.hibernate.orm:hibernate-core' has been rejected:
           Cannot select module with conflict on capability 'org.hibernate:hibernate-core:7.4.12.Final' also provided by [org.hibernate:hibernate-core:7.4.12.Final(compile)]
```

To find the dependency that brings in Hibernate 5, run `dependencyInsight` for the module and configuration that failed:

```bash
./gradlew <module>:dependencyInsight --dependency org.hibernate:hibernate-core --configuration compileClasspath
```

The path under the failed Hibernate 5 entry leads to it, for example:

```
org.hibernate:hibernate-core:5.6.14.Final FAILED
\--- net.corda:corda-core:4.11
     \--- compileClasspath
```

The usual causes are:

* A module that still depends on a Corda 4.14 or earlier artifact, for example a test CorDapp deliberately built against an older Corda,
  in the same configuration as Corda 4.15. Different versions of the *same* Corda artifact are not a problem, because Gradle picks the
  newest one.
* A library that depends on Hibernate 5, for example Spring Data JPA in a Spring Boot 2 web server that is in the same Gradle module as
  `corda-rpc`.

To fix it, in order of preference:

1. Upgrade or remove the dependency that brings in Hibernate 5.
2. If that dependency does not use Hibernate itself (for example an older Corda artifact that is only needed at compile time), exclude
   Hibernate 5 from it. Corda's own test CorDapps built against 4.11 and 4.12 do this:

   ```groovy
   cordaProvided("net.corda:corda-core:4.11") {
       exclude group: 'org.hibernate', module: 'hibernate-core'
   }
   ```

3. To fix every path at once, replace the old coordinate with Hibernate 7 throughout the build:

   ```groovy
   configurations.configureEach {
       resolutionStrategy.dependencySubstitution {
           substitute module('org.hibernate:hibernate-core') using module('org.hibernate.orm:hibernate-core:7.4.12.Final') because 'Corda 4.15 uses Hibernate 7'
       }
   }
   ```

A capability rule with `selectHighestVersion()` does **not** help: Hibernate 7's platform also moves the old coordinate to 7.4.12, so
both candidates have the same version. Only exclude or replace Hibernate 5 for code that does not use it. A library that calls
Hibernate 5 is likely to fail at run time against Hibernate 7.

**`withEntityManager` code built for 4.14 fails when it runs, not when the node starts.** The block passed to
`serviceHub.withEntityManager` receives a `jakarta.persistence.EntityManager` in 4.15. A flow compiled against 4.14 expects a
`javax.persistence.EntityManager` and fails when it reaches the call, for example with a `NoClassDefFoundError` or a
`ClassCastException`. The node's start-up check only looks at schema classes, so it does not find this. Rebuild the CorDapp, and run
the tests of every flow that uses `withEntityManager`.

### 4. Rebuilding, signing and versioning

The persistence annotations are part of your state classes' bytecode, so the CorDapp jar that contains them changes. Treat the
result as a new CorDapp version:

* Increase the CorDapp version number.
* Sign it with the **same key** as before. States using a signature constraint continue to verify.
* States using a **hash constraint** are pinned to the old jar and need the usual constraint-migration (contract upgrade) process.
* If you keep contracts and states in a jar separate from your flows, only the jar containing the schema classes needs to change.

### 5. Testing

`MockNetwork`, `MockServices` and the driver run against H2 and let Hibernate create the tables of your own schemas, as before.
Test your CorDapp against **every database you support**. If you can, run your integration tests against PostgreSQL, SQL Server and
Oracle: Hibernate's mapping of some Java types differs between databases.

### 6. A CorDapp you cannot rebuild

If you only have the compiled jar of a third-party CorDapp, the
[Eclipse Transformer](https://github.com/eclipse/transformer) can rewrite its `javax.persistence` references:

```bash
printf 'javax.persistence=jakarta.persistence\n' > rename.properties
java -cp 'org.eclipse.transformer.cli-1.0.0.jar:...' org.eclipse.transformer.cli.TransformerCLI cordapp.jar cordapp-jakarta.jar -tr rename.properties
```

This **only** renames the package. It cannot convert `@Type(type = "...")`, so it is only suitable for CorDapps without those annotations,
and the jar must be re-signed afterwards, with the same constraints implications as above. Prefer asking the vendor for a 4.15 build.

---

## What stays the same in the database

Hibernate 6 and 7 changed several defaults. Corda sets the following properties so that the data formats used by existing
databases and CorDapps do **not** change. You do not need to set or change any of them.

| Behaviour that changed in Hibernate 6+                                                 | How Corda keeps the Hibernate 5 behaviour                                                |
|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `@GeneratedValue` uses a sequence per entity (`foo_SEQ`), incrementing by 50           | `hibernate.id.db_structure_naming_strategy=legacy`: the single `hibernate_sequence`, with an increment of 1, is still used. (`hibernate.id.sequence.increment_size_mismatch_strategy=FIX` only matters for a `@SequenceGenerator` whose `allocationSize` differs from the database sequence.) |
| `Instant` is stored as `TIMESTAMP WITH TIME ZONE` and read using the database session's time zone | `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`: stored as a UTC `TIMESTAMP`, independent of the database server's time zone |
| `UUID` is stored in the database's native type (`uuid`, `uniqueidentifier`, `raw(16)`) | `hibernate.type.preferred_uuid_jdbc_type=BINARY` (PostgreSQL: `UUID`): as in Hibernate 5    |
| Oracle `double`/`float` are `BINARY_DOUBLE`/`BINARY_FLOAT`                              | `hibernate.dialect.oracle.use_binary_floats=false`                                        |
| From Oracle 23, `Boolean` is the native `boolean` type                                   | `hibernate.type.preferred_boolean_jdbc_type=BIT` on Oracle: `number(1,0)`, as in Hibernate 5 |
| `hbm2ddl=update` also alters existing columns                                          | A Corda schema-management tool that only creates what is missing                          |
| `Duration` is stored as `NUMERIC(21)` (or an interval)                                  | `hibernate.type.preferred_duration_jdbc_type=BIGINT`: a `BIGINT` number of nanoseconds, as in Hibernate 5 |
| `OffsetDateTime` / `ZonedDateTime` keep their offset (`TIMESTAMP WITH TIME ZONE` where available) | `hibernate.timezone.default_storage=NORMALIZE`: a plain `TIMESTAMP`, normalised to UTC, as in Hibernate 5 |
| Native SQL queries return `java.time` types for date and time columns                   | `hibernate.query.native.prefer_jdbc_datetime_types=true`: `java.sql.Timestamp`, `Date` and `Time`, as in Hibernate 5 |
| A bulk HQL `update` of an `@Immutable` entity throws an exception                       | `hibernate.query.immutable_entity_update_query_handling_mode=warning`: allowed, with a warning, as in Hibernate 5 |

Corda's own `@Type` mappings are preserved:

| Hibernate 5 type         | Hibernate 7 mapping                                                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------|
| `uuid-char`              | `@JdbcTypeCode(SqlTypes.VARCHAR)` (still stored as 36 characters)                                  |
| `corda-wrapper-binary`   | `@JdbcTypeCode(SqlTypes.VARBINARY)`                                                                 |
| `corda-blob`             | A Corda JDBC type code: a regular BLOB, except `bytea` on PostgreSQL (needed for checkpoints)       |

---

## Frequently asked questions

**Do I have to run any database scripts?** No. This upgrade does not change the database.

**Can the fix for CorDapps be put in the node's migration files?** No, and it is not needed. Liquibase changesets change the
database; the CorDapp problem is in compiled code (annotations in a jar). The node cannot rewrite a CorDapp's code through a database
migration. What *can* be, and is, handled in the node is every database-visible difference (see the table above).

**Will my vault data still be readable?** Yes. The vault tables, and the formats of their columns, are unchanged.

**Does my CorDapp need Hibernate 7 on its classpath?** Only if it uses Hibernate classes directly (for example `@JdbcTypeCode`).
The node provides Hibernate; declare it `cordaProvided`/compile-only in your build, as you did for Hibernate 5.

---

## Testing status (remove before publishing)

Verified by the engineer who made the change (branch `adel/ENT-15178`):

* **Unit tests on H2**: the full `node`, `node-api`, `node-api-tests`, `core-tests` and `finance` suites (2,342 tests). Every failure was
  compared with the unmodified 4.15 baseline run in the same environment, and **none is caused by this change**:
  the TLS/X.509 tests (17 failures in `node`, `node-api` and `node-api-tests`) fail identically on the baseline; the flow tests in `core-tests`
  (`ReceiveMultipleFlowTests`, `ReferencedStatesFlowTests`, `FlowSleepTest`, `ReceiveFinalityFlowTest`) fail in the same way in a full baseline
  run (13 failures) and pass when run on their own on both; and a Raft notary test timed out once in a full run and passes on its own.
* **PostgreSQL 16** (embedded, real server): 729 tests from the persistence, vault, identity, network-map, key and migration suites. The only
  failures are tests that fail identically on the Hibernate 5 baseline against PostgreSQL, because they assume H2 (a test that re-opens a
  database by name, an unordered `GROUP BY`, and the TLS tests).
* A database created by the Liquibase scripts is opened by Hibernate in strict `validate` mode (what a production node does) on both H2 and
  PostgreSQL, with the JVM in a non-UTC time zone. `Instant` values are stored as UTC wall-clock `timestamp` values, `UUID` and `hibernate_sequence`
  behave as before.
* The "built for Corda 4.14" start-up check was verified against the genuine Corda 4.12 `finance-contracts` jar.
* The migration script was run on unmodified Hibernate 5 sources and produced the same result as the hand conversion in this change.
* The capability conflict and its fixes were reproduced with Gradle 7.6.4, using `net.corda:corda-core:4.11` next to Hibernate 7.4.12. The
  conflict, the `exclude` and the substitution behave as described above, and `selectHighestVersion()` does not resolve the conflict.
  `dependencyInsight` shows the path to `corda-core:4.11` as above.
* The migration script's dependency checks were run on a sample build. It reported Corda 4.11 to 4.14 in Groovy and Kotlin build files,
  `gradle.properties` and a version catalog, and Spring Data JPA. It did not report Corda 4.15 versions, the Corda Gradle plugins'
  versions, a CorDapp's own version or the platform version.

**Not visible to the API checker:** `.ci/api-current.txt` records `withEntityManager(kotlin.jvm.functions.Function1)` and
`withEntityManager(java.util.function.Consumer)` without their type parameters, and records no annotations. The move from
`javax.persistence` to `jakarta.persistence` therefore leaves it unchanged, and no tool reports this break. The release notes must list
it as a breaking API change.

**Not tested** (no environment available): Microsoft SQL Server, Azure SQL, Oracle, and PostgreSQL versions other than 16. Hibernate 7
maps some Java types differently on these databases, and it is where an unnoticed difference is most likely. These need to be run by QA, against
a database created by a 4.14 node (not a fresh one) with a populated vault, **before release**. In particular check: startup in `validate` mode
(no `HibernateSchemaChangeException`), `Instant` columns, `UUID` columns, and the checkpoint tables on each database.
