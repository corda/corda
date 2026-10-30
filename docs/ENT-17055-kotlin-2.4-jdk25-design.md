# ENT-17055: Kotlin 2.4.21, JDK 25 and Gradle 9 for Corda 4.16

This document describes the changes made on the `adel/ENT-17055` branches of Corda Open Source (`corda`) and Corda Enterprise
(`enterprise`), the design of the multi-version ("contract generation") transaction verification, and every decision that had to be
made while the work was done unattended.

## 1. Summary

| | Before (4.15) | After (4.16) |
|---|---|---|
| Kotlin (compiler, stdlib, language/API version) | 1.9.20 | 2.4.21 |
| JDK (build, bytecode target, runtime) | 17 | 25 (Azul Zulu 25.0.5 used locally) |
| Gradle | 7.6.4 | 9.8.2 |
| `cordaVersion` / `platformVersion` | 4.15 / 170 | 4.16 / 180 |
| External verifiers | one (Kotlin 1.2, for 4.11 transactions) | two: Kotlin 1.2 (4.11) and Kotlin 1.9 (4.12 to 4.15) |
| Transaction attachment groups | `ATTACHMENTS_GROUP` (4.11), `ATTACHMENTS_V2_GROUP` (4.12+) | plus `ATTACHMENTS_V3_GROUP` (4.16+) |
| Node legacy directories | `legacy-contracts`, `legacy-jars` | plus `legacy-contracts1.9`, `legacy-jars1.9` |

Commits (both repos, branch `adel/ENT-17055`, **not pushed**):

| # | OS (`corda`) | ENT (`enterprise`) |
|---|---|---|
| 1 | Upgrade the build to Gradle 9.8.2 (also needs the Kotlin Gradle plugin 2.x, so the tooling moves to Kotlin 2.4.21 / JDK 25) | Upgrade the build to Gradle 9.8.2, Kotlin 2.4.21 and JDK 25 |
| 2 | Compile with Kotlin 2.4.21 and run on JDK 25; Corda 4.16, platform version 180 | Compile with Kotlin 2.4.21 and run on JDK 25 (incl. all the new K2 warnings, as ENT builds with warnings as errors) |
| 3 | Verify each contract generation's attachments with the matching verifier (the framework below) | Port of the framework |
| 4 | Require Java 25 to run the node, and fix capsules on Java 25 | (included in 2) |
| 5 | Remaining Gradle 9 and Java 25 capsule fixes | |
| 6 | Expose internal verification functions without `@Suppress("INVISIBLE_MEMBER")`, `-jvm-default=enable` | (used by 3) |
| 7 | Make Quasar work on Java 25 (patched Quasar, `-Xlambdas=class`) | Make Quasar work on Java 25 |
| 8 | Fix Quasar instrumentation of suspendable calls in constructor arguments; EC key size test fix | EC key size test fix |
| 9 | This document | This document (`docs/internal/`) |

Plus, in a worktree of `corda-gradle-plugins` (`../corda-gradle-plugins-ENT-17055`, branch `adel/ENT-17055` from `origin/release/5.1`,
version `5.1.4-SNAPSHOT`): two commits making the Corda Gradle plugins work on Gradle 9 (see 5.2).

## 2. Background: what 4.12 did

Corda 4.12 moved from Kotlin 1.2 / JDK 8 to Kotlin 1.9 / JDK 17. Contracts compiled for one cannot be loaded by the other, yet 4.11 and
4.12 nodes need to share transactions. 4.12 solved this by:

* Adding `ATTACHMENTS_V2_GROUP` for the 4.12 contract attachments, keeping the original `ATTACHMENTS_GROUP` for the 4.11 attachments.
  A 4.12 node on a network with `minimumPlatformVersion < 140` puts both the 4.12 and 4.11 versions of each contract in the transaction.
* A `legacy-contracts` node directory holding the 4.11 versions of the contracts, and `legacy-jars` for extra 3rd party jars.
* An external verifier process, built from the same `core`/`serialization` source but with the Kotlin 1.2 compiler, which verifies the
  4.11 part of a transaction. A transaction with both parts is verified in-process *and* externally, and the results must agree.

## 3. Design: contract generations

Rather than adding a second hard-coded "legacy" concept, the 4.11/4.12 split has been generalised into an ordered list of **contract
generations**, defined in one place: `net.corda.core.internal.verification.ContractGeneration` (core).

