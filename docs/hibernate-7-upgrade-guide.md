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
| **A node operator**                           | Nothing to your node configuration or database. Check your database version is supported. CorDapps built for Corda 4.14 or earlier keep working: the node converts their `javax.persistence` classes to `jakarta.persistence` as it loads them (see [CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)). |
| **A CorDapp developer** (with a custom schema)| Nothing to run on 4.15. When you next rebuild, port your schema classes to `jakarta.persistence`; a script helps with most of it. A CorDapp that uses a custom Hibernate `@Type` must be ported before it can run on 4.15. To run `MockNetwork`, `MockServices` or in-process driver tests of the CorDapp *before* porting it, add the test agent to the test JVM (see [Testing](#5-testing)). |
| **A CorDapp developer** (no custom schema)    | Nothing. Code that calls `ServiceHub.withEntityManager` or uses JPA classes directly is converted like the schemas; port it when you next rebuild. In-process tests of it need the test agent, as above. |

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

### 2. CorDapps built for Corda 4.14 or earlier

A CorDapp built for Corda 4.14 or earlier refers to `javax.persistence`, which Hibernate 7 does not read. The node's CorDapp class
loader therefore converts such classes as it loads them: every reference to `javax.persistence` in the class bytes is renamed to
`jakarta.persistence`, which is what the application servers did for Java EE applications when they moved to Jakarta EE. The rename
covers the schema annotations, `AttributeConverter` implementations, code that uses `EntityManager` and the query and criteria types in
`ServiceHub.withEntityManager` blocks, and `catch` clauses of the JPA exception types. The jar on disk is not touched, so its hash,
its signature and the contract constraints that depend on them are unaffected. The log shows, once per such CorDapp:

```
CorDapp /opt/corda/cordapps/my-cordapp-1.0.jar was built for Corda 4.14 or earlier (javax.persistence); its classes are converted to
jakarta.persistence for Hibernate 7 as they are loaded
```

A CorDapp built for Corda 4.15 (`jakarta.persistence`), or built to run on both (both sets of annotations, see
[A CorDapp that must run on 4.14 and 4.15](#7-a-cordapp-that-must-run-on-414-and-415)), is loaded as it is.

What the conversion cannot do is translate Hibernate's own annotations, whose members changed in Hibernate 6. `@Type(type = "uuid-char")`
and `@Type(type = "corda-wrapper-binary")` are replaced by the `@JdbcTypeCode` Corda's own schemas use (the stored formats are unchanged).
Any other `@Type`, and `@TypeDef`, make the node **refuse to start** with a message that names the class and the member, for example:

```
The class com.example.MyState$PersistentMyState uses the Hibernate 5 annotation @Type(type = "my-type") on amount, which has no equivalent
in Hibernate 7 that the node can substitute (it substitutes [uuid-char, corda-wrapper-binary]). The CorDapp that contains the class needs
to be rebuilt for Corda 4.15 ...
```

Nothing is changed in the database when this happens. Obtain an updated CorDapp from its vendor (or see the developer sections if you
build it yourself) **before** starting the node on 4.15. The same applies to a CorDapp that uses Hibernate 5 classes directly (for
example through `Session.unwrap`), which fails when that code runs rather than at start-up.

### 3. Things you may notice

* The log shows messages such as `HHH90006001: Setting 'hibernate.id.db_structure_naming_strategy' is still incubating`
  once per Hibernate session factory at start-up. They are informational. **[VERIFY]** whether these should be silenced by default.
* If your database does not match what Corda expects, start-up fails with `HibernateSchemaChangeException` and the reason, exactly as
  before. Run the node with the `run-migration-scripts` sub-command, or contact support.
* The `--allow-hibernate-to-manage-app-schema` option (development only) still creates *missing* tables and columns, but, as before,
  does **not** alter existing ones. (Hibernate 7's own `update` mode would alter existing columns; Corda deliberately turns that off.)
* With a converted CorDapp, Quasar (the flow framework's instrumentation agent) may print, on the console at start-up, a line such as
  `[quasar] WARNING: Method not found in class - assuming suspendable: com/example/Repo#find(Ljakarta/persistence/EntityManager;...)`.
  It is harmless. Quasar instruments the converted classes as the node loads them, but for a class that has not been loaded yet it reads
  the method list from the jar, where the method still has its `javax.persistence` signature; a call through the converted signature is
  then not found and Quasar treats it as it treats any unknown method, as possibly suspending, which is the safe assumption. The line
  appears only for a method whose signature names a `javax.persistence` type and only when its caller is loaded first. The same line
  can appear in an in-process test that runs the test agent (see [Testing](#5-testing)).

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

**Stricter mappings.** For example `@OrderColumn` is only allowed on a `java.util.List`, not a `Set`.

**Result order.** A query with no `ORDER BY` has no defined order. Hibernate 7 renders some queries differently (for example an
`IN` over a composite key), so a database may now return rows in a different order. Add an explicit sort.

**Duplicate `persist`.** Persisting a second entity with the same id in the same session now fails immediately with
`EntityExistsException`, and marks the transaction for rollback. Previously `save` threw `NonUniqueObjectException`, which did not
mark the transaction for rollback.

**Constraint violations.** A duplicate-key violation that was reported as a `PersistenceException` wrapping a
`ConstraintViolationException` is now reported as the `ConstraintViolationException` itself (which is itself a
`PersistenceException`). Code that catches `PersistenceException` is unaffected.

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

#### Testing a CorDapp that is still built against `javax.persistence`

A node converts such a CorDapp as its CorDapp class loader loads it (see [CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)).
In a `MockNetwork`, `MockServices` or in-process driver test, however, the CorDapp's classes are on the test's own classpath, from which
the JVM serves them before the node's class loader is asked, so they arrive unconverted and the node refuses the schema rather than let
Hibernate ignore it:

```
The class com.example.MyState$PersistentMyState of the schema com.example.MySchemaV1 was built for Corda 4.14 or earlier, as it uses the
javax.persistence annotations, and was not loaded through the node's CorDapp class loader, which converts such classes for Hibernate 7
(Corda 4.15). This happens when the CorDapp's classes are on the node's own classpath, for example in a MockNetwork, MockServices or
in-process driver test. Run such a test with the javax persistence test agent (-javaagent:corda-javax-persistence-test-agent.jar), ...
```

To run such tests on 4.15 without porting the CorDapp first, run the test JVM with the **javax persistence test agent**,
`net.corda:corda-javax-persistence-test-agent`. It is a Java agent that applies the node's conversion to every class the test JVM loads,
wherever it is loaded from: the CorDapp's schema, its `withEntityManager` code and its test code alike. In Gradle:

```groovy
configurations {
    javaxPersistenceAgent
}

dependencies {
    javaxPersistenceAgent "net.corda:corda-javax-persistence-test-agent:$corda_release_version"
}

tasks.withType(Test).configureEach {
    doFirst {
        // Listed before Quasar's agent (added by the quasar-utils plugin), so that Quasar instruments converted classes, as on a node.
        jvmArgs = ["-javaagent:${configurations.javaxPersistenceAgent.singleFile}"] + jvmArgs
    }
}
```

For a run configuration in an IDE, add the same `-javaagent:<path to the jar>` to the VM options, before Quasar's. The jar is
self-contained (it carries its own copy of the bytecode library it uses, under a private package name), so it works with any test runner
and does not change what is on the test classpath. A future version of the `quasar-utils` Gradle plugin may add the agent for you.

What to know about it:

* **It is for test JVMs only.** A node never needs it: the node's class loader converts the CorDapp. A driver test that starts its nodes
  *out of process* needs it neither, as those nodes load the CorDapp through that class loader. Tests of a CorDapp already built for
  `jakarta.persistence`, or for both APIs, do not need it, and it leaves their classes alone.
* **Start the JVM with it.** The agent must be on the command line (`-javaagent`); a class loaded before the agent is installed stays
  unconverted. The jar can also be attached to a running JVM, but that form is not supported for tests: the node then reports the class
  as loaded before the agent was.
* **The same limits as on a node.** A Hibernate 5 `@Type` the node cannot substitute, and `@TypeDef`, cannot be converted here either.
  Because the JVM ignores a class file transformer that fails, the agent prints the reason on the console (`[corda-javax-persistence-test-agent]
  The class ... was left as built for javax.persistence ...`) and the node's message then names the class and that reason:

  ```
  The class com.example.MyState$PersistentMyState of the schema com.example.MySchemaV1 was built for Corda 4.14 or earlier, as it uses the
  javax.persistence annotations, and the javax persistence test agent could not convert it for Hibernate 7 (Corda 4.15): The class
  com.example.MyState$PersistentMyState uses the Hibernate 5 annotation @TypeDef, which no longer exists in Hibernate 7. The CorDapp that
  contains the class needs to be rebuilt for Corda 4.15 ...
  ```
* **Quasar.** The agent is listed before Quasar's so that Quasar instruments the converted classes, as it does on a node. (The agent
  registers its transformer in a way that the JVM runs before Quasar's whatever the order, so a wrong order does not break a test, but
  keep the documented one.) The Quasar warning described in [Things you may notice](#3-things-you-may-notice) can appear in tests too.
* **A CorDapp that bundles the `javax.persistence` API jar** is handled: the API classes keep their names, and the CorDapp's classes are
  converted to refer to `jakarta.persistence` instead.

Alternatively, port the CorDapp to `jakarta.persistence` (sections 1 to 4), or test the unported jar on a real node or with
out-of-process driver nodes.

### 6. A CorDapp you cannot rebuild

Nothing needs to be done: the node converts it as described above. If it uses a Hibernate `@Type` the node cannot substitute, the
[Eclipse Transformer](https://github.com/eclipse/transformer) does not help either (it only renames the package); ask the vendor for a
4.15 build. To run in-process tests against it, use the test agent described in [Testing](#5-testing).

### 7. A CorDapp that must run on 4.14 and 4.15

A CorDapp built for Corda 4.14 (`javax.persistence`) runs on both: natively on 4.14, converted on 4.15. That is the simplest way to serve
both node generations with one jar, and the only thing it cannot use is a Hibernate `@Type` other than the two the node substitutes.

A CorDapp that wants to be built for `jakarta.persistence` while still running on 4.14 can carry **both** sets of annotations on every
mapped class, with the same attributes on each twin, and declare both API jars as compile-only dependencies (`javax.persistence-api:2.2`
and `jakarta.persistence-api:3.2.0`), bundling neither. Hibernate reads the set it understands, the JVM drops the annotations whose
classes are not on the node's classpath, and the node leaves a class that refers to both packages alone (it converts only classes that
refer to `javax.persistence` and not to `jakarta.persistence`; converting a dual class would duplicate its annotations). Code that calls
`ServiceHub.withEntityManager` must then not bind to either `EntityManager` interface at compile time, as the node hands over whichever
its Hibernate version implements: resolve the methods reflectively from the object you are given (both interfaces have the same method
names and shapes), or use `ServiceHub.jdbcSession()` instead. `@Type(type = "...")` has no equivalent that works on both versions, so
such a CorDapp replaces it by a JPA `AttributeConverter` (one implementation class per API).

---

## What stays the same in the database

Hibernate 6 and 7 changed several defaults. Corda sets the following properties so that the data formats used by existing
databases and CorDapps do **not** change. You do not need to set or change any of them.

| Behaviour that changed in Hibernate 6+                                                 | How Corda keeps the Hibernate 5 behaviour                                                |
|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `@GeneratedValue` uses a sequence per entity (`foo_SEQ`), incrementing by 50           | `hibernate.id.db_structure_naming_strategy=legacy` and `hibernate.id.sequence.increment_size_mismatch_strategy=FIX`: the single `hibernate_sequence` with an increment of 1 is still used |
| `Instant` is stored as `TIMESTAMP WITH TIME ZONE` and read using the database session's time zone | `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`: stored as a UTC `TIMESTAMP`, independent of the database server's time zone |
| `UUID` is stored in the database's native type (`uuid`, `uniqueidentifier`, `raw(16)`) | `hibernate.type.preferred_uuid_jdbc_type=BINARY` (PostgreSQL: `UUID`): as in Hibernate 5    |
| Oracle `double`/`float` are `BINARY_DOUBLE`/`BINARY_FLOAT`                              | `hibernate.dialect.oracle.use_binary_floats=false`                                        |
| `hbm2ddl=update` also alters existing columns                                          | A Corda schema-management tool that only creates what is missing                          |

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
database; the CorDapp problem is in compiled code (annotations in a jar), which the node handles when it loads the jar (see
[CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)). Every database-visible difference is handled in
the node too (see the table above).

**Is the converted CorDapp still the same CorDapp?** Yes. The conversion happens in memory as classes are loaded; the jar, its hash and
its signature are unchanged, so contract constraints, attachments and the CorDapp's identity on the network are unaffected.

**Will my vault data still be readable?** Yes. The vault tables, and the formats of their columns, are unchanged.

**Does my CorDapp need Hibernate 7 on its classpath?** Only if it uses Hibernate classes directly (for example `@JdbcTypeCode`).
The node provides Hibernate; declare it `cordaProvided`/compile-only in your build, as you did for Hibernate 5.

**My unported CorDapp runs on a 4.15 node but its `MockNetwork` tests fail with "was not loaded through the node's CorDapp class
loader". Why?** In an in-process test the CorDapp's classes are on the test classpath, where the node's converting class loader does not
see them. Add the javax persistence test agent to the test JVM, which converts them there (see [Testing](#5-testing)). Driver tests with
out-of-process nodes, and real nodes, do not need it.

**Does the test agent change my CorDapp, or does a node need it?** No. Like the node's class loader, the agent converts classes in
memory as the test JVM loads them; nothing on disk changes, and a node converts the CorDapp itself.

**Can I keep using the test agent instead of porting?** It lets the existing tests of a CorDapp built for Corda 4.14 or earlier run on
4.15, including code that is still typed on `javax.persistence`, so a CorDapp that is not being rebuilt can stay tested. Code you write
*for* 4.15 should use `jakarta.persistence`, as 4.14's test-time API (`javax.persistence-api`) is not part of a 4.15 build.

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

Load-time conversion of `javax.persistence` CorDapps (`CordappClassLoader`, `JavaxPersistenceTransformer` in
`common/javax-persistence-transformer`):

* Unit tests in `node-api` compile Java sources against a stub of the `javax.persistence` API at test time (the real one is not on the
  classpath of a 4.15 build) and check the converted classes against the real `jakarta.persistence` API: annotations with nested
  annotations and enum values, an `AttributeConverter`, a method taking an `EntityManager`, a lambda typed on it, a `catch` of a JPA
  exception, the `@Type` substitutions, the refusal of an unknown `@Type` and of `@TypeDef`, a dual-annotated class left alone, a bundled
  copy of the `javax.persistence` API left under its own names, and the code source and package of classes loaded from a jar.
* Quasar: the conversion runs before Quasar's agent sees a class, so Quasar instruments the converted bytes, and the node's class loader
  exclusion (`net.corda.core.serialization.internal.**`, the attachments class loader) does not cover the CorDapp class loader, so its
  classes are instrumented as before. `CordappClassLoaderQuasarTest` runs, under the agent with instrumentation verification on, a fiber
  through converted `@Suspendable` methods whose signatures name `EntityManager`, in both load orders; the caller-first order exercises
  Quasar's jar-based lookup and its "assuming suspendable" fallback (see [Things you may notice](#3-things-you-may-notice)). Neither
  Archive Service jar has a converted class with a `@Suspendable` method, so the end-to-end run below did not exercise this path.
* End to end: the Archive Service CorDapp 2.0 (built against Corda 4.12, `javax.persistence` only, with its own JPA repositories and
  `withEntityManager` code) and its demo CorDapp, deployed unchanged on a two-node network (notary and archiver, H2) built from this
  branch with the network bootstrapper. Both jars were converted at load time; the demo's `CreateLedgerFlow` created 80 transactions
  over 280 state rows through the converted schema; `process-all-pending`, `list-items`, `create-snapshot`, `export-snapshot`
  (transaction zip, manifest and 280 queryable-state rows to CSV) and `delete-vault` ran to completion and emptied the vault and
  model tables. Multi-schema mode (the CorDapp's own Hibernate 5 session factory for a PostgreSQL backup schema) was not run; it uses
  Hibernate 5 classes directly and is outside what the conversion covers.

The javax persistence test agent (`testing/javax-persistence-agent`, published as `corda-javax-persistence-test-agent`), whose tests run
in that module's `test` task under the agent's own self-contained jar and Quasar's agent (with instrumentation verification on):

* A test CorDapp built against Corda 4.12, `javax.persistence-api` 2.2 and Hibernate 5 (`testing/cordapps/javax-persistence`), none of
  which is on the test runtime classpath: a `QueryableState` with a `@Type(type = "uuid-char")` column, a flow that records it and one
  that counts it through `withEntityManager` with a `@Suspendable` helper typed on `javax.persistence.EntityManager`, and test code with
  a `withEntityManager` block typed on the javax API. Run through `MockServices` (vault queries on the label and on the UUID column),
  a `MockNetwork` node (both flows, a vault query and the test-code block) and an in-process driver node (both flows over RPC and a vault
  query over RPC).
* The `node-api` fixture loaded through a plain `URLClassLoader` under the agent: the conversion, the bundled-API case, a dual-annotated
  class left alone, the console message and the recorded reason for an unknown `@Type` and for `@TypeDef`, and the fiber test of
  `CordappClassLoaderQuasarTest` repeated with the agent doing the conversion, in both load orders.
* A second test CorDapp whose schema uses `@TypeDef`: `MockServices` fails with the node's message carrying the agent's reason.
* Without the agent (`node` module, `JavaxPersistenceWithoutAgentTest`): the same test CorDapp is refused with the message that names the
  agent.

**Not tested** (no environment available): Microsoft SQL Server, Azure SQL, Oracle, and PostgreSQL versions other than 16. Hibernate 7
maps some Java types differently on these databases, and it is where an unnoticed difference is most likely. These need to be run by QA, against
a database created by a 4.14 node (not a fresh one) with a populated vault, **before release**. In particular check: startup in `validate` mode
(no `HibernateSchemaChangeException`), `Instant` columns, `UUID` columns, and the checkpoint tables on each database.
