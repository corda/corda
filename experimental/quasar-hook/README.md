What is this?
=============

This is a javaagent that may be used while running applications using quasar. It hooks into quasar to track what
methods are scanned, instrumented and used at runtime, and generates an exclude pattern that may be passed in to quasar
to stop it from scanning classes unnecessarily.

Arguments
===

`expand`, `alwaysExcluded` and `truncate` tweak the output exclude pattern. `expand` is a list of packages to always expand (for example
instead of generating `com.*` generate `com.google.*,com.typesafe.*` etc.), `alwaysExcluded` is a list of packages under
which all classes are considered excluded irregardless of instrumentation, `truncate` is a list of packages that should
not be included in the exclude pattern. Truncating `net.corda` means nothing should be excluded from instrumentation in
Corda.

How to generate an exclude pattern for Corda
====

In order to generate a good exclude pattern we need to exercise the Corda code so that most classes get loaded and
inspected by quasar and quasar-hook. For this we can run tests using the 'Quasar exclude pattern extraction (...)'
intellij run configuration, which includes the hook. In addition we should run the tool on a bare corda.jar, as some
additional classes are used when the jar is invoked directly. To do this we'll use a node in samples:

```
./gradlew experimental:quasar-hook:jar
./gradlew samples:irs-demo:deployNodes
cd samples/irs-demo/build/nodes/NotaryService
java -javaagent:../../../../../experimental/quasar-hook/build/libs/quasar-hook.jar=expand=com,de,org,co,io;truncate=net.corda;alwaysExcluded=com.opengamma,io.atomix,org.jolokia -jar corda.jar
```

Once the node is started just exit the node.

We can take the union of the two generated exclude patterns to get a final one.