```kotlin
enum class ContractGeneration(attachmentsGroup, kotlinVersion, maxClassFileMajorVersion, platformVersion, releases,
                              legacyContractsDirName, legacyJarsDirName) {
    CORDA_4_11(ATTACHMENTS_GROUP,    Kotlin 1.2, Java 8,  PV 1,   "4.11 or earlier", "legacy-contracts",    "legacy-jars"),
    CORDA_4_12(ATTACHMENTS_V2_GROUP, Kotlin 1.9, Java 17, PV 140, "4.12 to 4.15",    "legacy-contracts1.9", "legacy-jars1.9"),
    CORDA_4_16(ATTACHMENTS_V3_GROUP, Kotlin 2.4, Java 25, PV 180, "4.16 or later",   null, null);
}
```

* The last generation is `CURRENT` and is verified in-process. The others (`LEGACY`) each have their own external verifier, built with
  that generation's Kotlin version, and their own legacy directories.
* `isRequiredFor(minimumPlatformVersion)`: a generation's attachments are added to new transactions while the network's minimum platform
  version is below the *next* generation's platform version (i.e. older nodes may still be on the network). So 4.11 attachments are
  included while minPV < 140 and 4.12 to 4.15 attachments while minPV < 180.
* `LanguageVersion.isCompatibleWith(generation)` replaces `isLegacyCompatible`/`isNonLegacyCompatible` (kept as convenience properties):
  the CorDapp's bytecode must be no newer than the generation's Java version, and any Kotlin metadata must match the generation's Kotlin
  version. Java-only CorDapps are compatible with every generation that supports their bytecode.

Everything else is written in terms of `ContractGeneration`, so no code mentions "1.9" or "4.12" specifically:

