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
| **A node operator**                           | Nothing to your node configuration or database. Check your database version is supported. CorDapps built for Corda 4.14 or earlier load without a rebuild: the node converts their `javax.persistence` classes to `jakarta.persistence` as it loads them, and refuses to start if a CorDapp uses something Hibernate 7 no longer has (see [CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)). Hibernate 7's behaviour changes apply to converted CorDapps as they do to rebuilt ones, so test them on a 4.15 node first. |
| **A CorDapp developer** (with a custom schema)| Nothing to run on 4.15. When you next rebuild, port your schema classes to `jakarta.persistence`; a script helps with most of it. A CorDapp that uses a Hibernate `@Type` other than the ones the node substitutes (listed below) must be ported before it can run on 4.15. |
| **A CorDapp developer** (no custom schema)    | Nothing. Code that calls `ServiceHub.withEntityManager` or uses JPA classes directly is converted like the schemas; port it when you next rebuild. Code that uses Hibernate classes directly is not converted: if it uses a class or member Hibernate 7 no longer has (`Session.save`), the node refuses the CorDapp at start-up and it must be ported. |

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
`ServiceHub.withEntityManager` blocks, `catch` clauses of the JPA exception types, and the Kotlin metadata of Kotlin classes (so Kotlin
reflection on a converted class sees the renamed types). The jar on disk is not touched, so its hash, its signature and the contract
constraints that depend on them are unaffected. A CorDapp that bundles the `javax.persistence` API jar inside its own jar is loaded as
well: the API classes are left as they are and only the CorDapp's classes are converted. The log shows, once per such CorDapp:

```
CorDapp /opt/corda/cordapps/my-cordapp-1.0.jar was built for Corda 4.14 or earlier (javax.persistence); its classes are converted to
jakarta.persistence for Hibernate 7 as they are loaded
```

