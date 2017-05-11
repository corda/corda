What is this?
=============

This is a javaagent that may be used while running applications using quasar. It hooks into quasar to track what
methods are scanned, instrumented and used at runtime, and generates an exclude pattern that may be passed in to quasar
to stop it from scanning classes unnecessarily.

Example usage
=============

```
./gradlew experimental:quasar-hook:jar
java -javaagent:experimental/quasar-hook/build/libs/quasar-hook.jar="expand=com,de,org,co;truncate=net.corda" -jar path/to/corda.jar
```

The above will run corda.jar and on exit will print information about what classes were scanned/instrumented.

`expand` and `truncate` tweak the output exclude pattern. `expand` is a list of packages to always expand (for example
instead of generating `com.*` generate `com.google.*,com.typesafe.*` etc.), `truncate` is a list of packages that should
not be included in the exclude pattern. Truncating `net.corda` means nothing should be excluded from instrumentation in
Corda.