| Area | Change |
|---|---|
| `TraversableTransaction` | `attachmentsFor(generation)`, `attachmentGenerations`. `attachments` (public API) returns the newest generation's attachments present. `allAttachments` is the union. Filtered transactions handle every attachments group. |
| `createComponentGroups` | New overload taking `attachmentsByGeneration`; the old signature delegates to it. |
| `WireTransaction.tryVerify` | The current generation is verified in-process; every legacy generation present is verified by its external verifier. |
| `WireTransaction.toLedgerTransactionInternal` | Uses `VerificationSupport.contractGeneration`'s group, so the same source works for the node and each external verifier. |
| `VerificationResult` | Holds the in-process result plus a map of external results per generation. `enforceSuccess` requires *all* versions to agree (the newest legacy result is the primary one when there's no in-process result). |
| `VerificationSupport` | `contractGeneration` (default `CURRENT`); `getTrustedClassAttachments(className, generation)`. |
| `ExternalVerifierHandle` | `verifyTransaction(ctx, generation)`. |
| `ExternalVerifierHandleImpl` (node) | One lazily started verifier process per legacy generation, each embedded in the node as `external-verifier-<kotlin>.jar`, with its own socket, `legacy-jars*` classpath and log files (the 4.11 verifier keeps its existing log names; the others are `verifier-1.9-*`). ENT keeps its verified-transaction cache (now keyed by generation), single-thread executor (one per generation) and party pre-caching. |
| `TransactionBuilder` | Adds each required legacy generation's attachments, warns once if a required legacy version is missing, and fixes missing dependencies per generation. |
| `CordappProviderInternal` / `CordappLoader` | `legacyContractCordapps: Map<ContractGeneration, List<CordappImpl>>`; `ContractAttachmentWithLegacy.legacyAttachments` map (`legacyAttachment` kept for 4.11). |
| `JarScanningCordappLoader` | Loads each generation's `legacy-contracts*` directory (`legacyContractsDirs(baseDirectory)`), checks each CorDapp is compatible with its directory's generation (with messages saying which directory it belongs in), requires a current version of every legacy contract, and that versionIds increase from each generation to the next. `legacyGenerationOf(jar)` finds the directory a contract CorDapp belongs in. In ENT, the "treat as a pre-4.12 node" logic is generalised to "the oldest generation all the CorDapps are compatible with". |
| External verifier (`verifier/src`) | Takes the generation as a command line argument, checks it is running the matching Kotlin version, and uses that generation's attachments. It now uses the public `@CordaInternal` accessors in `InternalVerification.kt` rather than `@Suppress("INVISIBLE_MEMBER")`, so it will also compile with Kotlin 2 when 4.16 becomes a legacy generation. |
| Jackson `WireTransaction` JSON | Legacy attachments are suffixed `-legacy` (4.11, unchanged) or `-legacy-<kotlin>` (e.g. `-legacy-1.9`). |
| Test infrastructure | The driver places each `legacyContracts` CorDapp in the directory of the oldest generation it's compatible with; smoke-test `NodeParams.legacyContractJarsByGeneration`; `NodeProcess` runs 4.12 to 4.15 nodes on `JAVA_17_HOME`. |

### 3.1 Verification matrix

| Transaction built by | Groups present | Verified on a 4.16 node by |
|---|---|---|
| 4.16, minPV ≥ 180 | V3 | in-process |
| 4.16, 140 ≤ minPV < 180 (with `legacy-contracts1.9`) | V3, V2 | in-process + Kotlin 1.9 verifier |
| 4.16, minPV < 140 (with both legacy dirs) | V3, V2, V1 | in-process + Kotlin 1.9 + Kotlin 1.2 verifiers |
| 4.12 to 4.15 | V2 (+ V1) | Kotlin 1.9 verifier (+ Kotlin 1.2 verifier) |
| 4.11 | V1 | Kotlin 1.2 verifier |

4.12 to 4.15 nodes ignore the unknown V3 group (component groups are positional in the Merkle tree, so unknown ordinals are already handled)
and verify the V2 group, which is exactly what they did before.

### 3.2 Build structure for the external verifiers

* `buildSrc/.../LegacyKotlin.groovy`: `LegacyKotlinVersion` enum (`KOTLIN_1_2`, `KOTLIN_1_9`) holding each old compiler's version,
  language/API version and JVM target. `corda.kotlin-1.2` and `corda.kotlin-1.9` are one-line precompiled plugins applying it.
* OS `settings.gradle` creates `core-<v>`, `serialization-<v>` and `verifier-<v>` for each version in one list (`['1.2', '1.9']`).
  `core-<v>`/`serialization-<v>` compile the existing `core`/`serialization` source with that compiler (published as
  `corda-core-<v>`/`corda-serialization-<v>`). `verifier-<v>` compiles the shared `verifier/src`. ENT only has `verifier-<v>`, consuming
  the OS artifacts.
* The node embeds each `verifier-<v>` shadow jar as `external-verifier-<v>.jar` (`node/build.gradle`, same list).

The old `verifier` project became `verifier-1.2`; its source stays in `verifier/src` and is shared.

### 3.3 Adding the next generation (e.g. Corda 5.x on Kotlin 3.x / JDK 29)

1. `ComponentGroupEnum`: add `ATTACHMENTS_V4_GROUP` at the end.
2. `PlatformVersionSwitches`: add the switch for the new release's platform version.
3. `ContractGeneration`: add the new constant at the end, and give `CORDA_4_16` its directory names (e.g. `legacy-contracts2.4`,
   `legacy-jars2.4`).
4. `buildSrc` (both repos): add `KOTLIN_2_4` to `LegacyKotlinVersion` and a `corda.kotlin-2.4.gradle` plugin.
5. OS `settings.gradle` and ENT `settings.gradle`: add `'2.4'` to the list; create `core-2.4`, `serialization-2.4`, `verifier-2.4`
   (copy the 1.9 ones). Add `'2.4'` to the list in `node/build.gradle` (both repos).
6. Bump the contract CorDapps' `versionId` (e.g. finance contracts) so each generation's version is higher than the previous.
7. If `core` starts using stdlib APIs newer than an old Kotlin version, add copies of them to that version's `core-<v>` module (as
   `core-1.2` does).
8. Update the tests that enumerate generations (`ContractGenerationTest`) and add test jars for the new legacy generation.

## 4. Testing

New/updated tests (OS, mirrored in ENT where the module exists):

* `ContractGenerationTest`: ordering, groups and their fixed ordinals, directory names, `isRequiredFor`, the language version
  compatibility matrix, unsupported versions.
* `VerificationResultTest`: every combination of agreeing/disagreeing in-process and legacy results.
* `DetermineLanguageVersionTest`: Kotlin 1.2, 1.9 (4.14.4 finance contracts) and 2.4 jars.
* `JarScanningCordappLoaderTest`: both legacy directories together, 1.9-only, wrong directory in each direction (with the expected
  messages), missing current version, versionId ordering between legacy generations, `legacyContractsDirs`, `legacyGenerationOf`.