A CorDapp built for Corda 4.15 (`jakarta.persistence`), or built to run on both (both sets of annotations, see
[A CorDapp that must run on 4.14 and 4.15](#7-a-cordapp-that-must-run-on-414-and-415)), is loaded as it is.

Hibernate's own annotations are not renamed, and several of them changed between Hibernate 5 and 7, so on a class it converts the node
converts or checks each one:

* `@Type(type = "...")`, whose members changed in Hibernate 6, is replaced by the `@JdbcTypeCode` Corda's own schemas use, or by a
  `@Convert` to a converter that stores the same format, for the names below (each also by the name of its Hibernate 5 type class, which
  `@Type` accepted too); the stored formats are unchanged:

| `@Type(type = "...")` on 4.14                                       | Replaced by                                                                 |
|---------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `uuid-char`, `org.hibernate.type.UUIDCharType`                       | `@JdbcTypeCode(SqlTypes.VARCHAR)`                                           |
| `corda-wrapper-binary`                                              | `@JdbcTypeCode(SqlTypes.VARBINARY)`                                         |
| `corda-blob`                                                        | `@JdbcTypeCode(CordaSqlTypes.CORDA_BLOB)`: a BLOB, `bytea` on PostgreSQL    |
| `materialized_blob`, `org.hibernate.type.MaterializedBlobType`      | `@JdbcTypeCode(SqlTypes.BLOB)`                                              |
| `materialized_clob`, `org.hibernate.type.MaterializedClobType`      | `@JdbcTypeCode(SqlTypes.CLOB)`                                              |
| `uuid-binary`, `org.hibernate.type.UUIDBinaryType`                   | `@JdbcTypeCode(SqlTypes.BINARY)`                                            |
| `binary`, `org.hibernate.type.BinaryType`                            | `@JdbcTypeCode(SqlTypes.VARBINARY)`                                         |
| `text`, `org.hibernate.type.TextType`                                | `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`                                       |
| `pg-uuid`, `org.hibernate.type.PostgresUUIDType`                     | `@JdbcTypeCode(SqlTypes.UUID)`: PostgreSQL's native `uuid`                  |
| `yes_no`, `true_false` (and their type classes)                      | `@Convert(converter = YesNoBooleanConverter / TrueFalseBooleanConverter)` with `@JdbcTypeCode(SqlTypes.CHAR)`: Corda's converters store 'Y'/'N' and 'T'/'F' in a `CHAR` column and read it as Hibernate 5 did (padded or not, either case), so the column a 4.14 node used passes validation on every database |
| `numeric_boolean` (and its type class)                               | `@Convert(converter = NumericBooleanConverter)`: Hibernate 7's own converter, 1/0 |
| `string` (and `org.hibernate.type.StringType`), on a `String`      | `@JdbcTypeCode(SqlTypes.VARCHAR)`: the explicit type opted the column out of nationalised character data on SQL Server (`varchar`, not `nvarchar`), and the explicit JDBC type keeps it so |
| `character` (and `org.hibernate.type.CharacterType`), on a `Character` or `char` | `@Convert(converter = SingleCharacterConverter)` with `@JdbcTypeCode(SqlTypes.CHAR)`: a `CHAR` column as before, read by its first character (the rest is padding, and a stored space is a space), so a `CHAR(255)` column that Hibernate 5's DDL made on H2 reads as it did |
| `long`, `integer`, `short`, `byte`, `boolean`, `double`, `float`, `big_decimal`, `big_integer`, `timestamp`, `date`, `time`, `instant` (and their type classes) | Nothing, on a member of the Java type the name was the default mapping for (`@Type(type = "long")` on a `Long`): Hibernate 7's default mapping of the member is the same. For these, as for `string` and `character`, a `@Type` on a member of another type was a choice, and the class is refused |

* `@Where`, `@WhereJoinTable` and `@OrderBy`, which Hibernate 7 renamed, become `@SQLRestriction`, `@SQLJoinTableRestriction` and
  `@SQLOrder`. `@DynamicInsert(true)` and `@DynamicUpdate(true)` lose the member Hibernate 7 no longer has, and `@DynamicInsert(false)`
  and `@DynamicUpdate(false)`, which only said what the absence of the annotation says, are dropped. `@Generated(GenerationTime.INSERT)`
  becomes `@Generated(event = INSERT)`, `ALWAYS` becomes `{INSERT, UPDATE}`, and `NEVER`, which meant not generated, drops the annotation.
* Members Hibernate 7 reads differently but which mean the same are rewritten: the `@Cascade` constants it removed, `DELETE` to
  `REMOVE`, `EVICT` to `DETACH` and `SAVE_UPDATE` to `PERSIST`. Hibernate 5's save-update cascade saved new children reachable from a
  managed parent when the session flushed, and never fired for `merge`; Hibernate 7's `PERSIST` cascade does the same at flush and
  leaves `merge` alone too (verified on H2, PostgreSQL, SQL Server and Oracle), which is why `MERGE` is not added. One difference
  remains: a *detached* child reachable from a managed parent at flush was re-attached and updated by Hibernate 5. Hibernate 7 treats it
  as new and inserts it, so the flush fails with a duplicate-key error (or `detached entity passed to persist` for a generated id) and the
  transaction rolls back. Code that relied on it must `merge` the child and add the managed copy the merge returns instead; nothing
  Hibernate 7 cascades at flush reproduces the old behaviour. `@ParamDef(type = "boolean")` and the other basic type names above become the
  Java class Hibernate 7 wants; `@Cache(include = "non-lazy")` becomes `includeLazy = false`; `CacheModeType` in `@NamedQuery` and
  `@NamedNativeQuery` becomes `CacheMode`.
* `@Index` and `@ForeignKey` only affected the DDL Hibernate generated, which a node does not use (Liquibase creates its tables); they
  are dropped, with a warning in the log that names the class and member, and so is a `@Table` (or each `@Table` of a `@Tables`) that
  sets only `appliesTo`, `indexes`, `comment` or `foreignKey`. A `@Table` that sets `fetch`, `inverse`, `optional`, `sqlInsert`,
  `sqlUpdate` or `sqlDelete` changed how Hibernate read and wrote the secondary table, so it is **refused**: Hibernate 7 expresses those
  through `@SecondaryRow` and the `table` member of `@SQLInsert`, `@SQLUpdate` and `@SQLDelete`, which the rebuilt CorDapp uses instead.
* Every other Hibernate annotation is checked against Hibernate 7, member by member. The JVM would silently drop an annotation
  Hibernate 7 no longer has, or a member it no longer has or declares differently, and the mapping would change without a trace; the
  node **refuses to start** instead, with a message naming the class, the annotation and the member.

**Hibernate 5 annotations that make the node refuse a CorDapp.** Check the schema classes of each CorDapp built for 4.14 or earlier for
these before upgrading, or start a 4.15 test node with the CorDapps in its `cordapps` directory: the start-up pass goes through every
class of every jar and reports all of them at once, so a test node is the pre-upgrade check.

* `@TypeDef` and `@TypeDefs`, and a `@Type` other than the names in the table above (a custom type, or a basic type on a member of
  another type).
* `@Table` with any of the members above.
* Annotations Hibernate 7 removed, for which it has no equivalent: `@Loader`, `@Polymorphism`, `@LazyCollection`, `@Proxy`,
  `@SelectBeforeUpdate`, `@Columns`, `@AccessType`, `@GeneratorType`, `@Target`, `@Persister`, `@Tuplizer`, `@IndexColumn`, `@Sort`.
* Members Hibernate 7 removed or declares differently, without an equivalent the node can write: `@Any` and `@ManyToAny` with
  `metaColumn` or `metaDef`, `@CollectionId` with `columns` or `type`, `@CollectionType(type = "...")` (a class in Hibernate 7),
  `@ParamDef(type = "...")` naming anything but the basic types above, `@Cache(include = ...)` with a value other than `all` or
  `non-lazy`.

The message names the class and the member in every case, for example:

```
The class com.example.MyState$PersistentMyState uses the Hibernate 5 annotation @Type(type = "my-type") on amount, which has no equivalent
in Hibernate 7 that the node can substitute (it substitutes [uuid-char, org.hibernate.type.UUIDCharType, corda-wrapper-binary, ...]).
The CorDapp that contains the class needs to be rebuilt for Corda 4.15 ...
```

Nothing is changed in the database when this happens. Obtain an updated CorDapp from its vendor (or see the developer sections if you
build it yourself) **before** starting the node on 4.15.

The conversion also leaves alone Hibernate classes other than annotations, such as the `Session` API reached through
`EntityManager.unwrap`. When it starts, the node goes through every class built for `javax.persistence` in every CorDapp jar, and
resolves each Hibernate class and member such a class refers to (supertypes, field and method types, calls, field accesses, casts, `catch`
clauses) against its Hibernate 7. A reference to something that no longer exists (`Session.save`, `update` and `delete`, the Hibernate 5
type classes) would fail with a `NoSuchMethodError` or `NoClassDefFoundError` when that code ran, so the node **refuses to start**
instead, listing every class that cannot be converted and what is wrong with it:

```
2 class(es) of the CorDapps built for Corda 4.14 or earlier cannot be converted for Hibernate 7 (Corda 4.15):
  - /opt/corda/cordapps/my-cordapp-1.0.jar: The class com.example.Repo uses Hibernate classes or members that do not exist in Hibernate 7
    (Corda 4.15), so that code would fail when it runs: method org.hibernate.Session.save(java.lang.Object).
  - /opt/corda/cordapps/my-cordapp-1.0.jar: The class com.example.MyState$PersistentMyState uses the Hibernate 5 annotation ...
The CorDapps that contain them need to be rebuilt for Corda 4.15 ...
```

References to Hibernate classes and members that still exist (the exception types, `Session.flush`, `createQuery`) pass, and that code
runs against Hibernate 7 unchanged. A Hibernate library the CorDapp bundles itself and the node does not ship (Hibernate Validator, for
example) is not checked against the node's Hibernate.

The check is stricter than the JVM, which links lazily: a class is refused for a reference in a method that may never run, whether in
the CorDapp's own code or in a library it bundles. If you cannot get a rebuilt CorDapp and accept that such code fails only when it runs,
start the node with `-Dnet.corda.cordapp.missingHibernateReferences=warn` (on the `java` command line, or through `custom.jvmArgs` in
`node.conf`, like any JVM option): the references are then logged as warnings at start-up and the classes are loaded anyway. The node
reads the setting once, when it starts. The annotation checks above are not affected by it, as those failures would be silent.

Two limits of the check. It only looks at classes that refer to `javax.persistence`, because those are the ones the node converts; a
class that uses a removed Hibernate class without touching JPA (a Hibernate 5 custom type, say) is loaded as it is and fails when it runs,
as after any other library upgrade. And it cannot check behaviour, which is the next paragraph.

**What the conversion does not change is Hibernate itself.** A converted CorDapp runs on Hibernate 7 with Corda's compatibility
settings, exactly as a CorDapp rebuilt for 4.15 does, so every behaviour change listed under
[Changes the script cannot make for you](#3-changes-the-script-cannot-make-for-you) applies to it too: HQL and JPQL are validated
strictly, `persist` of a duplicate marks the transaction for rollback, native queries return `Long` where they returned `BigInteger`,
`LIKE` patterns no longer treat a backslash as an escape on H2 and PostgreSQL, `NOT IN` an empty list matches every row, and an entity
attribute of an array type is refused when the node starts. A 4.14 CorDapp that relies on any of these needs the same change a rebuilt
one would, so run its flows on a 4.15 test node before relying on the conversion in production.

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
* With a converted CorDapp, Quasar (the flow framework's instrumentation agent) may print, on the console at start-up, a line such as
  `[quasar] WARNING: Method not found in class - assuming suspendable: com/example/Repo#find(Ljakarta/persistence/EntityManager;...)`.
  It is harmless. Quasar instruments the converted classes as the node loads them, but for a class that has not been loaded yet it reads
  the method list from the jar, where the method still has its `javax.persistence` signature; a call through the converted signature is
  then not found and Quasar treats it as it treats any unknown method, as possibly suspending, which is the safe assumption. The line
  appears only for a method whose signature names a `javax.persistence` type and only when its caller is loaded first.

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

**A `Character` in a `CHAR` column wider than one character.** Hibernate 5's own DDL made a `Character` (or `yes_no` / `true_false`)
column `CHAR(255)` on H2, and Hibernate 5 read the space-padded value it got back. Hibernate 7 rejects it (`CoercionException: value
contains more than one character`). This affects H2 schemas that Hibernate generated, for example dev nodes and scripts from the 4.14
migration exporter; a hand-written `CHAR(1)` column is fine. A rebuilt CorDapp should either change the column to `CHAR(1)` or map the
attribute through its own `AttributeConverter<Character, String>` that takes the first character of what it reads (not one that trims it,
which would turn a stored space into null; Hibernate 5 read a space), declared with `@JdbcTypeCode(SqlTypes.CHAR)`;
the node does the latter for a converted CorDapp (see the `character` row in the operators' table above).

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

**`withEntityManager` receives a `jakarta.persistence.EntityManager`.** The block passed to `serviceHub.withEntityManager` receives a
`jakarta.persistence.EntityManager` in 4.15, so a CorDapp rebuilt for 4.15 has to bind to that type; the script renames the import. A
CorDapp built for 4.14 that is deployed unchanged is not affected: the node's class loader renames its `javax.persistence` references,
the lambda's included, as it loads the CorDapp (see [CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)).
Run the tests of every flow that uses `withEntityManager` after the rebuild.

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

The conversion of `javax.persistence` classes only happens in the node's CorDapp class loader, which loads CorDapps from jars in the node's
`cordapps` directory. Classes that reach the node through its own classpath are loaded unchanged, and a node that finds such a class still
built against `javax.persistence` in a schema fails fast with a message that says so. That is the case for:

* `MockNetwork` and `MockServices`, and an **in-process** driver node (`startInProcess = true`): the CorDapp's classes are on the test's
  classpath.
* Your own CorDapp under test, supplied from its class directory (`cordappsForPackages`, `TestCordapp.findCordapp` on a project's own
  classes), on any kind of node.

An **out-of-process** driver node (the default) does convert a CorDapp supplied as a jar, for example a third-party CorDapp built for
4.14 that your tests depend on: the driver removes jars that carry a CorDapp manifest from the node's classpath and the node loads them
from its `cordapps` directory, as a real node does. So, to test on 4.15: port your own CorDapp to `jakarta.persistence` (it is yours to
rebuild), and run tests that need an unported third-party jar on out-of-process driver nodes or a real node until its vendor ships a
4.15 build.

### 6. A CorDapp you cannot rebuild

Nothing needs to be done: the node converts it as described above. If it uses a Hibernate `@Type` the node cannot substitute, the
[Eclipse Transformer](https://github.com/eclipse/transformer) does not help either (it only renames the package); ask the vendor for a
4.15 build.

### 7. A CorDapp that must run on 4.14 and 4.15

A CorDapp built for Corda 4.14 (`javax.persistence`) runs on both: natively on 4.14, converted on 4.15. Treat the conversion as a
compatibility bridge for a CorDapp you cannot rebuild yet, not as a build target: on 4.15 it cannot use a Hibernate annotation, `@Type`
name, class or member that Hibernate 7 no longer has, and the Hibernate 7 behaviour changes above apply to it without the rebuild that
would have made them visible at compile time.

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
| `corda-blob`             | `@JdbcTypeCode(CordaSqlTypes.CORDA_BLOB)`: a regular BLOB, except `bytea` on PostgreSQL (needed for checkpoints) |
| `materialized_blob`      | `@JdbcTypeCode(SqlTypes.BLOB)` (Corda 4.14 registered its own version of this standard type)        |

The node applies the same replacements, plus `uuid-binary`, `binary` and `text`, when it converts a CorDapp built for 4.14 (see
[CorDapps built for Corda 4.14 or earlier](#2-cordapps-built-for-corda-414-or-earlier)).

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

Load-time conversion of `javax.persistence` CorDapps (`CordappClassLoader`, `JavaxPersistenceTransformer`):

* Unit tests in `node-api` compile Java sources against a stub of the `javax.persistence` API at test time (the real one is not on the
  classpath of a 4.15 build) and check the converted classes against the real `jakarta.persistence` API: annotations with nested
  annotations and enum values, an `AttributeConverter`, a method taking an `EntityManager`, a lambda typed on it, a `catch` of a JPA
  exception, the `@Type` substitutions, the refusal of an unknown `@Type` and of `@TypeDef`, a dual-annotated class left alone, and the
  code source and package of classes loaded from a jar.
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

**Not tested** (no environment available): Microsoft SQL Server, Azure SQL, Oracle, and PostgreSQL versions other than 16. Hibernate 7
maps some Java types differently on these databases, and it is where an unnoticed difference is most likely. These need to be run by QA, against
a database created by a 4.14 node (not a fresh one) with a populated vault, **before release**. In particular check: startup in `validate` mode
(no `HibernateSchemaChangeException`), `Instant` columns, `UUID` columns, and the checkpoint tables on each database.
