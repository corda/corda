# Quasar on Java 25 (temporary workaround)

Corda 4.16 runs on Java 25 and compiles to Java 25 bytecode, but Quasar `0.9.3_r3` does not support it:

1. Its shaded copy of ASM only supports class files up to Java 21, so it cannot instrument Java 25 bytecode
   (fibers fail with "Fiber class ... has not been instrumented" / `VerifyInstrumentationException`).
2. `ThreadAccess` looks up `Thread.target`, which Java 19+ (Loom) moved into the final field `Thread.holder.task`, so its static
   initialiser fails (`ExceptionInInitializerError` at `ThreadAccess.java:89`).
3. Once ASM is upgraded, ASM 9.6+ calls `Interpreter.copyOperation` for *both* values produced by `DUP`, so Quasar's `TypeInterpreter`
   loses the original `NEW` value and can no longer instrument `new Foo(suspendableCall())` ("Expected to find a NewValue on stack
   index ..." followed by `ArrayIndexOutOfBoundsException: Index -1` in `InstrumentMethod$FrameInfo`), which breaks e.g.
   `subFlow(FinalityFlow(tx, initiateFlow(party)))`.

This standalone build produces `co.paralleluniverse:quasar-core:0.9.3_r3-asm9.11`, which is Quasar `0.9.3_r3` with:

1. ASM 9.11 (supports Java 25 and later) relocated to `co.paralleluniverse.asm`, replacing the old copy.
2. A patched `ThreadAccess` (`src/main/java`) which, when `Thread.target` doesn't exist, keeps the target Quasar sets in a weak map.
3. A patched `TypeAnalyzer` (`src/main/java`) whose frames analyse `DUP` the way ASM 9.5 did (keep the original value, copy only the top one).

It also republishes the unchanged `quasar-osgi-annotations` under the same version, as Corda uses `$quasar_version` for both.

```
./gradlew -p tools/quasar-jdk25-workaround publishToMavenLocal
```

`constants.properties` (Corda Open Source and Enterprise) uses `quasarVersion=0.9.3_r3-asm9.11`. This artifact must also be published to
R3's Artifactory for CI.

**This is a stop-gap.** The proper fix is a Quasar release (e.g. `0.9.4_r3`) with ASM >= 9.8 and the `ThreadAccess` and `TypeAnalyzer` changes, after which
this directory should be deleted and `quasarVersion` updated.