* `CordappProviderImplTests`: which legacy attachments are returned for each minimum platform version.
* `TransactionBuilderMockNetworkTest` (end-to-end, real external verifier processes on JDK 25): a transaction with V3+V2 (verified
  in-process and by the Kotlin 1.9 verifier), with V3+V2+V1 (in-process and both verifiers), and on a network with minPV 140 (no V1).
* `ExternalVerificationKotlin19Test` (smoke test): a real Corda 4.14.4 node (on JDK 17) and 4.16 nodes exchanging cash in both directions.

The 4.12 to 4.15 test CorDapps are the published `net.corda:corda-finance-contracts/workflows:4.14.4` (Kotlin 1.9, Java 17, signed with
the dev key), the same approach the 4.11 tests use. See section 7 for the test results.

## 5. Decisions and workarounds

### 5.1 Versions

* Kotlin **2.4.21** (confirmed). Main build `languageVersion`/`apiVersion` 2.4, `jvmTarget` 25.
* Gradle **9.8.2**, latest 9.x on 2026-10-29. JaCoCo 0.8.16, Shadow `com.gradleup.shadow` 9.6.1, javafxplugin 0.1.0, JavaFX 25.0.5 (11.0.2
  has no macOS aarch64 or JDK 25 support), JFrog build-info 6.0.4, Dokka 2.2.0 in V2 mode, ENT Develocity plugin 4.6.1 (Gradle Enterprise
  3.8.1 doesn't support Gradle 9).
* JDK: Azul Zulu **25.0.5** downloaded to `~/Library/Java/JavaVirtualMachines` (as agreed). The node now requires Java 25
  (`Node.isInvalidJavaVersion`, the caplets, `Min-Java-Version` of the capsules) and the Docker images use JDK 25 base images
  (`azul/zulu-openjdk:25`, `amazoncorretto:25`; **not built/verified** here).
* `cordaVersion` 4.16, `platformVersion` 180 (confirmed). The finance contracts `versionId` is bumped 2 → 3, as 4.12 bumped it 1 → 2,
  because a CorDapp's version must increase between generations.
* Kotlin 1.9 legacy compiler: **1.9.20**, the version 4.12 to 4.15 used (not 1.9.25).
* Only Kotlin 2.4 metadata is accepted for current CorDapps (2.0 to 2.3 are rejected as unsupported), mirroring 4.12 which only accepted
  1.9. This is strict; relaxing it would be a one-line change in `LanguageVersion.Bytecode`/`ContractGeneration`.

### 5.2 Gradle 9

* The usual removed APIs were migrated mechanically: project-level `sourceCompatibility`, `Jar.baseName/classifier/archiveName`,
  `JavaExec.main`, `mainClassName`, `substitute ... with`, `sourceSets.main.java.outputDir`, resolving `runtimeOnly`/`implementation`,
  adding a configuration as a dependency (now `extendsFrom`), dependency `force`, `project(':x').sourceSets` inside `dependencies {}`,
  `ProjectDependency.dependencyProject`, configurations declared before the source sets which create them.
* **Corda Gradle plugins**: 5.1.x calls `CopySpec.setFileMode/setDirMode` and `Configuration.files(Dependency)`, both removed in
  Gradle 9. Fixed in a worktree of `corda-gradle-plugins` (`adel/ENT-17055`, from `origin/release/5.1`, version **5.1.4-SNAPSHOT**,
  published to `mavenLocal`). The plugins are still built with their Gradle 7.6 build, so the new permissions API is called reflectively
  when available. **This needs releasing to Artifactory** before CI can build either repo.
* **Capsule plugin** (`us.kirchmeier.capsule` 1.0.5_r3) sets the removed `classifier` property in its task constructor and uses
  `ConfigureUtil`. Building a patched fork from GitHub wasn't permitted in this session, so it was replaced by a small in-repo
  implementation, `buildSrc/.../FatCapsule.groovy` (`corda.capsule` plugin), supporting the subset of the DSL Corda uses with the same
  manifest attributes. It's simpler than the original and removes a third party build dependency.
* **Legacy Kotlin compilers**: `corda.kotlin-1.2` used internal Gradle API (`DefaultSourceDirectorySet`) and ran the compiler in the
  daemon. Rewritten as `LegacyKotlinVersion` using public API only, and the compiler is **forked on a JDK 17 toolchain**: the Kotlin 1.2.71
  and 1.9.20 compilers crash on JDK 25 (`IllegalArgumentException: 25.0.5.0.3` from IntelliJ's `JavaVersion.parse`). The compiled code
  targets Java 8 / 17 and runs on JDK 25 (verified by the tests). **CI agents need a JDK 17** that Gradle can detect (or set
  `org.gradle.java.installations.paths`).
* Dokka V1 mode uses `ConfigureUtil`, so `docs` was migrated to the Dokka V2 DSL (not run here).

### 5.3 Kotlin 2 (K2) source changes

* `mapToSet` (public inline) used the stdlib's internal `mapCapacity` via `@Suppress("INVISIBLE_MEMBER")`, which K2 rejects; the same
  formula is inlined.
* Stricter inference fixes (`CustomSerializerRegistry`, `FlowLogicRefFactoryImpl`, `InfrequentlyMutatedCache`, some tests), and tests
  which accessed internal members with `INVISIBLE_MEMBER` now use the public `@CordaInternal` `attachmentsFor`.
* `core` (and `serialization`) source must still compile with the Kotlin 1.2 **and** 1.9 compilers, since the external verifiers are
  built from it. All new core code is written in Kotlin 1.2-compatible style.

### 5.4 JDK 25 changes

* `jdk.incubator.foreign` was removed: `SharedMemoryIncremental` uses the final `java.lang.foreign` API (no unsafe fallback needed).
* `sun.security.ec` moved into `java.base`: the `--add-opens` were updated.
* The attachments class loader accepts class file version 69.
* **Capsule cannot parse 5-part Java versions** such as Azul's `25.0.5.0.3` ("Could not parse version"), which stops `corda.jar` starting.
  `CordaCaplet` (and the webserver caplet) override `chooseJavaHome` to use the current JVM with the version trimmed to 3 parts, unless
  `capsule.java.home` is set. **This also affects production on such JDK builds.**
* `jarsigner` defaults to SHA-384 since JDK 20, and re-signing a JAR signed with SHA-256 rewrites the manifest and invalidates the existing
  signatures. The test utility now re-signs with the JAR's existing digest algorithm.
* Kotlin reflection no longer returns constructors in declaration order (one test selected `constructors.last()`).

### 5.5 Quasar (blocker, worked around)

**Quasar 0.9.3_r3 cannot run on Java 25**, so no flows (or anything with fibers) worked at first:

1. Its shaded ASM only supports class files up to Java 21, so Java 25 classes weren't instrumented ("Fiber class
   FlowStateMachineImpl has not been instrumented", `VerifyInstrumentationException`).
2. `ThreadAccess` looks up `Thread.target`, which Java 19+ moved into the final `Thread.holder.task`, so its static initialiser fails.
3. After upgrading its ASM: ASM 9.6+ calls `Interpreter.copyOperation` for both values produced by `DUP`, so Quasar's `TypeInterpreter`
   loses the original `NEW` value and can't instrument a suspendable call in constructor arguments, e.g.
   `subFlow(FinalityFlow(tx, initiateFlow(party)))` ("Unable to instrument class ...", `ArrayIndexOutOfBoundsException: Index -1`).
   Flows doing this fail at runtime (this is what broke `ScheduledFlowTests`).

Workaround: `tools/quasar-jdk25-workaround` (OS, a standalone build) produces `quasar-core:0.9.3_r3-asm9.11`, which is 0.9.3_r3 with ASM
9.11 relocated in, plus a patched `ThreadAccess` that keeps the target in a weak map when the field doesn't exist, plus a patched
`TypeAnalyzer` whose frames analyse `DUP` the way ASM 9.5 did. Both repos'
`constants.properties` use it. With it, the flow tests pass on JDK 25. **R3 needs to release a proper Quasar (e.g. 0.9.4_r3) with
these changes**, publish it to Artifactory, and delete the workaround.

Also, Kotlin 2.0 compiles lambdas and SAM conversions to `invokedynamic` by default, which Quasar can't instrument when they suspend.
Both builds now pass `-Xlambdas=class -Xsam-conversions=class` (the Kotlin 1.9 behaviour). **CorDapp developers using Kotlin 2.4 need
the same flags**; this should go into the CorDapp Gradle plugin and/or the 4.16 upgrade documentation.

### 5.6 Simplifications / design choices

* A single `ContractGeneration` table instead of separate "legacy"/"non-legacy" flags. Messages, directory names, the verifier jars and
  the attachment groups are all derived from it.
* External verifiers are chosen from what the transaction contains (which attachment groups are present), not by scanning jars.
* The verifier source is shared; each `verifier-<v>` module is a 25-line build file.
* `VerificationResult` requires every version to agree, not just the in-process and one external result.
* Verifier log file names: the 4.11 verifier keeps `verifier-<host>.log`/`verifier-stdout.log` for operational compatibility; newer ones
  are `verifier-<kotlin>-*`.
* The node jar is larger because it embeds two verifier fat jars (~40 MB each); shrinking them (e.g. minimising the shadow jars) is a
  possible follow-up.

### 5.7 Corda Enterprise specifics

* ENT builds with `compilation.warningsAsErrors=true`. Kotlin 2.4 and JDK 25 produce ~200 new warnings, all fixed rather than
  suppressed, except where the code legitimately needs the flagged construct (`Object()` monitors used with `wait`/`notify`, `ThreadDeath`
  in the uncaught exception handler, Hibernate deprecated statistics overrides), which have targeted `@Suppress`. The deprecated
  `URL(String)` constructor is replaced by `URI(..).toURL()` (97 places, mostly tests). The same warnings exist in OS (which doesn't
  build with warnings as errors) and were not changed there; this is something to watch on the next OS → ENT merge.
* `@Suppress("INVISIBLE_MEMBER")` no longer works in Kotlin 2, so `EnterpriseFinalityFlow` and the transaction validator use the new
  `verifyWithSupport`/`toLedgerTransactionWithSupport` accessors from OS core.
* `ExternalVerifierHandleImpl` keeps the ENT verified-transaction cache (keyed by `(generation, txId)` now), the single-thread executor
  (one per generation) and the party pre-caching.
* The "if all CorDapps are 4.11-compatible this is a pre-4.12 node" logic in `JarScanningCordappLoader` is generalised to the oldest
  generation all CorDapps are compatible with (so a node with only Kotlin 1.9 CorDapps is treated as a 4.12-4.15 node).
* `DelegatedRSAPrivateKey` must override `getParams()` because Java 22 added a default to `AsymmetricKey` as well as `RSAKey`.
* Gradle Enterprise plugin 3.8.1 doesn't support Gradle 9, so it's now the Develocity plugin 4.6.1. **The R3 server
  (gradle.dev.r3.com, Gradle Enterprise 2023.4.2) only supports plugin versions up to 3.16**, so build scans won't be accepted until the
  server is upgraded (the build itself is unaffected).
* `tools:flow-necromancer` (an IntelliJ 2020.1 plugin using `org.jetbrains.intellij` 1.17.4) can't be built on Gradle 9 and is excluded
  from `settings.gradle` with a TODO; it needs migrating to the IntelliJ Platform Gradle Plugin 2.x.
* A few tools targeted Java 6/8 bytecode, which JDK 25's javac no longer supports; they now use the default (25).

### 5.8 Local environment (not committed)

* `/etc/hosts` maps `software.r3.com` to `99.80.114.233`, an AWS host with the wrong certificate, and the real R3 Artifactory returns an
  SSO page, so Artifactory was unusable. **I didn't change system configuration.** Builds ran with a separate `GRADLE_USER_HOME` (in the
  session scratchpad) without the R3 credentials, sharing `~/.gradle/caches/modules-2`. OS resolved everything from the public
  repositories.
* ENT needs R3-private artifacts. Gradle 9 can't reuse Gradle 7's metadata cache, so a Maven-layout view of the existing download cache
  was created, served through an init script that also disables the unreachable Artifactory repositories. Some cached POMs were actually
  the Artifactory SSO HTML page; for `corda-solana-core:1.0.0` and `solana-programs:25.0.0-j17-1` minimal POMs were reconstructed in that
  local view. This is a local workaround only; CI with working Artifactory credentials doesn't need it.
* Your IDE's Gradle daemon holds the lock in `corda/.gradle`, so builds used `--project-cache-dir` in the scratchpad. I didn't stop it.
* Building with JDK 25: `JAVA_HOME`/`org.gradle.java.home` pointed at Zulu 25, with
  `-Porg.gradle.java.installations.paths=<JDK 17 home>` for the legacy compilers.

## 6. Follow-ups / not done

* **Release a Quasar supporting Java 25** (see 5.5) and remove `tools/quasar-jdk25-workaround`; until then publish
  `quasar-core`/`quasar-osgi-annotations` `0.9.3_r3-asm9.11` to Artifactory.
* Make `-Xlambdas=class -Xsam-conversions=class` the default for CorDapps (cordapp Gradle plugin) and document it.

* Release `corda-gradle-plugins` 5.1.4 (Gradle 9 fix) and update `gradlePluginsVersion` from `5.1.4-SNAPSHOT`.
* CI: Gradle 9 + JDK 25 agents with a JDK 17 toolchain; `JAVA_17_HOME` for the smoke tests running a 4.14 node.
* Docker images with JDK 25 base images (not built).
* The full test suites weren't run, only the targeted tests in section 7.
* `-Xjvm-default` is deprecated in Kotlin 2.2+ (`-jvm-default`); left as is (warning only).
* Re-run `ExternalVerificationKotlin19Test` (and the other smoke tests) on a machine with enough free disk space.
* ENT: migrate `tools:flow-necromancer` to the IntelliJ Platform Gradle Plugin 2.x; upgrade the Develocity server (or pin the plugin).
* Consider applying ENT's Kotlin 2.4 warning fixes to the shared OS files too, to reduce merge conflicts.
* Capsules without a caplet (ENT `pinger`, `firewall-loadtest`, `jmeter`) still hit the Capsule Java version parsing bug on JDKs with
  5-part versions (e.g. Azul `25.0.5.0.3`); they'd need a small caplet like the others.
* The CI Jenkinsfiles still set `JAVA_HOME` to JDK 17 images; they need JDK 25 agents (with JDK 17 available as a toolchain).

## 7. Test results (local, JDK 25.0.5)

OS:

* Compile of every module (main, test, integration/smoke test source sets): **pass**.
* The new and updated tests from section 4 (`TransactionBuilderMockNetworkTest`: real Kotlin 1.2 and 1.9 verifier processes on JDK 25,
  confirmed from their logs): **pass**.
* Full regression run of `:core:test :core-tests:test :serialization-tests:test :client:jackson:test :node-api:test :node:test`
  (about 2,200 tests, after the Quasar fixes): 32 failures, all explained:
  * `ScheduledFlowTests` (2), `ScheduledFlowsDrainingModeTest` (1): the Quasar `DUP` problem (5.5, item 3). **Fixed**, now pass.
  * `DefaultCryptoServiceTests` (1): Java 25 generates secp384r1 EC keys by default, which Corda doesn't support; the test relied on the
    old secp256r1 default. **Fixed** in the test (in OS and ENT). No main code relies on the default EC key size.
  * `ReceiveMultipleFlowTests` (4), `ReferencedStatesFlowTests` (5), `ReceiveFinalityFlowTest` (1): "Using more than one mock network
    simultaneously", a knock-on of earlier tests in the same JVM timing out without stopping their mock network. Pass when re-run.
  * `FlowSleepTest` (2), `RaftNotaryServiceTests` (1): timeouts (node shutdown hanging in Artemis) under load. Pass when re-run alone.
  * `TlsDiffAlgorithmsTest` (8), `TLSAuthenticationTests` (7): **environmental**. These tests connect to the machine's LAN address
    (`InetAddress.getLocalHost()`), which this sandbox blocks: a plain TCP socket to that address fails with "Bad file descriptor" on
    JDK 17 as well, while the same socket test to 127.0.0.1 works. Need re-running on CI.
* `ExternalVerificationKotlin19Test` (smoke): the 4.14.4 node (JDK 17) and the 4.16 nodes (JDK 25) start, but the test could **not**
  complete here: this machine's data volume is over 90% full, which is Artemis' default `max-disk-usage`, so the brokers block all RPC
  ("AMQ222212: Disk Full!"). It needs re-running on a machine with free disk space (with `JAVA_17_HOME` and `JAVA_8_HOME` set).

ENT:

* Compile of every module with warnings as errors: **pass**.
* The same core-tests and node tests as above (against ENT's node and ENT's `verifier-1.2`/`verifier-1.9`, both confirmed started), the
  new ENT legacy-node tests, and the transaction validator tests: **pass**.
* After the Quasar fixes: `ScheduledFlowTests`, `ScheduledFlowsDrainingModeTest`, `FinalityFlowTests`, `CollectSignaturesFlowTests`,
  `ReferencedStatesFlowTests`, `TransactionBuilderMockNetworkTest` (39 tests): **pass**.

Not run: the full ENT test suites, integration tests, smoke tests other than above, Docker image builds, documentation builds.
